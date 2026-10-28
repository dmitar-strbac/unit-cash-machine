package rs.unit.backend.report.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportView(
        ReportType type,
        String currency,
        String timeZone,
        Instant generatedAt,
        Instant periodStart,
        Instant periodEnd,
        long totalCents,
        long banknoteCount,
        List<DenominationTotal> denominations,
        List<UserTotal> users) {

    public ReportView {
        denominations = List.copyOf(denominations);
        users = List.copyOf(users);
    }

    public record DenominationTotal(int denomination, long banknoteCount, long totalCents) {}

    public record UserTotal(UUID userId, String displayName, long banknoteCount, long totalCents) {}
}
