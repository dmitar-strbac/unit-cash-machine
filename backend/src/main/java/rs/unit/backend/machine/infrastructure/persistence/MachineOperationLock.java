package rs.unit.backend.machine.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

public final class MachineOperationLock {

    private MachineOperationLock() {}

    public static void acquire(JdbcTemplate jdbc) {
        Short machineId =
                jdbc.queryForObject(
                        """
                        SELECT id
                        FROM cash_machine.machine_configuration
                        WHERE id = 1
                        FOR UPDATE
                        """,
                        Short.class);

        if (machineId == null) {
            throw new IllegalStateException("Machine configuration is missing.");
        }
    }
}
