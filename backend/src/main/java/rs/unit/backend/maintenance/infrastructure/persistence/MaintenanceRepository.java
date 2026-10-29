package rs.unit.backend.maintenance.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rs.unit.backend.machine.infrastructure.persistence.MachineOperationLock;
import rs.unit.backend.maintenance.application.MaintenanceCommandView;
import rs.unit.backend.maintenance.domain.MachineRunState;
import rs.unit.backend.maintenance.domain.MaintenanceAction;

@Repository
public class MaintenanceRepository {

    private final JdbcTemplate jdbc;

    public MaintenanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockMachine() {
        MachineOperationLock.acquire(jdbc);
    }

    public RuntimeState runtime() {
        return jdbc.queryForObject(
                """
                SELECT runtime.state,
                       runtime.generation,
                       runtime.command_id,
                       runtime.completes_at,
                       runtime.notice_until,
                       command.action
                FROM cash_machine.machine_runtime runtime
                LEFT JOIN cash_machine.maintenance_command command
                    ON command.id = runtime.command_id
                WHERE runtime.id = 1
                """,
                (rs, row) ->
                        new RuntimeState(
                                MachineRunState.valueOf(rs.getString("state")),
                                rs.getLong("generation"),
                                rs.getObject("command_id", UUID.class),
                                rs.getString("action") == null
                                        ? null
                                        : MaintenanceAction.valueOf(rs.getString("action")),
                                instant(rs, "completes_at"),
                                instant(rs, "notice_until")));
    }

    public Optional<MaintenanceCommandView> findCommand(UUID id) {
        return jdbc
                .query(
                        """
                        SELECT *
                        FROM cash_machine.maintenance_command
                        WHERE id = ?
                        """,
                        (rs, row) ->
                                new MaintenanceCommandView(
                                        rs.getObject("id", UUID.class),
                                        MaintenanceAction.valueOf(rs.getString("action")),
                                        rs.getObject("requested_by", UUID.class),
                                        rs.getString("status"),
                                        instant(rs, "requested_at"),
                                        instant(rs, "completes_at"),
                                        instant(rs, "finished_at")),
                        id)
                .stream()
                .findFirst();
    }

    public boolean hasActiveWork() {
        Boolean active =
                jdbc.queryForObject(
                        """
                        SELECT
                            EXISTS (
                                SELECT 1
                                FROM cash_machine.deposit
                                WHERE machine_id = 1
                                    AND status = 'ACTIVE'
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM cash_machine.cash_collection
                                WHERE step <> 'COMPLETED'
                            )
                        """,
                        Boolean.class);

        return Boolean.TRUE.equals(active);
    }

    public void createCommand(
            UUID id,
            MaintenanceAction action,
            UUID actor,
            Instant now,
            Instant completesAt,
            boolean pending) {
        jdbc.update(
                """
                INSERT INTO cash_machine.maintenance_command (
                    id, action, requested_by, status,
                    requested_at, completes_at, finished_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                action.name(),
                actor,
                pending ? "PENDING" : "COMPLETED",
                Timestamp.from(now),
                Timestamp.from(completesAt),
                pending ? null : Timestamp.from(now));
    }

    public void changeRuntime(
            MachineRunState state,
            UUID commandId,
            Instant completesAt,
            boolean invalidateSessions) {
        int changed =
                jdbc.update(
                        """
                        UPDATE cash_machine.machine_runtime
                        SET state = ?,
                            command_id = ?,
                            completes_at = ?,
                            notice_until = NULL,
                            generation = generation + ?
                        WHERE id = 1
                        """,
                        state.name(),
                        commandId,
                        timestamp(completesAt),
                        invalidateSessions ? 1 : 0);

        if (changed != 1) {
            throw new IllegalStateException("Machine runtime configuration is missing.");
        }
    }

    public void completeCommand(UUID id, Instant now, boolean reset) {
        int completed =
                jdbc.update(
                        """
                        UPDATE cash_machine.maintenance_command
                        SET status = 'COMPLETED',
                            finished_at = ?
                        WHERE id = ?
                            AND status = 'PENDING'
                        """,
                        Timestamp.from(now),
                        id);

        if (completed != 1) {
            throw new IllegalStateException("The maintenance command is not pending.");
        }

        if (reset) {
            jdbc.update(
                    """
                    UPDATE cash_machine.component_runtime
                    SET reset_count = reset_count + 1,
                        last_reset_at = ?
                    """,
                    Timestamp.from(now));
        }

        jdbc.update(
                """
                UPDATE cash_machine.machine_runtime
                SET state = 'RUNNING',
                    completes_at = NULL,
                    notice_until = ?
                WHERE id = 1
                    AND command_id = ?
                """,
                reset ? Timestamp.from(now.plusSeconds(10)) : null,
                id);
    }

    public List<ComponentReset> componentResets() {
        return jdbc.query(
                """
                SELECT component_id, reset_count, last_reset_at
                FROM cash_machine.component_runtime
                ORDER BY component_id
                """,
                (rs, row) ->
                        new ComponentReset(
                                rs.getString("component_id"),
                                rs.getLong("reset_count"),
                                instant(rs, "last_reset_at")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public record RuntimeState(
            MachineRunState state,
            long generation,
            UUID commandId,
            MaintenanceAction action,
            Instant completesAt,
            Instant noticeUntil) {}

    public record ComponentReset(String id, long resetCount, Instant lastResetAt) {}
}
