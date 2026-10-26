package rs.unit.backend.simulation.api;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.simulator.SimulatedHardware;

@RestController
@RequestMapping("/api/simulation")
@Profile("simulation")
public class SimulationController {

    private final SimulatedHardware simulatedHardware;

    public SimulationController(SimulatedHardware simulatedHardware) {
        this.simulatedHardware = simulatedHardware;
    }

    @PutMapping("/hardware")
    public HardwareSnapshot replaceHardware(@Valid @RequestBody HardwareStateRequest request) {
        return simulatedHardware.replaceState(request.toSnapshot());
    }
}
