package rs.unit.backend.authentication.application;

import java.time.Duration;
import java.time.Instant;
import rs.unit.backend.user.domain.UserRole;

public final class SessionTimeoutPolicy {

    private SessionTimeoutPolicy() {}

    public static Instant inactivityDeadline(
            UserRole role, Instant lastActivityAt, Instant absoluteExpiresAt) {
        Duration inactivity =
                switch (role) {
                    case CASHIER -> Duration.ofSeconds(60);
                    case MANAGEMENT, MAINTENANCE -> Duration.ofSeconds(30);
                    case CIT -> null;
                };

        if (inactivity == null) {
            return absoluteExpiresAt;
        }

        Instant deadline = lastActivityAt.plus(inactivity);

        return deadline.isBefore(absoluteExpiresAt) ? deadline : absoluteExpiresAt;
    }
}
