package rs.unit.backend.collection.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.collection.application.CollectionService;
import rs.unit.backend.collection.application.CollectionView;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final CollectionService service;

    public CollectionController(CollectionService service) {
        this.service = service;
    }

    @GetMapping("/current")
    public CollectionView current(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.current(user);
    }

    @PostMapping("/{id}/confirm-removal")
    public CollectionView confirmRemoval(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.confirmRemoval(user, id);
    }

    @PostMapping("/{id}/confirm-insertion")
    public CollectionView confirmInsertion(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.confirmInsertion(user, id);
    }

    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restart(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        service.restart(user, id);

        new CookieClearingLogoutHandler("UNIT_SESSION").logout(request, response, authentication);

        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
}
