package rs.unit.backend.authentication.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import rs.unit.backend.authentication.application.PinService;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class DemoAccountsConfigurationTest {

    @Mock private UserAccountRepository repository;

    private final Map<UUID, UserAccount> accounts = new HashMap<>();
    private final PinService pinService = new PinService(new BCryptPasswordEncoder(4));

    @BeforeEach
    void setUp() {
        when(repository.findById(any(UUID.class)))
                .thenAnswer(
                        invocation -> Optional.ofNullable(accounts.get(invocation.getArgument(0))));

        when(repository.save(any(UserAccount.class)))
                .thenAnswer(
                        invocation -> {
                            UserAccount account = invocation.getArgument(0);
                            accounts.put(account.getId(), account);
                            return account;
                        });
    }

    @Test
    void createsExpectedDemoAccountsWithHashedPins() throws Exception {
        var runner = new DemoAccountsConfiguration().demoAccounts(repository, pinService);

        runner.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(4, accounts.size());

        for (ExpectedAccount expected : expectedAccounts()) {
            UserAccount actual = accounts.get(expected.id());

            org.junit.jupiter.api.Assertions.assertNotNull(actual);
            assertEquals(expected.name(), actual.getDisplayName());
            assertEquals(expected.role(), actual.getRole());
            assertTrue(actual.isActive());
            assertNotEquals(expected.pin(), actual.getPinHash());
            assertTrue(pinService.matches(expected.pin(), actual.getPinHash()));
        }
    }

    @Test
    void runningInitializerAgainDoesNotOverwriteExistingAccounts() throws Exception {
        var runner = new DemoAccountsConfiguration().demoAccounts(repository, pinService);

        runner.run(new DefaultApplicationArguments(new String[0]));

        UUID cashierId = expectedAccounts().getFirst().id();
        var customizedCashier =
                new UserAccount(
                        cashierId,
                        "Renamed Cashier",
                        UserRole.CASHIER,
                        pinService.encode("9876"),
                        false);
        accounts.put(cashierId, customizedCashier);

        Map<UUID, UserAccount> before = Map.copyOf(accounts);
        clearInvocations(repository);

        runner.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(before, accounts);
        verify(repository, never()).save(any(UserAccount.class));

        UserAccount cashier = accounts.get(cashierId);
        assertEquals("Renamed Cashier", cashier.getDisplayName());
        org.junit.jupiter.api.Assertions.assertFalse(cashier.isActive());
        assertTrue(pinService.matches("9876", cashier.getPinHash()));
    }

    private List<ExpectedAccount> expectedAccounts() {
        return List.of(
                new ExpectedAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "Demo Cashier",
                        UserRole.CASHIER,
                        "1111"),
                new ExpectedAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        "Demo Manager",
                        UserRole.MANAGEMENT,
                        "2222"),
                new ExpectedAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        "Demo Maintenance",
                        UserRole.MAINTENANCE,
                        "3333"),
                new ExpectedAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000004"),
                        "Demo CIT",
                        UserRole.CIT,
                        "4444"));
    }

    private record ExpectedAccount(UUID id, String name, UserRole role, String pin) {}
}
