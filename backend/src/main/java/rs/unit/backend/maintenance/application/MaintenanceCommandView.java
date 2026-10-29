package rs.unit.backend.maintenance.application;

import java.time.Instant;
import java.util.UUID;
import rs.unit.backend.maintenance.domain.MaintenanceAction;

public record MaintenanceCommandView(
        UUID id,
        MaintenanceAction action,
        UUID requestedBy,
        String status,
        Instant requestedAt,
        Instant completesAt,
        Instant finishedAt) {}
