package rs.unit.backend.collection.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.collection.application.CollectionService;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.PhysicalAction;

@RestController
@Profile("simulation")
@RequestMapping("/api/collections")
public class CollectionSimulationController {

    private final CollectionService service;

    public CollectionSimulationController(CollectionService service) {
        this.service = service;
    }

    @PostMapping("/{id}/simulate")
    public HardwareSnapshot simulate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody SimulationRequest request) {
        return service.simulate(user, id, request.action());
    }

    public record SimulationRequest(@NotNull PhysicalAction action) {}
}
