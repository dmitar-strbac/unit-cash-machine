package rs.unit.backend.authentication.application;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.collection.application.CollectionService;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@Service
public class AuthenticationService {

    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(15);

    private final UserAccountRepository repository;
    private final PinService pinService;
    private final DepositService depositService;
    private final CollectionService collectionService;
    private final LoginAttemptLimiter attemptLimiter;
    private final Clock clock;
    private final MachineRuntimeGuard runtime;

    private final String dummyPinHash;

    public AuthenticationService(
            UserAccountRepository repository,
            PinService pinService,
            DepositService depositService,
            CollectionService collectionService,
            LoginAttemptLimiter attemptLimiter,
            Clock clock,
            MachineRuntimeGuard runtime) {
        this.repository = repository;
        this.pinService = pinService;
        this.depositService = depositService;
        this.collectionService = collectionService;
        this.attemptLimiter = attemptLimiter;
        this.clock = clock;
        this.runtime = runtime;
        this.dummyPinHash = pinService.encode("000000000000");
    }

    @Transactional
    public AuthenticatedUser login(UUID userId, UserRole requestedRole, String pin) {
        attemptLimiter.acquire();

        UserAccount account = repository.findById(userId).orElse(null);

        boolean pinMatches =
                pinService.matches(pin, account == null ? dummyPinHash : account.getPinHash());

        if (account == null
                || !pinMatches
                || !account.isActive()
                || account.getRole() == UserRole.CIT
                || account.getRole() != requestedRole) {
            throw invalidCredentials();
        }

        AuthenticatedUser user = authenticatedUser(account);

        if (user.role() == UserRole.CASHIER) {
            depositService.start(user);
        }

        return user;
    }

    @Transactional
    public AuthenticatedUser loginCit(String pin) {
        attemptLimiter.acquire();

        var candidates = repository.findByRoleAndActiveTrueOrderByDisplayNameAscIdAsc(UserRole.CIT);

        var matches = new ArrayList<UserAccount>();

        for (UserAccount candidate : candidates) {
            if (pinService.matches(pin, candidate.getPinHash())) {
                matches.add(candidate);
            }
        }

        if (candidates.isEmpty()) {
            pinService.matches(pin, dummyPinHash);
        }

        if (matches.size() != 1) {
            throw invalidCredentials();
        }

        AuthenticatedUser user = authenticatedUser(matches.getFirst());

        collectionService.beginOrResume(user);

        return user;
    }

    private AuthenticatedUser authenticatedUser(UserAccount account) {
        long generation = runtime.generationForLogin();

        return new AuthenticatedUser(
                account.getId(),
                account.getDisplayName(),
                account.getRole(),
                clock.instant().plus(SESSION_LIFETIME),
                UUID.randomUUID(),
                generation);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid credentials or account unavailable.");
    }
}
