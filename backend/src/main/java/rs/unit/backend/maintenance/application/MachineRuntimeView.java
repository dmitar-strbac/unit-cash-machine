package rs.unit.backend.maintenance.application;

import java.time.Instant;
import java.util.UUID;
import rs.unit.backend.maintenance.domain.MachineRunState;
import rs.unit.backend.maintenance.domain.MaintenanceAction;

public record MachineRuntimeView(
        MachineRunState state,
        long generation,
        UUID commandId,
        MaintenanceAction action,
        Instant completesAt,
        String notice) {}
