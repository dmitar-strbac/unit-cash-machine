package rs.unit.backend.collection.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.collection.application.CollectionService;

public class CollectionLogoutFilter extends OncePerRequestFilter {

    private final CollectionService service;

    public CollectionLogoutFilter(CollectionService service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean logoutRequest =
                "POST".equals(request.getMethod())
                        && request.getRequestURI()
                                .equals(request.getContextPath() + "/api/auth/logout");

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (logoutRequest
                && authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && service.hasUnfinishedCollection(user)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType("application/problem+json");
            response.getWriter()
                    .write(
                            """
                            {"type":"about:blank","title":"Conflict","status":409,"detail":"Complete the CIT procedure before signing out."}
                            """);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
