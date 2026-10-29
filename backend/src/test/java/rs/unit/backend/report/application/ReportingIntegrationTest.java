package rs.unit.backend.report.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.authentication.application.SessionActivityService;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.simulator.SimulatedHardware;
import rs.unit.backend.support.MachineTestState;
import rs.unit.backend.user.domain.UserRole;

@SpringBootTest(
        properties = {
            "unit.collection.monitor.enabled=false",
            "unit.deposit.monitor.enabled=false"
        })
@Transactional
class ReportingIntegrationTest {

    @Autowired private ReportingService reporting;
    @Autowired private DepositService deposits;
    @Autowired private SimulatedHardware hardware;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext applicationContext;
    @Autowired private SessionActivityService activityService;

    private MockMvc mockMvc;

    @MockitoBean private Clock clock;

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    private AuthenticatedUser cashier;
    private AuthenticatedUser manager;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
        MachineTestState.reset(jdbc);

        jdbc.update(
                """
                UPDATE cash_machine.machine_configuration
                SET time_zone = 'Europe/Belgrade'
                WHERE id = 1
                """);

        cashier = createUser(UserRole.CASHIER, "Test Cashier");
        manager = createUser(UserRole.MANAGEMENT, "Test Manager");

        mockMvc =
                MockMvcBuilders.webAppContextSetup(applicationContext)
                        .apply(springSecurity())
                        .build();
    }

    @Test
    void reportsIncludeAcceptedMoneyAndIgnoreRejectedBanknotes() {
        deposits.start(cashier);
        deposits.insert(cashier, UUID.randomUUID(), "EUR", 20);
        deposits.insert(cashier, UUID.randomUUID(), "EUR", 50);
        deposits.insert(cashier, UUID.randomUUID(), "USD", 20);
        deposits.insert(cashier, UUID.randomUUID(), "EUR", 3);

        var overview = reporting.overview(manager);

        assertEquals(7000, overview.deviceTotal().totalCents());
        assertEquals(7000, overview.dayTotal().totalCents());
        assertEquals(2, overview.dayTotal().banknoteCount());
        assertEquals(2, overview.dayTotal().denominations().size());
        assertEquals(1, overview.dayTotal().users().size());
        assertEquals(cashier.id(), overview.dayTotal().users().getFirst().userId());
    }

    @Test
    void completedDepositProducesExactlyOneAutomaticReceipt() {
        deposits.start(cashier);
        deposits.insert(cashier, UUID.randomUUID(), "EUR", 20);

        deposits.finish(cashier);
        deposits.finish(cashier);
        deposits.reconcile();

        var receipts = reporting.listReceipts(manager, 0);

        assertEquals(1, receipts.size());
        assertEquals("DEPOSIT", receipts.getFirst().kind());
        assertEquals(2000, receipts.getFirst().totalCents());
        assertEquals("PRINTED", receipts.getFirst().printStatus());
        assertTrue(receipts.getFirst().content().contains("Test Cashier"));

        assertEquals(
                1L,
                jdbc.queryForObject("SELECT COUNT(*) FROM cash_machine.print_attempt", Long.class));
    }

    @Test
    void emptyDepositDoesNotProduceAutomaticReceipt() {
        deposits.start(cashier);
        deposits.finish(cashier);

        assertTrue(reporting.listReceipts(manager, 0).isEmpty());
    }

    @Test
    void inactivityExpirationPreservesMoneyAndCreatesReceipt() {
        deposits.start(cashier);
        deposits.insert(cashier, UUID.randomUUID(), "EUR", 20);

        when(clock.instant()).thenReturn(now.plusSeconds(60));

        deposits.reconcile();
        deposits.reconcile();

        assertEquals(
                "EXPIRED",
                jdbc.queryForObject(
                        """
                        SELECT status
                        FROM cash_machine.deposit
                        WHERE login_id = ?
                        """,
                        String.class,
                        cashier.loginId()));

        var receipts = reporting.listReceipts(manager, 0);

        assertEquals(1, receipts.size());
        assertEquals(2000, receipts.getFirst().totalCents());
        assertEquals(2000, reporting.report(manager, ReportType.DAY_TOTAL).totalCents());
    }

    @Test
    void failedAutomaticPrintDoesNotPreventDepositCompletion() {
        deposits.start(cashier);
        deposits.insert(cashier, UUID.randomUUID(), "EUR", 20);
        setPrinter(PrinterState.OUT_OF_PAPER);

        deposits.finish(cashier);

        var receipt = reporting.listReceipts(manager, 0).getFirst();

        assertEquals("FAILED", receipt.printStatus());
        assertEquals("PRINTER_OUT_OF_PAPER", receipt.printError());

        String originalContent = receipt.content();

        setPrinter(PrinterState.READY);
        when(clock.instant()).thenReturn(now.plusSeconds(1));

        UUID requestId = UUID.randomUUID();

        var first = reporting.reprint(manager, receipt.id(), requestId);
        var replay = reporting.reprint(manager, receipt.id(), requestId);

        assertEquals("PRINTED", first.status());
        assertTrue(replay.replayed());
        assertEquals(originalContent, reporting.getReceipt(manager, receipt.id()).content());

        assertEquals(
                2L,
                jdbc.queryForObject("SELECT COUNT(*) FROM cash_machine.print_attempt", Long.class));
    }

    @Test
    void reportPrintRequestsAreIdempotentAndMayContainZero() {
        UUID requestId = UUID.randomUUID();

        var first = reporting.printReport(manager, ReportType.DAY_TOTAL, requestId);
        var replay = reporting.printReport(manager, ReportType.DAY_TOTAL, requestId);

        assertEquals(first.id(), replay.id());
        assertEquals(0, first.totalCents());
        assertEquals("PRINTED", first.printStatus());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> reporting.printReport(manager, ReportType.DEVICE_TOTAL, requestId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void receiptAccessIsRestrictedToItsDepositAndExpires() {
        deposits.start(cashier);

        var access = reporting.issueReceiptAccess(cashier);

        assertThrows(
                ResponseStatusException.class, () -> reporting.receiptByAccess(access.token()));

        deposits.insert(cashier, UUID.randomUUID(), "EUR", 20);
        deposits.finish(cashier);

        var receipt = reporting.receiptByAccess(access.token());

        assertEquals(2000, receipt.totalCents());
        assertEquals("DEPOSIT", receipt.kind());

        assertThrows(
                ResponseStatusException.class, () -> reporting.receiptByAccess("0".repeat(64)));

        when(clock.instant()).thenReturn(access.expiresAt());

        assertThrows(
                ResponseStatusException.class, () -> reporting.receiptByAccess(access.token()));
    }

    @Test
    void dailyReportUsesBanknoteTimeAndExcludesNextMidnight() {
        deposits.start(cashier);

        UUID operationId = UUID.randomUUID();
        deposits.insert(cashier, operationId, "EUR", 20);

        Instant start = Instant.parse("2025-12-31T23:00:00Z");
        Instant end = Instant.parse("2026-01-01T23:00:00Z");

        jdbc.update(
                """
                UPDATE cash_machine.deposit_banknote_operation
                SET recorded_at = ?
                WHERE id = ?
                """,
                Timestamp.from(start),
                operationId);

        assertEquals(2000, reporting.report(manager, ReportType.DAY_TOTAL).totalCents());

        jdbc.update(
                """
                UPDATE cash_machine.deposit_banknote_operation
                SET recorded_at = ?
                WHERE id = ?
                """,
                Timestamp.from(end),
                operationId);

        assertEquals(0, reporting.report(manager, ReportType.DAY_TOTAL).totalCents());
        assertEquals(2000, reporting.report(manager, ReportType.DEVICE_TOTAL).totalCents());
    }

    @Test
    void savedReportDoesNotChangeAfterNewDeposits() {
        var first = reporting.printReport(manager, ReportType.DAY_TOTAL, UUID.randomUUID());

        deposits.start(cashier);
        deposits.insert(cashier, UUID.randomUUID(), "EUR", 20);

        var second = reporting.printReport(manager, ReportType.DAY_TOTAL, UUID.randomUUID());

        assertNotEquals(first.id(), second.id());
        assertEquals(0, reporting.getReceipt(manager, first.id()).totalCents());
        assertEquals(2000, second.totalCents());
    }

    @Test
    void cashierCannotReadManagementReports() {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> reporting.overview(cashier));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private AuthenticatedUser createUser(UserRole role, String name) {
        UUID id = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO cash_machine.user_account (
                    id, display_name, role, pin_hash, active
                )
                VALUES (?, ?, ?, 'test-only-unused-hash', TRUE)
                """,
                id,
                name,
                role.name());

        return new AuthenticatedUser(id, name, role, now.plusSeconds(900));
    }

    private void setPrinter(PrinterState printer) {
        var current = hardware.snapshot();

        hardware.replaceState(
                new HardwareSnapshot(
                        current.acceptor(),
                        current.cashbox(),
                        current.door(),
                        current.lock(),
                        current.extensionBoard(),
                        printer));
    }

    @Test
    void printRequestCannotBeReusedByAnotherManager() {
        UUID requestId = UUID.randomUUID();

        reporting.printReport(manager, ReportType.DAY_TOTAL, requestId);

        AuthenticatedUser anotherManager = createUser(UserRole.MANAGEMENT, "Another Manager");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                reporting.printReport(
                                        anotherManager, ReportType.DAY_TOTAL, requestId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void printAttemptCannotBeReusedForAnotherReceipt() {
        var first = reporting.printReport(manager, ReportType.DAY_TOTAL, UUID.randomUUID());

        var second = reporting.printReport(manager, ReportType.DEVICE_TOTAL, UUID.randomUUID());

        UUID attemptId = UUID.randomUUID();

        reporting.reprint(manager, first.id(), attemptId);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> reporting.reprint(manager, second.id(), attemptId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void expiredManagerCannotReadReports() {
        var expired =
                new AuthenticatedUser(
                        manager.id(), manager.displayName(), UserRole.MANAGEMENT, now);

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> reporting.overview(expired));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void managerCannotRequestCashierReceiptAccess() {
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class, () -> reporting.issueReceiptAccess(manager));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void cashierCannotRequestReceiptAccessWithoutActiveDeposit() {
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class, () -> reporting.issueReceiptAccess(cashier));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void receiptAccessCannotBeIssuedAfterDepositCompletion() {
        deposits.start(cashier);
        deposits.finish(cashier);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class, () -> reporting.issueReceiptAccess(cashier));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void missingAndMalformedReceiptTokensAreRejected() {
        for (String token : new String[] {null, "", "invalid", "A".repeat(64)}) {
            ResponseStatusException exception =
                    assertThrows(
                            ResponseStatusException.class, () -> reporting.receiptByAccess(token));

            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        }
    }

    @ParameterizedTest
    @CsvSource({"-1", "10001"})
    void invalidReceiptPagesAreRejected(int page) {
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class, () -> reporting.listReceipts(manager, page));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void missingReceiptCannotBeReadOrReprinted() {
        UUID missing = UUID.randomUUID();

        ResponseStatusException read =
                assertThrows(
                        ResponseStatusException.class,
                        () -> reporting.getReceipt(manager, missing));

        ResponseStatusException print =
                assertThrows(
                        ResponseStatusException.class,
                        () -> reporting.reprint(manager, missing, UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, read.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, print.getStatusCode());
    }

    @ParameterizedTest
    @CsvSource({"2026-03-29T12:00:00Z, 23", "2026-10-25T12:00:00Z, 25"})
    void dailyPeriodRespectsDaylightSavingTransitions(String instant, long expectedHours) {
        Instant reportTime = Instant.parse(instant);
        when(clock.instant()).thenReturn(reportTime);

        var currentManager =
                new AuthenticatedUser(
                        manager.id(),
                        manager.displayName(),
                        UserRole.MANAGEMENT,
                        reportTime.plusSeconds(900));

        ReportView report = reporting.report(currentManager, ReportType.DAY_TOTAL);

        assertEquals(
                expectedHours,
                Duration.between(report.periodStart(), report.periodEnd()).toHours());
    }

    @ParameterizedTest
    @EnumSource(
            value = PrinterState.class,
            names = {"OFFLINE", "UNKNOWN"})
    void unavailablePrinterPreservesReportWithFailureReason(PrinterState printer) {
        setPrinter(printer);

        var receipt = reporting.printReport(manager, ReportType.DAY_TOTAL, UUID.randomUUID());

        assertEquals("FAILED", receipt.printStatus());
        assertEquals("PRINTER_" + printer.name(), receipt.printError());
        assertEquals(receipt.content(), reporting.getReceipt(manager, receipt.id()).content());
    }

    @Test
    void managementCanReadOverviewAndReceiptHistoryThroughApi() throws Exception {
        mockMvc.perform(asManager(get("/api/reports")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceTotal.totalCents").value(0))
                .andExpect(jsonPath("$.dayTotal.totalCents").value(0));

        mockMvc.perform(asManager(get("/api/receipts").param("page", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void managementCanReadAndPrintEachReportType(ReportType type) throws Exception {
        mockMvc.perform(asManager(get("/api/reports/" + type.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(type.name()))
                .andExpect(jsonPath("$.totalCents").value(0));

        mockMvc.perform(
                        asManager(post("/api/reports/" + type.name() + "/print"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(printBody(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value(type.name()))
                .andExpect(jsonPath("$.printStatus").value("PRINTED"));
    }

    @Test
    void managementCanReadDownloadAndReprintReceiptThroughApi() throws Exception {
        var receipt = reporting.printReport(manager, ReportType.DAY_TOTAL, UUID.randomUUID());

        mockMvc.perform(asManager(get("/api/receipts/" + receipt.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(receipt.id().toString()))
                .andExpect(jsonPath("$.totalCents").value(0));

        mockMvc.perform(asManager(get("/api/receipts/" + receipt.id() + "/pdf")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        org.hamcrest.Matchers.containsString(
                                                "receipt-" + receipt.id() + ".pdf")));

        mockMvc.perform(
                        asManager(post("/api/receipts/" + receipt.id() + "/print"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(printBody(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value(receipt.id().toString()))
                .andExpect(jsonPath("$.status").value("PRINTED"))
                .andExpect(jsonPath("$.replayed").value(false));

        mockMvc.perform(asManager(get("/api/receipts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(receipt.id().toString()));
    }

    @Test
    void cashierCanRequestAccessAndDownloadReceiptAfterLogout() throws Exception {
        deposits.start(cashier);

        mockMvc.perform(asUser(post("/api/deposits/current/receipt-access"), cashier).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiresAt").exists());

        var access = reporting.issueReceiptAccess(cashier);

        deposits.insert(cashier, UUID.randomUUID(), "EUR", 20);
        deposits.finish(cashier);

        mockMvc.perform(get("/api/receipt-access").header("X-Receipt-Token", access.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("DEPOSIT"))
                .andExpect(jsonPath("$.totalCents").value(2000));

        mockMvc.perform(get("/api/receipt-access/pdf").header("X-Receipt-Token", access.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/receipt-access")).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/receipt-access/pdf").header("X-Receipt-Token", "invalid"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportingApiRequiresAuthenticationManagementAndCsrf() throws Exception {
        mockMvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());

        deposits.start(cashier);

        mockMvc.perform(asUser(get("/api/reports"), cashier)).andExpect(status().isForbidden());

        mockMvc.perform(
                        asManager(post("/api/reports/DAY_TOTAL/print"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(printBody(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPrintRequestAndUnknownReportTypeAreRejected() throws Exception {
        mockMvc.perform(
                        asManager(post("/api/reports/DAY_TOTAL/print"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asManager(get("/api/reports/UNKNOWN_REPORT")))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder asManager(MockHttpServletRequestBuilder request) {
        return asUser(request, manager);
    }

    private MockHttpServletRequestBuilder asUser(
            MockHttpServletRequestBuilder request, AuthenticatedUser user) {
        MockHttpSession session = new MockHttpSession();
        activityService.initialize(user, session);

        var token =
                UsernamePasswordAuthenticationToken.authenticated(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));

        return request.session(session).with(authentication(token));
    }

    private String printBody(UUID requestId) {
        return "{\"requestId\":\"" + requestId + "\"}";
    }
}
