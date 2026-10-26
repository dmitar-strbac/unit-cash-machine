package rs.unit.backend.diagnostics.model;

import java.util.List;
import rs.unit.backend.diagnostics.model.MachineAlert.Severity;

public record MachineDiagnostics(List<MachineAlert> alerts) {

    public MachineDiagnostics {
        alerts = List.copyOf(alerts);
    }

    public Indicator indicator() {
        if (alerts.isEmpty()) {
            return Indicator.GREEN;
        }

        boolean hasError = alerts.stream().anyMatch(alert -> alert.severity() == Severity.ERROR);

        return hasError ? Indicator.RED : Indicator.AMBER;
    }

    public int alertCount() {
        return alerts.size();
    }

    public enum Indicator {
        GREEN,
        AMBER,
        RED
    }
}
