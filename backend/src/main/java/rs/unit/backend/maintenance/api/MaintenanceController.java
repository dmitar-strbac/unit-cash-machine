package rs.unit.backend.maintenance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.maintenance.application.ComponentService;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.maintenance.application.MachineRuntimeView;
import rs.unit.backend.maintenance.application.MaintenanceCommandView;
import rs.unit.backend.maintenance.application.MaintenanceService;
import rs.unit.backend.maintenance.domain.MaintenanceAction;

@RestController
public class MaintenanceController {

    private final MaintenanceService maintenance;
    private final MachineRuntimeGuard runtime;
    private final ComponentService components;

    public MaintenanceController(
            MaintenanceService maintenance,
            MachineRuntimeGuard runtime,
            ComponentService components) {
        this.maintenance = maintenance;
        this.runtime = runtime;
        this.components = components;
    }

    @GetMapping("/api/machine/runtime")
    public MachineRuntimeView runtime() {
        return runtime.view();
    }

    @PostMapping("/api/maintenance/commands")
    public MaintenanceCommandView execute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CommandRequest request) {
        return maintenance.execute(user, request.requestId(), request.action());
    }

    @GetMapping("/api/maintenance/commands/{id}")
    public MaintenanceCommandView command(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return maintenance.command(user, id);
    }

    @GetMapping("/api/maintenance/components")
    public List<ComponentService.ComponentView> components(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return components.components(user);
    }

    public record CommandRequest(@NotNull UUID requestId, @NotNull MaintenanceAction action) {}
}
