package rs.unit.backend.deposit.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.authentication.application.SessionTimeoutPolicy;
import rs.unit.backend.deposit.domain.BanknotePolicy;
import rs.unit.backend.deposit.infrastructure.persistence.DepositRepository;
import rs.unit.backend.deposit.infrastructure.persistence.DepositRepository.StoredDeposit;
import rs.unit.backend.deposit.infrastructure.persistence.DepositRepository.StoredOperation;
import rs.unit.backend.hardware.port.HardwareStatusPort;
import rs.unit.backend.machine.domain.MachineReadinessPolicy;
import rs.unit.backend.report.application.ReportingService;
import rs.unit.backend.user.domain.UserRole;

@Service
public class DepositService {

    private final DepositRepository repository;
    private final HardwareStatusPort hardware;
    private final Clock clock;
    private final UUID runtimeId = UUID.randomUUID();
    private final ReportingService reporting;

    public DepositService(
            DepositRepository repository,
            HardwareStatusPort hardware,
            Clock clock,
            ReportingService reporting) {
        this.repository = repository;
        this.hardware = hardware;
        this.clock = clock;
        this.reporting = reporting;
    }

    @Transactional
    public void start(AuthenticatedUser user) {
        requireCashier(user);

        repository.lockMachine();

        Instant now = clock.instant();
        requireValidSession(user, now);

        repository.closeUnavailableDeposits(runtimeId, now);

        repository.closeDepositsOfUnavailableAccounts(now);

        reporting.captureFinishedDeposits();

        if (repository.hasUnfinishedCollection()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Complete the cash collection before starting a deposit.");
        }

        if (repository.hasActiveDeposit()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Another deposit is already active on this machine.");
        }

        repository.create(
                UUID.randomUUID(), user.id(), user.loginId(), runtimeId, now, user.expiresAt());
    }

    @Transactional(readOnly = true)
    public DepositView current(AuthenticatedUser user) {
        requireCashier(user);

        Instant now = clock.instant();
        requireValidSession(user, now);
        requireActiveSession(user, now);

        return view(findOwnedDeposit(user), now);
    }

    @Transactional
    public BanknoteResult insert(
            AuthenticatedUser user, UUID operationId, String currency, int denomination) {
        requireCashier(user);
        validateBanknote(operationId, currency, denomination);

        repository.lockMachine();

        Instant now = clock.instant();
        requireValidSession(user, now);
        requireActiveSession(user, now);

        StoredDeposit deposit = findOwnedDeposit(user);
        StoredOperation previous = repository.findOperation(operationId).orElse(null);

        if (previous != null) {
            if (!previous.depositId().equals(deposit.id())
                    || !previous.currency().equals(currency)
                    || previous.denomination() != denomination) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Operation identifier has already been used for another request.");
            }

            return new BanknoteResult(operationId, previous.outcome(), true, view(deposit, now));
        }

        requireActiveDeposit(deposit);

        if (repository.hasUnfinishedCollection()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Banknote acceptance is blocked during cash collection.");
        }

        var snapshot = hardware.snapshot();

        if (!MachineReadinessPolicy.isDepositHardwareReady(snapshot)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The machine is not ready to accept banknotes.");
        }

        var outcome = BanknotePolicy.evaluate(currency, denomination);

        repository.recordOperation(operationId, deposit.id(), currency, denomination, outcome, now);

        repository.recordActivity(
                user.loginId(),
                SessionTimeoutPolicy.inactivityDeadline(user.role(), now, user.expiresAt()));

        return new BanknoteResult(operationId, outcome, false, view(deposit, now));
    }

    @Transactional
    public void finish(AuthenticatedUser user) {
        if (user.role() != UserRole.CASHIER) {
            return;
        }

        repository.lockMachine();

        StoredDeposit deposit = findOwnedDeposit(user);

        if (!"ACTIVE".equals(deposit.status())) {
            reporting.captureDeposit(deposit.id());
            return;
        }

        repository.complete(deposit.id(), clock.instant());
        reporting.captureDeposit(deposit.id());
    }

    private StoredDeposit findOwnedDeposit(AuthenticatedUser user) {
        StoredDeposit deposit =
                repository
                        .findByLoginId(user.loginId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "No deposit is associated with this login."));

        if (!deposit.userId().equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Deposit access denied.");
        }

        if (!deposit.runtimeId().equals(runtimeId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This deposit belongs to a previous application run.");
        }

        return deposit;
    }

    private DepositView view(StoredDeposit deposit, Instant now) {
        var totals = repository.totals(deposit.id());

        boolean acceptingEnabled =
                "ACTIVE".equals(deposit.status())
                        && now.isBefore(deposit.expiresAt())
                        && !repository.hasUnfinishedCollection()
                        && MachineReadinessPolicy.isDepositHardwareReady(hardware.snapshot());

        return new DepositView(
                deposit.id(),
                deposit.status(),
                "EUR",
                totals.totalCents(),
                totals.banknoteCount(),
                deposit.startedAt(),
                deposit.expiresAt(),
                deposit.finishedAt(),
                acceptingEnabled);
    }

    @Transactional
    public void reconcile() {
        repository.lockMachine();

        Instant now = clock.instant();

        repository.closeUnavailableDeposits(runtimeId, now);
        repository.closeDepositsOfUnavailableAccounts(now);
        reporting.captureFinishedDeposits();
    }

    @Transactional
    public boolean isSessionActive(AuthenticatedUser user) {
        requireCashier(user);
        repository.lockMachine();

        Instant now = clock.instant();

        repository.closeUnavailableDeposits(runtimeId, now);
        repository.closeDepositsOfUnavailableAccounts(now);
        reporting.captureFinishedDeposits();

        return now.isBefore(user.expiresAt())
                && repository.hasValidSession(user.loginId(), runtimeId, now);
    }

    @Transactional
    public void recordActivity(AuthenticatedUser user) {
        requireCashier(user);
        repository.lockMachine();

        Instant now = clock.instant();

        requireValidSession(user, now);
        requireActiveSession(user, now);

        repository.recordActivity(
                user.loginId(),
                SessionTimeoutPolicy.inactivityDeadline(user.role(), now, user.expiresAt()));
    }

    private void requireActiveSession(AuthenticatedUser user, Instant now) {
        if (!repository.hasValidSession(user.loginId(), runtimeId, now)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "The deposit session has expired.");
        }
    }

    @Transactional
    public boolean prepareForCollection() {
        repository.lockMachine();

        Instant now = clock.instant();

        repository.closeUnavailableDeposits(runtimeId, now);
        repository.closeDepositsOfUnavailableAccounts(now);
        reporting.captureFinishedDeposits();

        return !repository.hasActiveDeposit();
    }

    private static void requireCashier(AuthenticatedUser user) {
        if (user.role() != UserRole.CASHIER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only cashiers can make deposits.");
        }
    }

    private static void requireValidSession(AuthenticatedUser user, Instant now) {
        if (!now.isBefore(user.expiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The session has expired.");
        }
    }

    private static void requireActiveDeposit(StoredDeposit deposit) {
        if (!"ACTIVE".equals(deposit.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The deposit has already ended.");
        }
    }

    private static void validateBanknote(UUID operationId, String currency, int denomination) {
        if (operationId == null
                || currency == null
                || !currency.matches("[A-Z]{3}")
                || denomination <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid operation identifier, currency and denomination are required.");
        }
    }
}
