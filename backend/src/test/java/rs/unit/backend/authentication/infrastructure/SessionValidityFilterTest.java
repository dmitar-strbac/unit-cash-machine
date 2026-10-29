package rs.unit.backend.authentication.infrastructure;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.authentication.application.SessionActivityService;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

class SessionValidityFilterTest {

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final SessionActivityService activity = mock(SessionActivityService.class);
    private final DepositService deposits = mock(DepositService.class);
    private final MachineRuntimeGuard runtime = mock(MachineRuntimeGuard.class);
    private final FilterChain chain = mock(FilterChain.class);

    private final SessionValidityFilter filter =
            new SessionValidityFilter(
                    Clock.fixed(now, ZoneOffset.UTC), repository, activity, deposits, runtime);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidRuntimeSessionLogsOutEvenWhenAccountIsValid() throws Exception {
        UUID id = UUID.randomUUID();

        var user =
                new AuthenticatedUser(
                        id, "Test Manager", UserRole.MANAGEMENT, now.plusSeconds(900));

        var account = new UserAccount(id, "Test Manager", UserRole.MANAGEMENT, "unused-hash", true);

        when(repository.findById(id)).thenReturn(Optional.of(account));
        when(runtime.isSessionValid(user)).thenReturn(false);

        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());

        SecurityContextHolder.getContext().setAuthentication(authentication);

        var session = new MockHttpSession();
        var request = new MockHttpServletRequest("GET", "/api/maintenance/components");
        request.setSession(session);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(session.isInvalid());

        verify(runtime).isSessionValid(user);
        verify(deposits).reconcile();
        verifyNoInteractions(activity);
        verify(chain).doFilter(request, response);
    }

    @Test
    void unrelatedPrincipalPassesWithoutSessionValidation() throws Exception {
        var authentication =
                new UsernamePasswordAuthenticationToken("external-principal", null, List.of());

        SecurityContextHolder.getContext().setAuthentication(authentication);

        var request = new MockHttpServletRequest("GET", "/api/machine/status");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(repository, runtime, activity, deposits);
        verify(chain).doFilter(request, response);
    }
}
