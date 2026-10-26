package rs.unit.backend.machine.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.machine.application.MachineStatus;
import rs.unit.backend.machine.application.MachineStatusService;

@RestController
@RequestMapping("/api/machine")
public class MachineStatusController {

    private final MachineStatusService machineStatusService;

    public MachineStatusController(MachineStatusService machineStatusService) {
        this.machineStatusService = machineStatusService;
    }

    @GetMapping("/status")
    public MachineStatus getStatus() {
        return machineStatusService.getStatus();
    }
}
