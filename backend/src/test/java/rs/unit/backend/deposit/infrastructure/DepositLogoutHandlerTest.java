package rs.unit.backend.deposit.infrastructure;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.user.domain.UserRole;

@ExtendWith(MockitoExtension.class)
class DepositLogoutHandlerTest {

    @Mock private DepositService service;

    private DepositLogoutHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DepositLogoutHandler(service);
    }

    @Test
    void finishesDepositForAuthenticatedApplicationUser() {
        var user =
                new AuthenticatedUser(
                        UUID.randomUUID(),
                        "Test Cashier",
                        UserRole.CASHIER,
                        Instant.parse("2026-01-01T12:15:00Z"));

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());

        handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication);

        verify(service).finish(user);
    }

    @Test
    void ignoresLogoutWithoutAuthentication() {
        handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(), null);

        verifyNoInteractions(service);
    }

    @Test
    void ignoresAuthenticationWithDifferentPrincipalType() {
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated("external-user", null, List.of());

        handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication);

        verifyNoInteractions(service);
    }
}
