package rs.unit.backend.maintenance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.maintenance.application.MaintenanceCommandView;
import rs.unit.backend.maintenance.application.MaintenanceService;
import rs.unit.backend.maintenance.domain.MaintenanceAction;

@RestController
@Profile("simulation")
public class RuntimeSimulationController {

    private final MaintenanceService service;

    public RuntimeSimulationController(MaintenanceService service) {
        this.service = service;
    }

    @PostMapping("/api/simulator/power-on")
    public MaintenanceCommandView powerOn(@Valid @RequestBody StartRequest request) {
        return service.resumeSimulator(request.requestId(), MaintenanceAction.POWER_ON);
    }

    @PostMapping("/api/simulator/start-application")
    public MaintenanceCommandView start(@Valid @RequestBody StartRequest request) {
        return service.resumeSimulator(request.requestId(), MaintenanceAction.START_APPLICATION);
    }

    public record StartRequest(@NotNull UUID requestId) {}
}
