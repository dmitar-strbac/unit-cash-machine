package rs.unit.backend.authentication.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LoginAttemptLimiterTest {

    @Test
    void blocksEleventhAttemptAndAllowsNewWindowAtBoundary() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-01-01T12:00:00Z");

        when(clock.instant()).thenReturn(start);

        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

        for (int i = 0; i < 10; i++) {
            limiter.acquire();
        }

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, limiter::acquire);

        assertEquals(429, exception.getStatusCode().value());

        when(clock.instant()).thenReturn(start.plusSeconds(59));
        assertThrows(ResponseStatusException.class, limiter::acquire);

        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertDoesNotThrow(limiter::acquire);
    }
}
