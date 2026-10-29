package rs.unit.backend.report.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.port.HardwareStatusPort;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfiguration;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfigurationRepository;
import rs.unit.backend.printing.application.ReceiptFormatter;
import rs.unit.backend.printing.application.ReceiptView;
import rs.unit.backend.printing.infrastructure.persistence.ReceiptRepository;
import rs.unit.backend.report.infrastructure.persistence.ReportRepository;
import rs.unit.backend.user.domain.UserRole;

@Service
public class ReportingService {

    private final ReportRepository reports;
    private final ReceiptRepository receipts;
    private final MachineConfigurationRepository configurations;
    private final HardwareStatusPort hardware;
    private final ReceiptFormatter formatter;
    private final Clock clock;

    public ReportingService(
            ReportRepository reports,
            ReceiptRepository receipts,
            MachineConfigurationRepository configurations,
            HardwareStatusPort hardware,
            ReceiptFormatter formatter,
            Clock clock) {
        this.reports = reports;
        this.receipts = receipts;
        this.configurations = configurations;
        this.hardware = hardware;
        this.formatter = formatter;
        this.clock = clock;
    }

    @Transactional
    public Overview overview(AuthenticatedUser user) {
        requireManager(user);
        reports.lockMachine();

        MachineConfiguration machine = configuration();
        Instant now = clock.instant();

        return new Overview(
                build(ReportType.DEVICE_TOTAL, machine, now),
                build(ReportType.DAY_TOTAL, machine, now));
    }

    @Transactional
    public ReportView report(AuthenticatedUser user, ReportType type) {
        requireManager(user);
        reports.lockMachine();

        return build(type, configuration(), clock.instant());
    }

    @Transactional
    public ReceiptView printReport(AuthenticatedUser user, ReportType type, UUID requestId) {
        requireManager(user);
        reports.lockMachine();

        ReceiptView existing = receipts.findByRequestId(requestId).orElse(null);

        if (existing != null) {
            if (!existing.kind().equals(type.name()) || !existing.requestedBy().equals(user.id())) {
                throw conflict("This request identifier belongs to another print request.");
            }

            return existing;
        }

        MachineConfiguration machine = configuration();
        Instant now = clock.instant();
        ReportView report = build(type, machine, now);
        UUID receiptId = UUID.randomUUID();

        receipts.create(
                receiptId,
                type.name(),
                null,
                user.id(),
                requestId,
                now,
                report.totalCents(),
                formatter.report(machine, report, user.displayName()));

        attempt(receiptId, requestId);

        return receipt(receiptId);
    }

    @Transactional
    public PrintResult reprint(AuthenticatedUser user, UUID receiptId, UUID requestId) {
        requireManager(user);
        reports.lockMachine();
        receipt(receiptId);

        return attempt(receiptId, requestId);
    }

    @Transactional(readOnly = true)
    public ReceiptView getReceipt(AuthenticatedUser user, UUID receiptId) {
        requireManager(user);
        return receipt(receiptId);
    }

    @Transactional(readOnly = true)
    public List<ReceiptView> listReceipts(AuthenticatedUser user, int page) {
        requireManager(user);

        if (page < 0 || page > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid receipt page.");
        }

        return receipts.list(page);
    }

    @Transactional
    public void captureDeposit(UUID depositId) {
        reports.lockMachine();

        if (receipts.findByDepositId(depositId).isPresent()) {
            return;
        }

        var deposit = receipts.depositReceipt(depositId).orElse(null);

        if (deposit == null) {
            return;
        }

        UUID receiptId = UUID.randomUUID();

        receipts.create(
                receiptId,
                "DEPOSIT",
                depositId,
                deposit.userId(),
                null,
                clock.instant(),
                deposit.totalCents(),
                formatter.deposit(configuration(), deposit));

        attempt(receiptId, receiptId);
    }

    @Transactional
    public void captureFinishedDeposits() {
        reports.lockMachine();

        for (UUID depositId : receipts.pendingDeposits()) {
            captureDeposit(depositId);
        }
    }

    @Transactional
    public ReceiptAccess issueReceiptAccess(AuthenticatedUser user) {
        if (user.role() != UserRole.CASHIER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only cashiers can request deposit receipt access.");
        }

        requireUnexpired(user);
        reports.lockMachine();

        String token =
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", "");

        Instant expiresAt = clock.instant().plusSeconds(1800);

        if (!receipts.issueAccess(user.loginId(), hash(token), expiresAt)) {
            throw conflict("No active deposit is associated with this login.");
        }

        return new ReceiptAccess(token, expiresAt);
    }

    @Transactional(readOnly = true)
    public ReceiptView receiptByAccess(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}")) {
            throw notFound();
        }

        return receipts.findByAccess(hash(token), clock.instant())
                .orElseThrow(ReportingService::notFound);
    }

    private ReportView build(ReportType type, MachineConfiguration machine, Instant now) {
        ZoneId zone = ZoneId.of(machine.getTimeZone());
        var day = now.atZone(zone).toLocalDate();

        Instant start = day.atStartOfDay(zone).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();

        List<ReportRepository.Row> rows;

        if (type == ReportType.DEVICE_TOTAL) {
            var cycle = reports.currentCycle();
            start = cycle.openedAt();
            end = now;
            rows = reports.cycleRows(cycle.id());
        } else {
            rows = reports.dayRows(start, end);
        }

        var denominations = new TreeMap<Integer, Long>();
        var users = new LinkedHashMap<UUID, ReportView.UserTotal>();

        long total = 0;
        long count = 0;

        for (var row : rows) {
            denominations.merge(row.denomination(), row.banknoteCount(), Math::addExact);

            var previous = users.get(row.userId());

            long userCount = previous == null ? 0 : previous.banknoteCount();
            long userTotal = previous == null ? 0 : previous.totalCents();

            users.put(
                    row.userId(),
                    new ReportView.UserTotal(
                            row.userId(),
                            row.displayName(),
                            Math.addExact(userCount, row.banknoteCount()),
                            Math.addExact(userTotal, row.totalCents())));

            total = Math.addExact(total, row.totalCents());
            count = Math.addExact(count, row.banknoteCount());
        }

        var denominationTotals = new ArrayList<ReportView.DenominationTotal>();

        denominations.forEach(
                (denomination, amount) ->
                        denominationTotals.add(
                                new ReportView.DenominationTotal(
                                        denomination,
                                        amount,
                                        Math.multiplyExact((long) denomination * 100, amount))));

        return new ReportView(
                type,
                machine.getCurrency(),
                machine.getTimeZone(),
                now,
                start,
                end,
                total,
                count,
                denominationTotals,
                new ArrayList<>(users.values()));
    }

    private PrintResult attempt(UUID receiptId, UUID requestId) {
        var previous = receipts.findAttempt(requestId).orElse(null);

        if (previous != null) {
            if (!previous.receiptId().equals(receiptId)) {
                throw conflict("This identifier belongs to another print attempt.");
            }

            return new PrintResult(
                    receiptId, requestId, previous.status(), previous.errorCode(), true);
        }

        PrinterState printer = hardware.snapshot().printer();

        String status = printer == PrinterState.READY ? "PRINTED" : "FAILED";
        String error = printer == PrinterState.READY ? null : "PRINTER_" + printer.name();

        receipts.recordAttempt(requestId, receiptId, clock.instant(), status, error);

        return new PrintResult(receiptId, requestId, status, error, false);
    }

    private ReceiptView receipt(UUID id) {
        return receipts.findById(id).orElseThrow(ReportingService::notFound);
    }

    private MachineConfiguration configuration() {
        return configurations
                .findById((short) 1)
                .orElseThrow(() -> new IllegalStateException("Machine configuration is missing."));
    }

    private void requireManager(AuthenticatedUser user) {
        if (user.role() != UserRole.MANAGEMENT) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only management can access reports.");
        }

        requireUnexpired(user);
    }

    private void requireUnexpired(AuthenticatedUser user) {
        if (!clock.instant().isBefore(user.expiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The session has expired.");
        }
    }

    private static String hash(String value) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt is unavailable.");
    }

    public record Overview(ReportView deviceTotal, ReportView dayTotal) {}

    public record ReceiptAccess(String token, Instant expiresAt) {}

    public record PrintResult(
            UUID receiptId, UUID requestId, String status, String errorCode, boolean replayed) {}
}
