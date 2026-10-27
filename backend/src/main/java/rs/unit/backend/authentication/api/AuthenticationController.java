package rs.unit.backend.authentication.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.authentication.application.AuthenticationService;
import rs.unit.backend.user.domain.UserRole;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfRepository;

    public AuthenticationController(
            AuthenticationService authenticationService,
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfRepository) {
        this.authenticationService = authenticationService;
        this.contextRepository = contextRepository;
        this.csrfRepository = csrfRepository;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    public AuthenticatedUser login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        rejectExistingLogin();

        AuthenticatedUser user =
                authenticationService.login(body.userId(), body.role(), body.pin());

        establishSession(user, request, response);
        return user;
    }

    @PostMapping("/cit-login")
    public AuthenticatedUser loginCit(
            @Valid @RequestBody CitLoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        rejectExistingLogin();

        AuthenticatedUser user = authenticationService.loginCit(body.pin());

        establishSession(user, request, response);
        return user;
    }

    @GetMapping("/me")
    public AuthenticatedUser me(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    private void rejectExistingLogin() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();

        if (current != null
                && current.isAuthenticated()
                && !(current instanceof AnonymousAuthenticationToken)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Log out before signing in as another user.");
        }
    }

    private void establishSession(
            AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) {

        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        csrfRepository.saveToken(null, request, response);
    }

    public record CsrfResponse(String headerName, String token) {}

    public record LoginRequest(
            @NotNull UUID userId,
            @NotNull UserRole role,
            @NotNull @Pattern(regexp = "[0-9]{4,12}") String pin) {

        @Override
        public String toString() {
            return "LoginRequest[userId=" + userId + ", role=" + role + ", pin=REDACTED]";
        }
    }

    public record CitLoginRequest(@NotNull @Pattern(regexp = "[0-9]{4,12}") String pin) {

        @Override
        public String toString() {
            return "CitLoginRequest[pin=REDACTED]";
        }
    }
}
