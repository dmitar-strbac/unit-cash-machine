package rs.unit.backend.authentication.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

public class SessionValidityFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final UserAccountRepository repository;

    public SessionValidityFilter(Clock clock, UserAccountRepository repository) {
        this.clock = clock;
        this.repository = repository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {

            boolean expired = !clock.instant().isBefore(user.expiresAt());

            boolean accountValid =
                    !expired
                            && repository
                                    .findById(user.id())
                                    .filter(account -> account.isActive())
                                    .filter(account -> account.getRole() == user.role())
                                    .isPresent();

            if (!accountValid) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
