package rs.unit.backend.collection.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.collection.domain.CollectionStep;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.machine.infrastructure.persistence.MachineOperationLock;

@Repository
public class CollectionRepository {

    private final JdbcTemplate jdbc;

    public CollectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockMachine() {
        MachineOperationLock.acquire(jdbc);
    }

    public Optional<StoredCollection> findUnfinished() {
        return jdbc
                .query(
                        """
                        SELECT *
                        FROM cash_machine.cash_collection
                        WHERE step <> 'COMPLETED'
                        """,
                        this::map)
                .stream()
                .findFirst();
    }

    public Optional<StoredCollection> findById(UUID id) {
        return jdbc
                .query(
                        """
                        SELECT *
                        FROM cash_machine.cash_collection
                        WHERE id = ?
                        """,
                        this::map,
                        id)
                .stream()
                .findFirst();
    }

    public Optional<StoredCollection> findByLoginId(UUID loginId) {
        return jdbc
                .query(
                        """
                        SELECT *
                        FROM cash_machine.cash_collection
                        WHERE login_id = ?
                        """,
                        this::map,
                        loginId)
                .stream()
                .findFirst();
    }

    public UUID currentCycleId() {
        return jdbc.queryForObject(
                """
                SELECT id
                FROM cash_machine.cash_cycle
                WHERE machine_id = 1
                    AND closed_at IS NULL
                """,
                UUID.class);
    }

    public void create(UUID id, UUID cycleId, AuthenticatedUser user, UUID runtimeId, Instant now) {
        jdbc.update(
                """
                INSERT INTO cash_machine.cash_collection (
                    id,
                    cash_cycle_id,
                    started_by,
                    operator_id,
                    login_id,
                    runtime_id,
                    lease_expires_at,
                    step,
                    started_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN_DOOR', ?)
                """,
                id,
                cycleId,
                user.id(),
                user.id(),
                user.loginId(),
                runtimeId,
                timestamp(user.expiresAt()),
                timestamp(now));
    }

    public void claim(UUID id, AuthenticatedUser user, UUID runtimeId) {
        jdbc.update(
                """
                UPDATE cash_machine.cash_collection
                SET operator_id = ?,
                    login_id = ?,
                    runtime_id = ?,
                    lease_expires_at = ?,
                    step = CASE
                        WHEN COALESCE(resume_step, step) = 'SECURING'
                            THEN 'CLOSE_DOOR'
                        ELSE COALESCE(resume_step, step)
                    END,
                    resume_step = NULL,
                    securing_deadline = NULL,
                    error_code = NULL
                WHERE id = ?
                    AND step <> 'COMPLETED'
                """,
                user.id(),
                user.loginId(),
                runtimeId,
                timestamp(user.expiresAt()),
                id);
    }

    public void changeStep(UUID id, CollectionStep step, Instant deadline) {
        jdbc.update(
                """
                UPDATE cash_machine.cash_collection
                SET step = ?,
                    securing_deadline = ?,
                    error_code = NULL
                WHERE id = ?
                """,
                step.name(),
                timestamp(deadline),
                id);
    }

    public void confirmRemoval(UUID id, Instant now) {
        jdbc.update(
                """
                UPDATE cash_machine.cash_collection
                SET removal_confirmed_at = ?,
                    step = 'INSERT_CASSETTE'
                WHERE id = ?
                """,
                timestamp(now),
                id);
    }

    public void confirmInsertion(UUID id, Instant now) {
        jdbc.update(
                """
                UPDATE cash_machine.cash_collection
                SET insertion_confirmed_at = ?,
                    step = 'CLOSE_DOOR'
                WHERE id = ?
                """,
                timestamp(now),
                id);
    }

    public void markIncomplete(UUID id, String errorCode) {
        jdbc.update(
                """
                UPDATE cash_machine.cash_collection
                SET resume_step = CASE
                        WHEN step = 'SECURING' THEN 'CLOSE_DOOR'
                        ELSE step
                    END,
                    step = 'INCOMPLETE',
                    securing_deadline = NULL,
                    error_code = ?
                WHERE id = ?
                    AND step NOT IN ('INCOMPLETE', 'COMPLETED')
                """,
                errorCode,
                id);
    }

    public void releaseLease(UUID id, Instant now) {
        jdbc.update(
                """
                UPDATE cash_machine.cash_collection
                SET lease_expires_at = ?
                WHERE id = ?
                    AND step = 'INCOMPLETE'
                """,
                timestamp(now),
                id);
    }

    public long cycleTotal(UUID cycleId) {
        Long total =
                jdbc.queryForObject(
                        """
                        SELECT COALESCE(SUM(operation.denomination::bigint * 100), 0)
                        FROM cash_machine.deposit_banknote_operation operation
                        JOIN cash_machine.deposit deposit
                            ON deposit.id = operation.deposit_id
                        WHERE deposit.cash_cycle_id = ?
                            AND operation.outcome = 'ACCEPTED'
                        """,
                        Long.class,
                        cycleId);

        return total == null ? 0 : total;
    }

    public void complete(StoredCollection collection, Instant now, long total) {
        int closed =
                jdbc.update(
                        """
                        UPDATE cash_machine.cash_cycle
                        SET closed_at = ?
                        WHERE id = ?
                            AND closed_at IS NULL
                        """,
                        timestamp(now),
                        collection.cashCycleId());

        if (closed != 1) {
            throw new IllegalStateException("The cash cycle has already been closed.");
        }

        jdbc.update(
                """
                INSERT INTO cash_machine.cash_cycle (
                    id,
                    machine_id,
                    opened_at
                )
                VALUES (?, 1, ?)
                """,
                UUID.randomUUID(),
                timestamp(now));

        jdbc.update(
                """
                UPDATE cash_machine.cash_collection
                SET step = 'COMPLETED',
                    resume_step = NULL,
                    securing_deadline = NULL,
                    finished_at = ?,
                    collected_total_cents = ?,
                    error_code = NULL
                WHERE id = ?
                """,
                timestamp(now),
                total,
                collection.id());
    }

    public void event(
            UUID collectionId,
            UUID actorId,
            String eventType,
            Instant now,
            HardwareSnapshot hardware) {
        jdbc.update(
                """
                INSERT INTO cash_machine.cash_collection_event (
                    collection_id,
                    actor_id,
                    event_type,
                    recorded_at,
                    door_state,
                    lock_state,
                    cashbox_state
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                collectionId,
                actorId,
                eventType,
                timestamp(now),
                hardware.door().name(),
                hardware.lock().name(),
                hardware.cashbox().name());
    }

    private StoredCollection map(ResultSet rs, int rowNumber) throws SQLException {
        return new StoredCollection(
                rs.getObject("id", UUID.class),
                rs.getObject("cash_cycle_id", UUID.class),
                rs.getObject("operator_id", UUID.class),
                rs.getObject("login_id", UUID.class),
                rs.getObject("runtime_id", UUID.class),
                instant(rs, "lease_expires_at"),
                CollectionStep.valueOf(rs.getString("step")),
                instant(rs, "started_at"),
                instant(rs, "removal_confirmed_at"),
                instant(rs, "insertion_confirmed_at"),
                instant(rs, "securing_deadline"),
                instant(rs, "finished_at"),
                rs.getObject("collected_total_cents", Long.class),
                rs.getString("error_code"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    public record StoredCollection(
            UUID id,
            UUID cashCycleId,
            UUID operatorId,
            UUID loginId,
            UUID runtimeId,
            Instant leaseExpiresAt,
            CollectionStep step,
            Instant startedAt,
            Instant removalConfirmedAt,
            Instant insertionConfirmedAt,
            Instant securingDeadline,
            Instant finishedAt,
            Long collectedTotalCents,
            String errorCode) {}
}
