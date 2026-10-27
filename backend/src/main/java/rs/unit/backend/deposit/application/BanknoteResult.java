package rs.unit.backend.deposit.application;

import java.util.UUID;
import rs.unit.backend.deposit.domain.BanknotePolicy.Outcome;

public record BanknoteResult(
        UUID operationId, Outcome outcome, boolean replayed, DepositView deposit) {}
