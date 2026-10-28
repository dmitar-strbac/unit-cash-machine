package rs.unit.backend.hardware.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;

@SpringBootTest(properties = "unit.collection.monitor.enabled=false")
@Transactional
class SimulatedHardwareTest {

    @Autowired private SimulatedHardware simulator;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        simulator.replaceState(
                new HardwareSnapshot(
                        AcceptorState.READY,
                        CashboxState.PRESENT,
                        DoorState.CLOSED,
                        LockState.LOCKED,
                        ExtensionBoardState.ONLINE,
                        PrinterState.READY));
    }

    @Test
    void replacesStateWithoutChangingPreviouslyReadSnapshot() {
        HardwareSnapshot before = simulator.snapshot();

        HardwareSnapshot replacement =
                new HardwareSnapshot(
                        before.acceptor(),
                        before.cashbox(),
                        before.door(),
                        before.lock(),
                        before.extensionBoard(),
                        PrinterState.OFFLINE);

        HardwareSnapshot result = simulator.replaceState(replacement);

        assertEquals(replacement, result);
        assertEquals(replacement, simulator.snapshot());
        assertEquals(PrinterState.READY, before.printer());
    }

    @Test
    void anotherSimulatorInstanceReadsPersistedState() {
        simulator.unlockDoor();

        SimulatedHardware anotherInstance = new SimulatedHardware(jdbc);

        assertEquals(LockState.UNLOCKED, anotherInstance.snapshot().lock());
    }

    @Test
    void rejectsNullWithoutChangingState() {
        HardwareSnapshot before = simulator.snapshot();

        assertThrows(NullPointerException.class, () -> simulator.replaceState(null));

        assertEquals(before, simulator.snapshot());
    }
}
