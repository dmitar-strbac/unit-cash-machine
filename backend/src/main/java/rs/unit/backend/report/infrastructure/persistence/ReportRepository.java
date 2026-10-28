package rs.unit.backend.report.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rs.unit.backend.machine.infrastructure.persistence.MachineOperationLock;

@Repository
public class ReportRepository {

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockMachine() {
        MachineOperationLock.acquire(jdbc);
    }

    public Cycle currentCycle() {
        return jdbc.queryForObject(
                """
                SELECT id, opened_at
                FROM cash_machine.cash_cycle
                WHERE machine_id = 1
                    AND closed_at IS NULL
                """,
                (rs, row) ->
                        new Cycle(
                                rs.getObject("id", UUID.class),
                                rs.getTimestamp("opened_at").toInstant()));
    }

    public List<Row> cycleRows(UUID cycleId) {
        return jdbc.query(
                """
                SELECT deposit.user_id,
                       account.display_name,
                       operation.denomination,
                       COUNT(*) AS banknote_count
                FROM cash_machine.deposit_banknote_operation operation
                JOIN cash_machine.deposit deposit
                    ON deposit.id = operation.deposit_id
                JOIN cash_machine.user_account account
                    ON account.id = deposit.user_id
                WHERE operation.outcome = 'ACCEPTED'
                    AND deposit.cash_cycle_id = ?
                GROUP BY deposit.user_id,
                         account.display_name,
                         operation.denomination
                ORDER BY account.display_name,
                         deposit.user_id,
                         operation.denomination
                """,
                (rs, row) ->
                        new Row(
                                rs.getObject("user_id", UUID.class),
                                rs.getString("display_name"),
                                rs.getInt("denomination"),
                                rs.getLong("banknote_count")),
                cycleId);
    }

    public List<Row> dayRows(Instant start, Instant end) {
        return jdbc.query(
                """
                SELECT deposit.user_id,
                       account.display_name,
                       operation.denomination,
                       COUNT(*) AS banknote_count
                FROM cash_machine.deposit_banknote_operation operation
                JOIN cash_machine.deposit deposit
                    ON deposit.id = operation.deposit_id
                JOIN cash_machine.user_account account
                    ON account.id = deposit.user_id
                WHERE operation.outcome = 'ACCEPTED'
                    AND operation.recorded_at >= ?
                    AND operation.recorded_at < ?
                GROUP BY deposit.user_id,
                         account.display_name,
                         operation.denomination
                ORDER BY account.display_name,
                         deposit.user_id,
                         operation.denomination
                """,
                (rs, row) ->
                        new Row(
                                rs.getObject("user_id", UUID.class),
                                rs.getString("display_name"),
                                rs.getInt("denomination"),
                                rs.getLong("banknote_count")),
                Timestamp.from(start),
                Timestamp.from(end));
    }

    public record Cycle(UUID id, Instant openedAt) {}

    public record Row(UUID userId, String displayName, int denomination, long banknoteCount) {

        public long totalCents() {
            return Math.multiplyExact(Math.multiplyExact((long) denomination, 100L), banknoteCount);
        }
    }
}
