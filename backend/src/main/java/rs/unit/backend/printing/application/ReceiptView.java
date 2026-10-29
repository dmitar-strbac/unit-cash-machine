package rs.unit.backend.printing.application;

import java.time.Instant;
import java.util.UUID;

public record ReceiptView(
        UUID id,
        String kind,
        UUID requestedBy,
        Instant createdAt,
        long totalCents,
        String content,
        String printStatus,
        String printError) {}
