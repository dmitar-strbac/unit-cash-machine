package rs.unit.backend.printing.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfiguration;
import rs.unit.backend.printing.infrastructure.persistence.ReceiptRepository.DepositReceipt;
import rs.unit.backend.report.application.ReportType;
import rs.unit.backend.report.application.ReportView;

class ReceiptFormatterTest {

    private final ReceiptFormatter formatter = new ReceiptFormatter();
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void deviceReportContainsDenominationsAndTotals() {
        String text = formatter.report(machine(), report(ReportType.DEVICE_TOTAL), "Test Manager");

        assertTrue(text.contains("-- Device Report --"));
        assertTrue(text.contains("EUR 20.00 x 2 = 40.00"));
        assertTrue(text.contains("Total EUR: 40.00"));
        assertTrue(text.contains("-- End Device Report --"));
    }

    @Test
    void dailyReportContainsDenominationBreakdown() {
        String text = formatter.report(machine(), report(ReportType.DAY_TOTAL), "Test Manager");

        assertTrue(text.contains("-- Daily Report --"));
        assertTrue(text.contains("-- Total per denomination --"));
        assertTrue(text.contains("EUR 20.00 x 2 = 40.00"));
        assertTrue(text.contains("Date: 01-01-2026 13:00:00"));
    }

    @Test
    void dailyUserReportContainsUserBreakdown() {
        String text =
                formatter.report(machine(), report(ReportType.DAY_TOTAL_USER), "Test Manager");

        assertTrue(text.contains("-- Daily User Report --"));
        assertTrue(text.contains("-- Total per user --"));
        assertTrue(text.contains("Test Cashier"));
        assertTrue(text.contains("Users deposited: 1"));
        assertTrue(text.contains("Total EUR: 40.00"));
    }

    @Test
    void emptyUserReportContainsZeroUsersAndZeroTotal() {
        ReportView empty =
                new ReportView(
                        ReportType.DAY_TOTAL_USER,
                        "EUR",
                        "Europe/Belgrade",
                        now,
                        now.minusSeconds(3600),
                        now,
                        0,
                        0,
                        List.of(),
                        List.of());

        String text = formatter.report(machine(), empty, "Test Manager");

        assertTrue(text.contains("Users deposited: 0"));
        assertTrue(text.contains("Total EUR: 0.00"));
    }

    @Test
    void transactionReceiptContainsStableTransactionDetails() {
        DepositReceipt deposit =
                new DepositReceipt(
                        UUID.randomUUID(), UUID.randomUUID(), 42, now, "Test\nCashier", 2, 4000);

        String text = formatter.deposit(machine(), deposit);

        assertTrue(text.contains("User: Test Cashier"));
        assertTrue(text.contains("Bill amount: 2"));
        assertTrue(text.contains("Transaction #42"));
        assertTrue(text.contains("Total value EUR: 40.00"));
        assertTrue(text.contains("-- END OF RECEIPT --"));
    }

    private ReportView report(ReportType type) {
        return new ReportView(
                type,
                "EUR",
                "Europe/Belgrade",
                now,
                now.minusSeconds(3600),
                now,
                4000,
                2,
                List.of(new ReportView.DenominationTotal(20, 2, 4000)),
                List.of(new ReportView.UserTotal(UUID.randomUUID(), "Test Cashier", 2, 4000)));
    }

    private MachineConfiguration machine() {
        MachineConfiguration machine = mock(MachineConfiguration.class);

        when(machine.getDisplayName()).thenReturn("Unit Machine");
        when(machine.getMachineCode()).thenReturn("UNIT-001");
        when(machine.getTimeZone()).thenReturn("Europe/Belgrade");

        return machine;
    }
}
