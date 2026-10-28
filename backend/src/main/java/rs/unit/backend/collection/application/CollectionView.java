package rs.unit.backend.collection.application;

import java.time.Instant;
import java.util.UUID;
import rs.unit.backend.collection.domain.CollectionStep;

public record CollectionView(
        UUID id,
        CollectionStep step,
        boolean doorClosed,
        boolean doorLocked,
        boolean removalConfirmed,
        boolean insertionConfirmed,
        Instant startedAt,
        Instant securingDeadline,
        Instant finishedAt,
        Long collectedTotalCents,
        String errorCode) {}
