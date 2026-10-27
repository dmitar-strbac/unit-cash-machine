package rs.unit.backend.authentication.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.authentication.application.AuthenticationService;
import rs.unit.backend.user.domain.UserRole;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    @Mock private AuthenticationService authenticationService;
    @Mock private SecurityContextRepository contextRepository;
    @Mock private CsrfTokenRepository csrfRepository;

    private AuthenticationController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        controller =
                new AuthenticationController(
                        authenticationService, contextRepository, csrfRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginWithoutExistingAuthenticationCreatesSessionAndSavesContext() {
        UUID id = UUID.randomUUID();
        var user =
                new AuthenticatedUser(
                        id,
                        "Test Cashier",
                        UserRole.CASHIER,
                        Instant.parse("2026-01-01T12:15:00Z"));

        when(authenticationService.login(id, UserRole.CASHIER, "1234")).thenReturn(user);

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertNull(request.getSession(false));

        var result =
                controller.login(
                        new AuthenticationController.LoginRequest(id, UserRole.CASHIER, "1234"),
                        request,
                        response);

        assertSame(user, result);
        assertNotNull(request.getSession(false));

        assertSavedAuthentication(user, request, response);
        verify(csrfRepository).saveToken(null, request, response);
    }

    @Test
    void unauthenticatedTokenDoesNotPreventCitLogin() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.unauthenticated("pending", "unused"));

        var user =
                new AuthenticatedUser(
                        UUID.randomUUID(),
                        "Test CIT",
                        UserRole.CIT,
                        Instant.parse("2026-01-01T12:15:00Z"));

        when(authenticationService.loginCit("5678")).thenReturn(user);

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        var result =
                controller.loginCit(
                        new AuthenticationController.CitLoginRequest("5678"), request, response);

        assertSame(user, result);
        assertNotNull(request.getSession(false));

        assertSavedAuthentication(user, request, response);
        verify(csrfRepository).saveToken(null, request, response);
    }

    @Test
    void loginRequestToStringRedactsPin() {
        UUID id = UUID.randomUUID();
        String pin = "839174625083";

        var request = new AuthenticationController.LoginRequest(id, UserRole.CASHIER, pin);

        assertEquals(
                "LoginRequest[userId=" + id + ", role=CASHIER, pin=REDACTED]", request.toString());
        assertFalse(request.toString().contains(pin));
    }

    @Test
    void citLoginRequestToStringRedactsPin() {
        String pin = "839174625083";
        var request = new AuthenticationController.CitLoginRequest(pin);

        assertEquals("CitLoginRequest[pin=REDACTED]", request.toString());
        assertFalse(request.toString().contains(pin));
    }

    private void assertSavedAuthentication(
            AuthenticatedUser expectedUser,
            MockHttpServletRequest request,
            MockHttpServletResponse response) {

        var captor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(contextRepository).saveContext(captor.capture(), same(request), same(response));

        var context = captor.getValue();
        var authentication = context.getAuthentication();

        assertSame(context, SecurityContextHolder.getContext());
        assertSame(expectedUser, authentication.getPrincipal());
        assertTrue(authentication.isAuthenticated());
        assertNull(authentication.getCredentials());
        assertEquals(
                java.util.List.of("ROLE_" + expectedUser.role().name()),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList());
    }
}
