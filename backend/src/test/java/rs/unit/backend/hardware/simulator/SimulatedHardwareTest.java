package rs.unit.backend.hardware.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;

class SimulatedHardwareTest {

    @Test
    void replacesStateWithoutChangingPreviouslyReadSnapshot() {
        SimulatedHardware simulator = new SimulatedHardware();
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
    void rejectsNullWithoutChangingState() {
        SimulatedHardware simulator = new SimulatedHardware();
        HardwareSnapshot before = simulator.snapshot();

        assertThrows(NullPointerException.class, () -> simulator.replaceState(null));

        assertEquals(before, simulator.snapshot());
    }
}
