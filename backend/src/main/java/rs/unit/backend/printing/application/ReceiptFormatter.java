package rs.unit.backend.printing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfiguration;
import rs.unit.backend.printing.infrastructure.persistence.ReceiptRepository.DepositReceipt;
import rs.unit.backend.report.application.ReportType;
import rs.unit.backend.report.application.ReportView;

@Component
public class ReceiptFormatter {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public String report(MachineConfiguration machine, ReportView report, String operator) {
        String title =
                switch (report.type()) {
                    case DEVICE_TOTAL -> "Device Report";
                    case DAY_TOTAL -> "Daily Report";
                    case DAY_TOTAL_USER -> "Daily User Report";
                };

        StringBuilder text = header(title, machine, report.generatedAt());

        text.append("Operator: ").append(clean(operator)).append('\n');
        text.append("Period from: ").append(date(report.periodStart(), machine)).append('\n');
        text.append("Period to: ").append(date(report.periodEnd(), machine)).append("\n\n");

        if (report.type() == ReportType.DAY_TOTAL_USER) {
            text.append("-- Total per user --\n");

            for (var user : report.users()) {
                text.append(clean(user.displayName())).append('\n');
                text.append("EUR ").append(money(user.totalCents())).append('\n');
            }

            text.append("\nUsers deposited: ").append(report.users().size()).append('\n');
        } else {
            text.append("-- Total per denomination --\n");

            for (var denomination : report.denominations()) {
                text.append("EUR ")
                        .append(money((long) denomination.denomination() * 100))
                        .append(" x ")
                        .append(denomination.banknoteCount())
                        .append(" = ")
                        .append(money(denomination.totalCents()))
                        .append('\n');
            }
        }

        text.append("\nTotal EUR: ").append(money(report.totalCents())).append('\n');
        text.append("-- End ").append(title).append(" --\n");

        return text.toString();
    }

    public String deposit(MachineConfiguration machine, DepositReceipt deposit) {
        StringBuilder text = header("TRANSACTION RECEIPT", machine, deposit.finishedAt());

        text.append("User: ").append(clean(deposit.displayName())).append('\n');
        text.append("Bill amount: ").append(deposit.banknoteCount()).append('\n');
        text.append("Transaction #").append(deposit.transactionNumber()).append('\n');
        text.append("Total value EUR: ").append(money(deposit.totalCents())).append('\n');
        text.append("-- END OF RECEIPT --\n");

        return text.toString();
    }

    private StringBuilder header(String title, MachineConfiguration machine, Instant instant) {
        return new StringBuilder()
                .append("-- ")
                .append(title)
                .append(" --\n\n")
                .append("Machine: ")
                .append(clean(machine.getDisplayName()))
                .append('\n')
                .append("Machine ID: ")
                .append(clean(machine.getMachineCode()))
                .append('\n')
                .append("Date: ")
                .append(date(instant, machine))
                .append('\n');
    }

    private String date(Instant instant, MachineConfiguration machine) {
        return DATE.format(instant.atZone(ZoneId.of(machine.getTimeZone())));
    }

    private String money(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private String clean(String value) {
        return value.replaceAll("\\p{Cntrl}", " ");
    }
}
