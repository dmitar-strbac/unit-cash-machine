package rs.unit.backend.authentication.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LoginAttemptLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;

    private Instant windowStartedAt;
    private int attempts;

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void acquire() {
        Instant now = clock.instant();

        if (windowStartedAt == null || !now.isBefore(windowStartedAt.plus(WINDOW))) {
            windowStartedAt = now;
            attempts = 0;
        }

        if (attempts >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Please try again later.");
        }

        attempts++;
    }
}
