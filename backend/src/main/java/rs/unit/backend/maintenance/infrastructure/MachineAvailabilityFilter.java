package rs.unit.backend.maintenance.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.maintenance.domain.MachineRunState;

public class MachineAvailabilityFilter extends OncePerRequestFilter {

    private static final Set<String> ALWAYS_AVAILABLE =
            Set.of(
                    "/api/machine/status",
                    "/api/machine/runtime",
                    "/api/auth/csrf",
                    "/api/simulator/power-on",
                    "/api/simulator/start-application");

    private final MachineRuntimeGuard runtime;

    public MachineAvailabilityFilter(MachineRuntimeGuard runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (!path.startsWith("/api/") || ALWAYS_AVAILABLE.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        var state = runtime.view().state();

        boolean resetRead =
                state == MachineRunState.RESETTING
                        && ("GET".equals(request.getMethod())
                                || path.equals("/api/auth/activity")
                                || path.equals("/api/auth/logout"));

        if (state == MachineRunState.RUNNING || resetRead) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(503);
        response.setContentType("application/problem+json");
        response.setHeader("Retry-After", "2");
        response.getWriter()
                .write(
                        """
                {"type":"about:blank","title":"Machine unavailable","status":503,"detail":"The simulated machine application is not running."}
                """);
    }
}
