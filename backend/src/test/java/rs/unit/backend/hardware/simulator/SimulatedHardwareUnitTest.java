package rs.unit.backend.hardware.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.model.PhysicalAction;

@ExtendWith(MockitoExtension.class)
class SimulatedHardwareUnitTest {

    @Mock private JdbcTemplate jdbc;

    private SimulatedHardware simulator;

    @BeforeEach
    void setUp() {
        simulator = spy(new SimulatedHardware(jdbc));

        lenient().when(jdbc.queryForObject(anyString(), eq(Short.class))).thenReturn((short) 1);

        lenient().when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    @ParameterizedTest
    @MethodSource("unlockFailures")
    void rejectsUnlockWhenGateIsUnavailable(HardwareSnapshot snapshot) {
        state(snapshot);

        assertConflict(simulator::unlockDoor);
    }

    static Stream<HardwareSnapshot> unlockFailures() {
        return Stream.of(
                gate(DoorState.OPEN, LockState.LOCKED, ExtensionBoardState.ONLINE),
                gate(DoorState.CLOSED, LockState.LOCKED, ExtensionBoardState.OFFLINE),
                gate(DoorState.CLOSED, LockState.FAULT, ExtensionBoardState.ONLINE),
                gate(DoorState.CLOSED, LockState.UNKNOWN, ExtensionBoardState.ONLINE));
    }

    @Test
    void unlockingAlreadyUnlockedDoorIsIdempotent() {
        state(gate(DoorState.CLOSED, LockState.UNLOCKED, ExtensionBoardState.ONLINE));

        assertEquals(LockState.UNLOCKED, simulator.unlockDoor().lock());
    }

    @Test
    void opensUnlockedDoor() {
        state(gate(DoorState.CLOSED, LockState.UNLOCKED, ExtensionBoardState.ONLINE));

        assertEquals(DoorState.OPEN, simulator.simulate(PhysicalAction.OPEN_DOOR).door());
    }

    @Test
    void rejectsOpeningLockedDoor() {
        state(gate(DoorState.CLOSED, LockState.LOCKED, ExtensionBoardState.ONLINE));

        assertConflict(() -> simulator.simulate(PhysicalAction.OPEN_DOOR));
    }

    @Test
    void closesDoor() {
        state(gate(DoorState.OPEN, LockState.UNLOCKED, ExtensionBoardState.ONLINE));

        assertEquals(DoorState.CLOSED, simulator.simulate(PhysicalAction.CLOSE_DOOR).door());
    }

    @ParameterizedTest
    @MethodSource("lockingFailures")
    void rejectsLockingWhenGateIsUnavailable(HardwareSnapshot snapshot) {
        state(snapshot);

        assertConflict(() -> simulator.simulate(PhysicalAction.LOCK_DOOR));
    }

    static Stream<HardwareSnapshot> lockingFailures() {
        return Stream.of(
                gate(DoorState.OPEN, LockState.UNLOCKED, ExtensionBoardState.ONLINE),
                gate(DoorState.CLOSED, LockState.UNLOCKED, ExtensionBoardState.OFFLINE),
                gate(DoorState.CLOSED, LockState.FAULT, ExtensionBoardState.ONLINE),
                gate(DoorState.CLOSED, LockState.UNKNOWN, ExtensionBoardState.ONLINE));
    }

    @Test
    void locksClosedDoor() {
        state(gate(DoorState.CLOSED, LockState.UNLOCKED, ExtensionBoardState.ONLINE));

        assertEquals(LockState.LOCKED, simulator.simulate(PhysicalAction.LOCK_DOOR).lock());
    }

    @Test
    void lockingAlreadyLockedDoorIsIdempotent() {
        state(gate(DoorState.CLOSED, LockState.LOCKED, ExtensionBoardState.ONLINE));

        assertEquals(LockState.LOCKED, simulator.simulate(PhysicalAction.LOCK_DOOR).lock());
    }

    @ParameterizedTest
    @MethodSource("cassetteActions")
    void changesCassetteStateWithOpenDoor(PhysicalAction action, CashboxState expected) {
        state(gate(DoorState.OPEN, LockState.UNLOCKED, ExtensionBoardState.ONLINE));

        assertEquals(expected, simulator.simulate(action).cashbox());
    }

    static Stream<Arguments> cassetteActions() {
        return Stream.of(
                Arguments.of(PhysicalAction.REMOVE_CASSETTE, CashboxState.ABSENT),
                Arguments.of(PhysicalAction.INSERT_CASSETTE, CashboxState.PRESENT));
    }

    @ParameterizedTest
    @MethodSource("cassetteFailures")
    void rejectsCassetteHandlingWithoutOpenUnlockedDoor(
            PhysicalAction action, DoorState door, LockState lock) {
        state(gate(door, lock, ExtensionBoardState.ONLINE));

        assertConflict(() -> simulator.simulate(action));
    }

    static Stream<Arguments> cassetteFailures() {
        return Stream.of(
                Arguments.of(PhysicalAction.REMOVE_CASSETTE, DoorState.CLOSED, LockState.UNLOCKED),
                Arguments.of(PhysicalAction.INSERT_CASSETTE, DoorState.OPEN, LockState.LOCKED));
    }

    @Test
    void rejectsNullPhysicalAction() {
        assertThrows(NullPointerException.class, () -> simulator.simulate(null));
    }

    @Test
    void rejectsMissingHardwareConfigurationDuringUpdate() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () ->
                        simulator.replaceState(
                                gate(
                                        DoorState.CLOSED,
                                        LockState.LOCKED,
                                        ExtensionBoardState.ONLINE)));
    }

    private void state(HardwareSnapshot snapshot) {
        doReturn(snapshot).when(simulator).snapshot();
    }

    private static HardwareSnapshot gate(
            DoorState door, LockState lock, ExtensionBoardState board) {
        return new HardwareSnapshot(
                AcceptorState.READY, CashboxState.PRESENT, door, lock, board, PrinterState.READY);
    }

    private static void assertConflict(Runnable action) {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, action::run);

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
