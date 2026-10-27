package rs.unit.backend.authentication.api;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@SpringBootTest
@ActiveProfiles("simulation")
@Transactional
class AuthenticationIntegrationTest {

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private UserAccountRepository repository;
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
    void pollingDoesNotExtendAbsoluteSessionLifetime() throws Exception {
        MockHttpSession session = loginCashier();

        when(clock.instant()).thenReturn(now.plusSeconds(899));

        mockMvc.perform(get("/api/machine/status").session(session)).andExpect(status().isOk());

        when(clock.instant()).thenReturn(now.plusSeconds(900));

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
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
        MockHttpSession session = loginCashier();

        entityManager.flush();
        jdbcTemplate.update("DELETE FROM cash_machine.user_account WHERE id = ?", cashierId);
        entityManager.clear();

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
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

        return id;
    }

    private String loginBody(UUID id, String role, String pin) {
        return """
                {"userId":"%s","role":"%s","pin":"%s"}
                """
                .formatted(id, role, pin);
    }
}
