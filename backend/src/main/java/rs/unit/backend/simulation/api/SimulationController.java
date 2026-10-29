package rs.unit.backend.simulation.api;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.simulator.SimulatedHardware;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.maintenance.infrastructure.persistence.MaintenanceRepository;

@RestController
@RequestMapping("/api/simulation")
@Profile("simulation")
public class SimulationController {

    private final SimulatedHardware simulatedHardware;
    private final MachineRuntimeGuard runtime;
    private final MaintenanceRepository maintenance;

    public SimulationController(
            SimulatedHardware simulatedHardware,
            MachineRuntimeGuard runtime,
            MaintenanceRepository maintenance) {
        this.simulatedHardware = simulatedHardware;
        this.runtime = runtime;
        this.maintenance = maintenance;
    }

    @PutMapping("/hardware")
    @Transactional
    public HardwareSnapshot replaceHardware(@Valid @RequestBody HardwareStateRequest request) {
        maintenance.lockMachine();
        runtime.requireRunning();

        return simulatedHardware.replaceState(request.toSnapshot());
    }
}
