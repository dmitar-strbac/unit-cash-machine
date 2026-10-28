package rs.unit.backend.hardware.simulator;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.model.PhysicalAction;
import rs.unit.backend.hardware.port.HardwareControlPort;
import rs.unit.backend.machine.infrastructure.persistence.MachineOperationLock;

@Component
public class SimulatedHardware implements HardwareControlPort {

    private final JdbcTemplate jdbc;

    public SimulatedHardware(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public HardwareSnapshot snapshot() {
        return jdbc.queryForObject(
                """
                SELECT acceptor,
                       cashbox,
                       door,
                       lock_state,
                       extension_board,
                       printer
                FROM cash_machine.simulated_hardware
                WHERE id = 1
                """,
                (rs, rowNumber) ->
                        new HardwareSnapshot(
                                AcceptorState.valueOf(rs.getString("acceptor")),
                                CashboxState.valueOf(rs.getString("cashbox")),
                                DoorState.valueOf(rs.getString("door")),
                                LockState.valueOf(rs.getString("lock_state")),
                                ExtensionBoardState.valueOf(rs.getString("extension_board")),
                                PrinterState.valueOf(rs.getString("printer"))));
    }

    @Transactional
    public HardwareSnapshot replaceState(HardwareSnapshot newState) {
        Objects.requireNonNull(newState, "newState");

        MachineOperationLock.acquire(jdbc);

        int updated =
                jdbc.update(
                        """
                        UPDATE cash_machine.simulated_hardware
                        SET acceptor = ?,
                            cashbox = ?,
                            door = ?,
                            lock_state = ?,
                            extension_board = ?,
                            printer = ?
                        WHERE id = 1
                        """,
                        newState.acceptor().name(),
                        newState.cashbox().name(),
                        newState.door().name(),
                        newState.lock().name(),
                        newState.extensionBoard().name(),
                        newState.printer().name());

        if (updated != 1) {
            throw new IllegalStateException("Simulated hardware configuration is missing.");
        }

        return newState;
    }

    @Override
    @Transactional
    public HardwareSnapshot unlockDoor() {
        MachineOperationLock.acquire(jdbc);

        HardwareSnapshot current = snapshot();

        if (current.door() != DoorState.CLOSED
                || current.extensionBoard() != ExtensionBoardState.ONLINE
                || (current.lock() != LockState.LOCKED && current.lock() != LockState.UNLOCKED)) {
            throw conflict("The door cannot be unlocked in its current state.");
        }

        return replaceState(copy(current, current.cashbox(), current.door(), LockState.UNLOCKED));
    }

    @Override
    @Transactional
    public HardwareSnapshot simulate(PhysicalAction action) {
        Objects.requireNonNull(action, "action");

        MachineOperationLock.acquire(jdbc);

        HardwareSnapshot current = snapshot();

        HardwareSnapshot replacement =
                switch (action) {
                    case OPEN_DOOR -> {
                        if (current.lock() != LockState.UNLOCKED) {
                            throw conflict("Unlock the door before opening it.");
                        }

                        yield copy(current, current.cashbox(), DoorState.OPEN, current.lock());
                    }

                    case CLOSE_DOOR ->
                            copy(current, current.cashbox(), DoorState.CLOSED, current.lock());

                    case LOCK_DOOR -> {
                        if (current.door() != DoorState.CLOSED
                                || current.extensionBoard() != ExtensionBoardState.ONLINE
                                || current.lock() == LockState.FAULT
                                || current.lock() == LockState.UNKNOWN) {
                            throw conflict("The door cannot be locked in its current state.");
                        }

                        yield copy(current, current.cashbox(), current.door(), LockState.LOCKED);
                    }

                    case REMOVE_CASSETTE -> {
                        requireOpenDoor(current);

                        yield copy(current, CashboxState.ABSENT, current.door(), current.lock());
                    }

                    case INSERT_CASSETTE -> {
                        requireOpenDoor(current);

                        yield copy(current, CashboxState.PRESENT, current.door(), current.lock());
                    }
                };

        return replaceState(replacement);
    }

    private static HardwareSnapshot copy(
            HardwareSnapshot current, CashboxState cashbox, DoorState door, LockState lock) {
        return new HardwareSnapshot(
                current.acceptor(),
                cashbox,
                door,
                lock,
                current.extensionBoard(),
                current.printer());
    }

    private static void requireOpenDoor(HardwareSnapshot hardware) {
        if (hardware.door() != DoorState.OPEN || hardware.lock() != LockState.UNLOCKED) {
            throw conflict("Open the unlocked door before handling the cassette.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
