package rs.unit.backend.authentication.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import rs.unit.backend.authentication.application.LoginAttemptLimiter;
import rs.unit.backend.authentication.application.PinService;
import rs.unit.backend.deposit.infrastructure.persistence.DepositRepository;
import rs.unit.backend.support.MachineTestState;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@SpringBootTest(
        properties = {
            "unit.collection.monitor.enabled=false",
            "unit.deposit.monitor.enabled=false",
            "unit.maintenance.monitor.enabled=false"
        })
@ActiveProfiles("simulation")
@Transactional
class AuthenticationIntegrationTest {

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private UserAccountRepository repository;
    @Autowired private DepositRepository depositRepository;
    @Autowired private PinService pinService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @MockitoBean private LoginAttemptLimiter attemptLimiter;

    @MockitoBean private Clock clock;

    private MockMvc mockMvc;
    private UUID cashierId;

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        MachineTestState.reset(jdbcTemplate);

        when(clock.instant()).thenReturn(now);

        mockMvc =
                MockMvcBuilders.webAppContextSetup(applicationContext)
                        .apply(springSecurity())
                        .build();

        jdbcTemplate.update("UPDATE cash_machine.user_account SET active = FALSE");

        cashierId = save(UserRole.CASHIER, "1234", true);
    }

    @Test
    void loginRotatesSessionAndPersistsAuthentication() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String previousId = session.getId();

        mockMvc.perform(
                        post("/api/auth/login")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(cashierId, "CASHIER", "1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cashierId.toString()))
                .andExpect(jsonPath("$.role").value("CASHIER"))
                .andExpect(jsonPath("$.pinHash").doesNotExist());

        assertNotEquals(previousId, session.getId());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cashierId.toString()));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(cashierId, "CASHIER", "1234")))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPinAndWrongRoleAreRejected() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(cashierId, "CASHIER", "9999")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(cashierId, "MANAGEMENT", "1234")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveAndMissingAccountsAreRejected() throws Exception {
        UUID inactive = save(UserRole.CASHIER, "1234", false);

        for (UUID id : new UUID[] {inactive, UUID.randomUUID()}) {
            mockMvc.perform(
                            post("/api/auth/login")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginBody(id, "CASHIER", "1234")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void citLogsInWithoutSelectingAccount() throws Exception {
        UUID citId = save(UserRole.CIT, "5678", true);

        mockMvc.perform(
                        post("/api/auth/cit-login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pin\":\"5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(citId.toString()))
                .andExpect(jsonPath("$.role").value("CIT"));
    }

    @Test
    void duplicateCitPinsAreRejected() throws Exception {
        save(UserRole.CIT, "5678", true);
        save(UserRole.CIT, "5678", true);

        mockMvc.perform(
                        post("/api/auth/cit-login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pin\":\"5678\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = loginCashier();

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());

        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void activityDoesNotExtendAbsoluteSessionLifetime() throws Exception {
        MockHttpSession session = loginCashier();

        for (int seconds = 50; seconds < 900; seconds += 50) {
            when(clock.instant()).thenReturn(now.plusSeconds(seconds));

            mockMvc.perform(post("/api/auth/activity").session(session).with(csrf()))
                    .andExpect(status().isNoContent());
        }

        when(clock.instant()).thenReturn(now.plusSeconds(899));

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        when(clock.instant()).thenReturn(now.plusSeconds(900));

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
    }

    @Test
    void pollingDoesNotExtendCashierInactivityDeadline() throws Exception {
        MockHttpSession session = loginCashier();

        when(clock.instant()).thenReturn(now.plusSeconds(59));

        mockMvc.perform(get("/api/machine/status").session(session)).andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        when(clock.instant()).thenReturn(now.plusSeconds(60));

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
    }

    @Test
    void explicitActivityExtendsCashierInactivityDeadline() throws Exception {
        MockHttpSession session = loginCashier();

        when(clock.instant()).thenReturn(now.plusSeconds(50));

        mockMvc.perform(post("/api/auth/activity").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        when(clock.instant()).thenReturn(now.plusSeconds(109));

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        when(clock.instant()).thenReturn(now.plusSeconds(110));

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
    }

    @Test
    void activityCannotBeRecordedForMissingDeposit() {
        InvalidDataAccessApiUsageException exception =
                assertThrows(
                        InvalidDataAccessApiUsageException.class,
                        () ->
                                depositRepository.recordActivity(
                                        UUID.randomUUID(), now.plusSeconds(60)));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals(
                "An active deposit is required to record activity.",
                exception.getCause().getMessage());
    }

    @Test
    void activityCannotBeRecordedAfterDepositCompletion() throws Exception {
        MockHttpSession session = loginCashier();

        UUID loginId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT login_id
                        FROM cash_machine.deposit
                        WHERE user_id = ?
                            AND status = 'ACTIVE'
                        """,
                        UUID.class,
                        cashierId);

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        InvalidDataAccessApiUsageException exception =
                assertThrows(
                        InvalidDataAccessApiUsageException.class,
                        () -> depositRepository.recordActivity(loginId, now.plusSeconds(60)));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals(
                "An active deposit is required to record activity.",
                exception.getCause().getMessage());

        assertEquals("COMPLETED", depositRepository.findByLoginId(loginId).orElseThrow().status());
    }

    @Test
    void deactivatingAccountInvalidatesSessionOnNextRequest() throws Exception {
        MockHttpSession session = loginCashier();

        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE cash_machine.user_account SET active = FALSE WHERE id = ?", cashierId);
        entityManager.clear();

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCannotChangeHardware() throws Exception {
        MockHttpSession session = loginCashier();

        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointReturnsTokenAndCreatesSession() throws Exception {
        var result =
                mockMvc.perform(get("/api/auth/csrf"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                        .andExpect(jsonPath("$.token").isNotEmpty())
                        .andReturn();

        assertTrue(result.getRequest().getSession(false) != null);
    }

    @Test
    void authenticatedUserCannotLogInAgain() throws Exception {
        MockHttpSession session = loginCashier();

        mockMvc.perform(
                        post("/api/auth/login")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(cashierId, "CASHIER", "1234")))
                .andExpect(status().isConflict());

        mockMvc.perform(
                        post("/api/auth/cit-login")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pin\":\"5678\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cashierId.toString()));
    }

    @Test
    void citAccountCannotUseProfileLogin() throws Exception {
        UUID citId = save(UserRole.CIT, "5678", true);

        mockMvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(citId, "CIT", "5678")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void citLoginIsRejectedWhenNoActiveCitAccountsExist() throws Exception {
        save(UserRole.CIT, "5678", false);

        mockMvc.perform(
                        post("/api/auth/cit-login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pin\":\"5678\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void citLoginIsRejectedWhenPinMatchesNoAccount() throws Exception {
        save(UserRole.CIT, "5678", true);

        mockMvc.perform(
                        post("/api/auth/cit-login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pin\":\"9999\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingAccountRoleInvalidatesSessionOnNextRequest() throws Exception {
        MockHttpSession session = loginCashier();

        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE cash_machine.user_account SET role = ? WHERE id = ?",
                "MANAGEMENT",
                cashierId);
        entityManager.clear();

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
    }

    @Test
    void deletingAccountInvalidatesSessionOnNextRequest() throws Exception {
        UUID managerId = save(UserRole.MANAGEMENT, "5678", true);

        var result =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginBody(managerId, "MANAGEMENT", "5678")))
                        .andExpect(status().isOk())
                        .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        entityManager.flush();

        jdbcTemplate.update("DELETE FROM cash_machine.user_account WHERE id = ?", managerId);

        entityManager.clear();

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
    }

    @Test
    void cashierLoginStartsAnEmptyDeposit() throws Exception {
        MockHttpSession session = loginCashier();

        mockMvc.perform(get("/api/deposits/current").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.totalCents").value(0))
                .andExpect(jsonPath("$.banknoteCount").value(0))
                .andExpect(jsonPath("$.acceptingEnabled").value(true));
    }

    @Test
    void acceptedBanknotesArePersistedImmediately() throws Exception {
        MockHttpSession session = loginCashier();

        mockMvc.perform(
                        post("/api/deposits/current/banknotes")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(banknoteBody(UUID.randomUUID(), "EUR", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.deposit.totalCents").value(2000));

        mockMvc.perform(
                        post("/api/deposits/current/banknotes")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(banknoteBody(UUID.randomUUID(), "EUR", 50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deposit.totalCents").value(7000))
                .andExpect(jsonPath("$.deposit.banknoteCount").value(2));

        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM cash_machine.deposit_banknote_operation operation
                        JOIN cash_machine.deposit deposit
                            ON deposit.id = operation.deposit_id
                        WHERE deposit.user_id = ?
                            AND operation.outcome = 'ACCEPTED'
                        """,
                        Long.class,
                        cashierId);

        assertEquals(2L, count);
    }

    @Test
    void otherCurrencyDoesNotIncreaseDeposit() throws Exception {
        MockHttpSession session = loginCashier();

        mockMvc.perform(
                        post("/api/deposits/current/banknotes")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(banknoteBody(UUID.randomUUID(), "USD", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CURRENCY_REJECTED"))
                .andExpect(jsonPath("$.deposit.totalCents").value(0))
                .andExpect(jsonPath("$.deposit.banknoteCount").value(0));
    }

    @Test
    void unsupportedEuroDenominationIsRejected() throws Exception {
        MockHttpSession session = loginCashier();

        mockMvc.perform(
                        post("/api/deposits/current/banknotes")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(banknoteBody(UUID.randomUUID(), "EUR", 25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DENOMINATION_REJECTED"))
                .andExpect(jsonPath("$.deposit.totalCents").value(0));
    }

    @Test
    void repeatingOperationDoesNotCountBanknoteTwice() throws Exception {
        MockHttpSession session = loginCashier();
        UUID operationId = UUID.randomUUID();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(
                            post("/api/deposits/current/banknotes")
                                    .session(session)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(banknoteBody(operationId, "EUR", 20)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.replayed").value(attempt == 1))
                    .andExpect(jsonPath("$.deposit.totalCents").value(2000))
                    .andExpect(jsonPath("$.deposit.banknoteCount").value(1));
        }
    }

    @Test
    void operationIdentifierCannotBeReusedWithDifferentContents() throws Exception {
        MockHttpSession session = loginCashier();
        UUID operationId = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/deposits/current/banknotes")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(banknoteBody(operationId, "EUR", 20)))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/deposits/current/banknotes")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(banknoteBody(operationId, "EUR", 50)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/deposits/current").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCents").value(2000));
    }

    @Test
    void anotherBrowserCannotStartASecondDeposit() throws Exception {
        loginCashier();

        mockMvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(cashierId, "CASHIER", "1234")))
                .andExpect(status().isConflict());
    }

    @Test
    void banknoteInsertionRequiresCsrf() throws Exception {
        MockHttpSession session = loginCashier();

        mockMvc.perform(
                        post("/api/deposits/current/banknotes")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(banknoteBody(UUID.randomUUID(), "EUR", 20)))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutCompletesDepositAndPreservesAcceptedMoney() throws Exception {
        MockHttpSession session = loginCashier();

        mockMvc.perform(
                        post("/api/deposits/current/banknotes")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(banknoteBody(UUID.randomUUID(), "EUR", 100)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        String depositStatus =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM cash_machine.deposit
                        WHERE user_id = ?
                        """,
                        String.class,
                        cashierId);

        assertEquals("COMPLETED", depositStatus);

        Long totalCents =
                jdbcTemplate.queryForObject(
                        """
                        SELECT SUM(operation.denomination::bigint * 100)
                        FROM cash_machine.deposit_banknote_operation operation
                        JOIN cash_machine.deposit deposit
                            ON deposit.id = operation.deposit_id
                        WHERE deposit.user_id = ?
                            AND operation.outcome = 'ACCEPTED'
                        """,
                        Long.class,
                        cashierId);

        assertEquals(10000L, totalCents);
    }

    @Test
    void expiredDepositDoesNotPreventNextCashierLogin() throws Exception {
        loginCashier();

        when(clock.instant()).thenReturn(now.plusSeconds(900));

        mockMvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(cashierId, "CASHIER", "1234")))
                .andExpect(status().isOk());

        Long expiredCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM cash_machine.deposit
                        WHERE user_id = ?
                            AND status = 'EXPIRED'
                        """,
                        Long.class,
                        cashierId);

        assertEquals(1L, expiredCount);
    }

    @Test
    void expiredSessionCannotInsertBanknotes() throws Exception {
        MockHttpSession session = loginCashier();

        when(clock.instant()).thenReturn(now.plusSeconds(900));

        mockMvc.perform(
                        post("/api/deposits/current/banknotes")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(banknoteBody(UUID.randomUUID(), "EUR", 20)))
                .andExpect(status().isUnauthorized());
    }

    private String banknoteBody(UUID operationId, String currency, int denomination) {
        return """
            {
                "operationId": "%s",
                "currency": "%s",
                "denomination": %d
            }
            """
                .formatted(operationId, currency, denomination);
    }

    @Test
    void repositoryReadsCompletedDepositWithFinishTime() throws Exception {
        MockHttpSession session = loginCashier();

        UUID loginId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT login_id
                        FROM cash_machine.deposit
                        WHERE user_id = ?
                            AND status = 'ACTIVE'
                        """,
                        UUID.class,
                        cashierId);

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        var deposit = depositRepository.findByLoginId(loginId).orElseThrow();

        assertEquals("COMPLETED", deposit.status());
        assertEquals(now, deposit.finishedAt());
    }

    @Test
    void nextLoginClosesDepositFromPreviousApplicationRun() throws Exception {
        loginCashier();

        jdbcTemplate.update(
                """
                UPDATE cash_machine.deposit
                SET runtime_id = ?
                WHERE user_id = ?
                    AND status = 'ACTIVE'
                """,
                UUID.randomUUID(),
                cashierId);

        mockMvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(cashierId, "CASHIER", "1234")))
                .andExpect(status().isOk());

        Long interruptedCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM cash_machine.deposit
                        WHERE user_id = ?
                            AND status = 'INTERRUPTED'
                            AND finished_at IS NOT NULL
                        """,
                        Long.class,
                        cashierId);

        assertEquals(1L, interruptedCount);
    }

    private MockHttpSession loginCashier() throws Exception {
        var result =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginBody(cashierId, "CASHIER", "1234")))
                        .andExpect(status().isOk())
                        .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UUID save(UserRole role, String pin, boolean active) {
        UUID id = UUID.randomUUID();

        repository.save(new UserAccount(id, "Test " + role, role, pinService.encode(pin), active));

        entityManager.flush();

        return id;
    }

    private String loginBody(UUID id, String role, String pin) {
        return """
                {"userId":"%s","role":"%s","pin":"%s"}
                """
                .formatted(id, role, pin);
    }
}
