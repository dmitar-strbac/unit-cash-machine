package rs.unit.backend.machine.application;

import java.util.List;
import rs.unit.backend.diagnostics.model.MachineAlert;
import rs.unit.backend.diagnostics.model.MachineDiagnostics.Indicator;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.maintenance.application.MachineRuntimeView;

public record MachineStatus(
        String machineCode,
        String displayName,
        String location,
        String currency,
        String timeZone,
        boolean simulated,
        boolean secure,
        boolean depositHardwareReady,
        boolean printingAvailable,
        HardwareSnapshot hardware,
        Indicator indicator,
        int alertCount,
        List<MachineAlert> alerts,
        MachineRuntimeView runtime) {

    public MachineStatus {
        alerts = List.copyOf(alerts);
    }
}
