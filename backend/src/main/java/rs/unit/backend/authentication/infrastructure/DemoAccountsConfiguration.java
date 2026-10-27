package rs.unit.backend.authentication.infrastructure;

import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import rs.unit.backend.authentication.application.PinService;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoAccountsConfiguration {

    @Bean
    public ApplicationRunner demoAccounts(UserAccountRepository repository, PinService pinService) {

        return args -> {
            createIfMissing(
                    repository,
                    pinService,
                    "00000000-0000-0000-0000-000000000001",
                    "Demo Cashier",
                    UserRole.CASHIER,
                    "1111");

            createIfMissing(
                    repository,
                    pinService,
                    "00000000-0000-0000-0000-000000000002",
                    "Demo Manager",
                    UserRole.MANAGEMENT,
                    "2222");

            createIfMissing(
                    repository,
                    pinService,
                    "00000000-0000-0000-0000-000000000003",
                    "Demo Maintenance",
                    UserRole.MAINTENANCE,
                    "3333");

            createIfMissing(
                    repository,
                    pinService,
                    "00000000-0000-0000-0000-000000000004",
                    "Demo CIT",
                    UserRole.CIT,
                    "4444");
        };
    }

    private void createIfMissing(
            UserAccountRepository repository,
            PinService pinService,
            String id,
            String displayName,
            UserRole role,
            String pin) {

        UUID userId = UUID.fromString(id);

        if (repository.findById(userId).isEmpty()) {
            repository.save(
                    new UserAccount(userId, displayName, role, pinService.encode(pin), true));
        }
    }
}
