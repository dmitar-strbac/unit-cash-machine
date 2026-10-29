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
import rs.unit.backend.authentication.application.SessionActivityService;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

public class SessionValidityFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final UserAccountRepository repository;
    private final SessionActivityService activityService;
    private final DepositService deposits;
    private final MachineRuntimeGuard runtime;

    public SessionValidityFilter(
            Clock clock,
            UserAccountRepository repository,
            SessionActivityService activityService,
            DepositService deposits,
            MachineRuntimeGuard runtime) {
        this.clock = clock;
        this.repository = repository;
        this.activityService = activityService;
        this.deposits = deposits;
        this.runtime = runtime;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            boolean accountValid =
                    clock.instant().isBefore(user.expiresAt())
                            && repository
                                    .findById(user.id())
                                    .filter(account -> account.isActive())
                                    .filter(account -> account.getRole() == user.role())
                                    .isPresent();

            boolean sessionValid =
                    accountValid
                            && runtime.isSessionValid(user)
                            && activityService.isActive(user, request.getSession(false));

            if (!sessionValid) {
                deposits.reconcile();

                new SecurityContextLogoutHandler().logout(request, response, authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
