package rs.unit.backend.authentication.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import rs.unit.backend.user.domain.UserRole;

class SessionTimeoutPolicyTest {

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void cashierReceivesSixtySeconds() {
        assertEquals(
                now.plusSeconds(60),
                SessionTimeoutPolicy.inactivityDeadline(
                        UserRole.CASHIER, now, now.plusSeconds(900)));
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {"MANAGEMENT", "MAINTENANCE"})
    void otherOperatorsReceiveThirtySeconds(UserRole role) {
        assertEquals(
                now.plusSeconds(30),
                SessionTimeoutPolicy.inactivityDeadline(role, now, now.plusSeconds(900)));
    }

    @Test
    void citKeepsAbsoluteExpiration() {
        assertEquals(
                now.plusSeconds(900),
                SessionTimeoutPolicy.inactivityDeadline(UserRole.CIT, now, now.plusSeconds(900)));
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void activityCannotExtendAbsoluteExpiration(UserRole role) {
        assertEquals(
                now.plusSeconds(10),
                SessionTimeoutPolicy.inactivityDeadline(role, now, now.plusSeconds(10)));
    }
}
