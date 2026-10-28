package rs.unit.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class MachineTestState {

    private MachineTestState() {}

    public static void reset(JdbcTemplate jdbc) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Machine test state must be reset inside a test transaction.");
        }

        jdbc.update("DELETE FROM cash_machine.print_attempt");
        jdbc.update("DELETE FROM cash_machine.receipt");
        jdbc.update("DELETE FROM cash_machine.cash_collection_event");
        jdbc.update("DELETE FROM cash_machine.cash_collection");
        jdbc.update("DELETE FROM cash_machine.deposit_banknote_operation");
        jdbc.update("DELETE FROM cash_machine.deposit");

        jdbc.update(
                """
                UPDATE cash_machine.simulated_hardware
                SET acceptor = 'READY',
                    cashbox = 'PRESENT',
                    door = 'CLOSED',
                    lock_state = 'LOCKED',
                    extension_board = 'ONLINE',
                    printer = 'READY'
                WHERE id = 1
                """);
    }
}
