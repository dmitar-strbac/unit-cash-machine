package rs.unit.backend.user.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import rs.unit.backend.authentication.application.PinService;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@SpringBootTest(
        properties = {
            "unit.collection.monitor.enabled=false",
            "unit.deposit.monitor.enabled=false",
            "unit.maintenance.monitor.enabled=false"
        })
@Transactional
class UserDirectoryIntegrationTest {

    @Autowired private WebApplicationContext applicationContext;

    @Autowired private UserAccountRepository repository;

    @Autowired private PinService pinService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private EntityManager entityManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.webAppContextSetup(applicationContext)
                        .apply(springSecurity())
                        .build();

        jdbcTemplate.update("UPDATE cash_machine.user_account SET active = FALSE");
    }

    @Test
    void returnsOnlyActiveUsersOfRequestedRoleWithoutCredentials() throws Exception {
        UserAccount cashier = save("Ana", UserRole.CASHIER, true);
        save("Inactive cashier", UserRole.CASHIER, false);
        save("Manager", UserRole.MANAGEMENT, true);
        save("CIT operator", UserRole.CIT, true);

        mockMvc.perform(get("/api/users").param("role", "CASHIER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(cashier.getId().toString()))
                .andExpect(jsonPath("$[0].displayName").value("Ana"))
                .andExpect(jsonPath("$[0].role").value("CASHIER"))
                .andExpect(jsonPath("$[0].pin").doesNotExist())
                .andExpect(jsonPath("$[0].pinHash").doesNotExist())
                .andExpect(jsonPath("$[0].active").doesNotExist());
    }

    @Test
    void returnsUsersInDisplayNameOrder() throws Exception {
        save("Zoran", UserRole.MANAGEMENT, true);
        save("Ana", UserRole.MANAGEMENT, true);

        mockMvc.perform(get("/api/users").param("role", "MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].displayName").value("Ana"))
                .andExpect(jsonPath("$[1].displayName").value("Zoran"));
    }

    @Test
    void citAccountsAreNotListed() throws Exception {
        save("CIT operator", UserRole.CIT, true);

        mockMvc.perform(get("/api/users").param("role", "CIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        mockMvc.perform(get("/api/users").param("role", "UNKNOWN_ROLE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingRoleIsRejected() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isBadRequest());
    }

    @Test
    void storesHashedPinAndAccountState() {
        UserAccount account = save("Technician", UserRole.MAINTENANCE, false);
        entityManager.flush();
        entityManager.clear();

        String storedHash =
                jdbcTemplate.queryForObject(
                        "SELECT pin_hash FROM cash_machine.user_account WHERE id = ?",
                        String.class,
                        account.getId());

        assertTrue(pinService.matches("0123", storedHash));
        assertFalse(account.isActive());
    }

    private UserAccount save(String displayName, UserRole role, boolean active) {
        return repository.save(
                new UserAccount(
                        UUID.randomUUID(), displayName, role, pinService.encode("0123"), active));
    }
}
