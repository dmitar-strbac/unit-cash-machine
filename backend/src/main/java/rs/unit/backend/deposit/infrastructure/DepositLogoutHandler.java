package rs.unit.backend.deposit.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.deposit.application.DepositService;

@Component
public class DepositLogoutHandler implements LogoutHandler {

    private final DepositService service;

    public DepositLogoutHandler(DepositService service) {
        this.service = service;
    }

    @Override
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            service.finish(user);
        }
    }
}
