package rs.unit.backend.deposit.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rs.unit.backend.deposit.domain.BanknotePolicy.Outcome;
import rs.unit.backend.machine.infrastructure.persistence.MachineOperationLock;

@Repository
public class DepositRepository {

    private final JdbcTemplate jdbc;

    public DepositRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockMachine() {
        MachineOperationLock.acquire(jdbc);
    }

    public boolean hasUnfinishedCollection() {
        Boolean unfinished =
                jdbc.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM cash_machine.cash_collection
                            WHERE step <> 'COMPLETED'
                        )
                        """,
                        Boolean.class);

        return Boolean.TRUE.equals(unfinished);
    }

    public void closeDepositsOfUnavailableAccounts(Instant now) {
        jdbc.update(
                """
                UPDATE cash_machine.deposit deposit
                SET status = 'INTERRUPTED',
                    finished_at = ?
                FROM cash_machine.user_account account
                WHERE deposit.user_id = account.id
                    AND deposit.status = 'ACTIVE'
                    AND (account.active = FALSE OR account.role <> 'CASHIER')
                """,
                timestamp(now));
    }

    public void closeUnavailableDeposits(UUID runtimeId, Instant now) {
        jdbc.update(
                """
                UPDATE cash_machine.deposit
                SET status = CASE
                        WHEN expires_at <= ? THEN 'EXPIRED'
                        ELSE 'INTERRUPTED'
                    END,
                    finished_at = CASE
                        WHEN expires_at <= ? THEN expires_at
                        ELSE ?
                    END
                WHERE machine_id = 1
                    AND status = 'ACTIVE'
                    AND (expires_at <= ? OR runtime_id <> ?)
                """,
                timestamp(now),
                timestamp(now),
                timestamp(now),
                timestamp(now),
                runtimeId);
    }

    public boolean hasActiveDeposit() {
        Boolean active =
                jdbc.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM cash_machine.deposit
                            WHERE machine_id = 1
                                AND status = 'ACTIVE'
                        )
                        """,
                        Boolean.class);

        return Boolean.TRUE.equals(active);
    }

    public void create(
            UUID depositId,
            UUID userId,
            UUID loginId,
            UUID runtimeId,
            Instant startedAt,
            Instant expiresAt) {
        jdbc.update(
                """
                INSERT INTO cash_machine.deposit (
                    id,
                    machine_id,
                    user_id,
                    login_id,
                    runtime_id,
                    status,
                    started_at,
                    expires_at,
                    cash_cycle_id
                )
                VALUES (
                    ?, 1, ?, ?, ?, 'ACTIVE', ?, ?,
                    (
                        SELECT id
                        FROM cash_machine.cash_cycle
                        WHERE machine_id = 1
                            AND closed_at IS NULL
                    )
                )
                """,
                depositId,
                userId,
                loginId,
                runtimeId,
                timestamp(startedAt),
                timestamp(expiresAt));
    }

    public Optional<StoredDeposit> findByLoginId(UUID loginId) {
        List<StoredDeposit> deposits =
                jdbc.query(
                        """
                        SELECT id,
                               user_id,
                               login_id,
                               runtime_id,
                               status,
                               started_at,
                               expires_at,
                               finished_at
                        FROM cash_machine.deposit
                        WHERE login_id = ?
                        """,
                        this::mapDeposit,
                        loginId);

        return deposits.stream().findFirst();
    }

    public Optional<StoredOperation> findOperation(UUID operationId) {
        List<StoredOperation> operations =
                jdbc.query(
                        """
                        SELECT id,
                               deposit_id,
                               currency,
                               denomination,
                               outcome
                        FROM cash_machine.deposit_banknote_operation
                        WHERE id = ?
                        """,
                        (rs, rowNumber) ->
                                new StoredOperation(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("deposit_id", UUID.class),
                                        rs.getString("currency"),
                                        rs.getInt("denomination"),
                                        Outcome.valueOf(rs.getString("outcome"))),
                        operationId);

        return operations.stream().findFirst();
    }

    public void recordOperation(
            UUID operationId,
            UUID depositId,
            String currency,
            int denomination,
            Outcome outcome,
            Instant recordedAt) {
        jdbc.update(
                """
                INSERT INTO cash_machine.deposit_banknote_operation (
                    id,
                    deposit_id,
                    currency,
                    denomination,
                    outcome,
                    recorded_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                operationId,
                depositId,
                currency,
                denomination,
                outcome.name(),
                timestamp(recordedAt));
    }

    public Totals totals(UUID depositId) {
        return jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(denomination::bigint * 100), 0) AS total_cents,
                       COUNT(*) AS banknote_count
                FROM cash_machine.deposit_banknote_operation
                WHERE deposit_id = ?
                    AND outcome = 'ACCEPTED'
                """,
                (rs, rowNumber) ->
                        new Totals(rs.getLong("total_cents"), rs.getLong("banknote_count")),
                depositId);
    }

    public void complete(UUID depositId, Instant finishedAt) {
        jdbc.update(
                """
                UPDATE cash_machine.deposit
                SET status = 'COMPLETED',
                    finished_at = ?
                WHERE id = ?
                    AND status = 'ACTIVE'
                """,
                timestamp(finishedAt),
                depositId);
    }

    private StoredDeposit mapDeposit(ResultSet rs, int rowNumber) throws SQLException {
        Timestamp finishedAt = rs.getTimestamp("finished_at");

        return new StoredDeposit(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("login_id", UUID.class),
                rs.getObject("runtime_id", UUID.class),
                rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                finishedAt == null ? null : finishedAt.toInstant());
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    public record StoredDeposit(
            UUID id,
            UUID userId,
            UUID loginId,
            UUID runtimeId,
            String status,
            Instant startedAt,
            Instant expiresAt,
            Instant finishedAt) {}

    public record StoredOperation(
            UUID id, UUID depositId, String currency, int denomination, Outcome outcome) {}

    public record Totals(long totalCents, long banknoteCount) {}
}
