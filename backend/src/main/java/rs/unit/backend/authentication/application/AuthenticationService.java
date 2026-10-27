package rs.unit.backend.authentication.application;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@Service
public class AuthenticationService {

    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(15);

    private final UserAccountRepository repository;
    private final PinService pinService;
    private final LoginAttemptLimiter attemptLimiter;
    private final Clock clock;

    private final String dummyPinHash;

    public AuthenticationService(
            UserAccountRepository repository,
            PinService pinService,
            LoginAttemptLimiter attemptLimiter,
            Clock clock) {
        this.repository = repository;
        this.pinService = pinService;
        this.attemptLimiter = attemptLimiter;
        this.clock = clock;
        this.dummyPinHash = pinService.encode("000000000000");
    }

    @Transactional(readOnly = true)
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

        return authenticatedUser(account);
    }

    @Transactional(readOnly = true)
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

        return authenticatedUser(matches.getFirst());
    }

    private AuthenticatedUser authenticatedUser(UserAccount account) {
        return new AuthenticatedUser(
                account.getId(),
                account.getDisplayName(),
                account.getRole(),
                clock.instant().plus(SESSION_LIFETIME));
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid credentials or account unavailable.");
    }
}
