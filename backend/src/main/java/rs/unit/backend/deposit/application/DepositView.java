package rs.unit.backend.deposit.application;

import java.time.Instant;
import java.util.UUID;

public record DepositView(
        UUID id,
        String status,
        String currency,
        long totalCents,
        long banknoteCount,
        Instant startedAt,
        Instant expiresAt,
        Instant finishedAt,
        boolean acceptingEnabled) {}
