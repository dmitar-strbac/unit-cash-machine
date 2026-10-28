package rs.unit.backend.collection.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.collection.application.CollectionService;
import rs.unit.backend.user.domain.UserRole;

@ExtendWith(MockitoExtension.class)
class CollectionLogoutFilterTest {

    @Mock private CollectionService service;
    @Mock private FilterChain chain;

    private CollectionLogoutFilter filter;
    private AuthenticatedUser cit;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        filter = new CollectionLogoutFilter(service);

        cit =
                new AuthenticatedUser(
                        UUID.randomUUID(),
                        "Test CIT",
                        UserRole.CIT,
                        Instant.parse("2026-01-01T12:15:00Z"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresRequestsWithDifferentHttpMethod() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/auth/logout");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(service);
    }

    @Test
    void ignoresRequestsToDifferentPath() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/collections/current");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(service);
    }

    @Test
    void allowsLogoutWithoutAuthentication() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/logout");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(service);
    }

    @Test
    void allowsAuthenticationWithDifferentPrincipalType() throws Exception {
        authenticate("external-user");

        var request = new MockHttpServletRequest("POST", "/api/auth/logout");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(service);
    }

    @Test
    void allowsLogoutAfterCollectionCompletion() throws Exception {
        authenticate(cit);

        when(service.hasUnfinishedCollection(cit)).thenReturn(false);

        var request = new MockHttpServletRequest("POST", "/api/auth/logout");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void blocksLogoutDuringUnfinishedCollection() throws Exception {
        authenticate(cit);

        when(service.hasUnfinishedCollection(cit)).thenReturn(true);

        var request = new MockHttpServletRequest("POST", "/api/auth/logout");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(409, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertTrue(response.getContentAsString().contains("Complete the CIT procedure"));

        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void recognizesLogoutUnderServletContextPath() throws Exception {
        authenticate(cit);

        when(service.hasUnfinishedCollection(cit)).thenReturn(true);

        var request = new MockHttpServletRequest("POST", "/unit/api/auth/logout");
        request.setContextPath("/unit");

        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(409, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    private void authenticate(Object principal) {
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
