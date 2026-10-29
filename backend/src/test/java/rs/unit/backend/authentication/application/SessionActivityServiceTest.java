package rs.unit.backend.authentication.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.user.domain.UserRole;

class SessionActivityServiceTest {

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    private final Clock clock = mock(Clock.class);
    private final DepositService deposits = mock(DepositService.class);
    private final MockHttpSession session = new MockHttpSession();

    private SessionActivityService service;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
        service = new SessionActivityService(deposits, clock);
    }

    @Test
    void readingSessionDoesNotExtendItsDeadline() {
        AuthenticatedUser user = user(UserRole.MANAGEMENT);
        service.initialize(user, session);

        when(clock.instant()).thenReturn(now.plusSeconds(29));
        assertTrue(service.isActive(user, session));

        when(clock.instant()).thenReturn(now.plusSeconds(30));
        assertFalse(service.isActive(user, session));
    }

    @Test
    void interactionExtendsAnActiveSession() {
        AuthenticatedUser user = user(UserRole.MAINTENANCE);
        service.initialize(user, session);

        when(clock.instant()).thenReturn(now.plusSeconds(20));
        service.recordActivity(user, session);

        when(clock.instant()).thenReturn(now.plusSeconds(49));
        assertTrue(service.isActive(user, session));

        when(clock.instant()).thenReturn(now.plusSeconds(50));
        assertFalse(service.isActive(user, session));
    }

    @Test
    void interactionCannotReviveExpiredSession() {
        AuthenticatedUser user = user(UserRole.MANAGEMENT);
        service.initialize(user, session);

        when(clock.instant()).thenReturn(now.plusSeconds(30));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class, () -> service.recordActivity(user, session));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void missingSessionOrDeadlineIsRejected() {
        AuthenticatedUser user = user(UserRole.MANAGEMENT);

        assertFalse(service.isActive(user, null));
        assertFalse(service.isActive(user, session));

        assertThrows(ResponseStatusException.class, () -> service.recordActivity(user, null));

        assertThrows(ResponseStatusException.class, () -> service.recordActivity(user, session));
    }

    @Test
    void cashierValidityAndActivityUsePersistentDepositState() {
        AuthenticatedUser user = user(UserRole.CASHIER);
        when(deposits.isSessionActive(user)).thenReturn(true);

        assertTrue(service.isActive(user, session));

        service.recordActivity(user, session);

        verify(deposits).recordActivity(user);

        when(deposits.isSessionActive(user)).thenReturn(false);

        assertFalse(service.isActive(user, session));
    }

    @Test
    void citDoesNotExpireAfterThirtyOrSixtySeconds() {
        AuthenticatedUser user = user(UserRole.CIT);
        service.initialize(user, session);

        when(clock.instant()).thenReturn(now.plusSeconds(600));

        assertTrue(service.isActive(user, session));

        service.recordActivity(user, session);

        when(clock.instant()).thenReturn(now.plusSeconds(900));

        assertFalse(service.isActive(user, session));
    }

    private AuthenticatedUser user(UserRole role) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "Test Operator", role, now.plusSeconds(900));
    }
}
