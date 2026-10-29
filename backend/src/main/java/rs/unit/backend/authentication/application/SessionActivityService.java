package rs.unit.backend.authentication.application;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.user.domain.UserRole;

@Service
public class SessionActivityService {

    private static final String DEADLINE_ATTRIBUTE =
            SessionActivityService.class.getName() + ".deadline";

    private final DepositService deposits;
    private final Clock clock;

    public SessionActivityService(DepositService deposits, Clock clock) {
        this.deposits = deposits;
        this.clock = clock;
    }

    public void initialize(AuthenticatedUser user, HttpSession session) {
        session.setAttribute(
                DEADLINE_ATTRIBUTE,
                SessionTimeoutPolicy.inactivityDeadline(
                        user.role(), clock.instant(), user.expiresAt()));
    }

    public boolean isActive(AuthenticatedUser user, HttpSession session) {
        if (!clock.instant().isBefore(user.expiresAt())) {
            return false;
        }

        if (user.role() == UserRole.CIT) {
            return true;
        }

        if (user.role() == UserRole.CASHIER) {
            return deposits.isSessionActive(user);
        }

        if (session == null) {
            return false;
        }

        synchronized (session) {
            Object deadline = session.getAttribute(DEADLINE_ATTRIBUTE);

            return deadline instanceof Instant expiresAt && clock.instant().isBefore(expiresAt);
        }
    }

    public void recordActivity(AuthenticatedUser user, HttpSession session) {
        if (user.role() == UserRole.CASHIER) {
            deposits.recordActivity(user);
            return;
        }

        if (session == null) {
            throw expired();
        }

        synchronized (session) {
            if (!isActive(user, session)) {
                throw expired();
            }

            session.setAttribute(
                    DEADLINE_ATTRIBUTE,
                    SessionTimeoutPolicy.inactivityDeadline(
                            user.role(), clock.instant(), user.expiresAt()));
        }
    }

    private static ResponseStatusException expired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The session has expired.");
    }
}
