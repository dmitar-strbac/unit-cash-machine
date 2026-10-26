package rs.unit.backend.hardware.simulator;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.port.HardwareStatusPort;

@Component
public class SimulatedHardware implements HardwareStatusPort {

    private final AtomicReference<HardwareSnapshot> state =
            new AtomicReference<>(
                    new HardwareSnapshot(
                            AcceptorState.READY,
                            CashboxState.PRESENT,
                            DoorState.CLOSED,
                            LockState.LOCKED,
                            ExtensionBoardState.ONLINE,
                            PrinterState.READY));

    @Override
    public HardwareSnapshot snapshot() {
        return state.get();
    }

    public HardwareSnapshot replaceState(HardwareSnapshot newState) {
        Objects.requireNonNull(newState, "newState");
        state.set(newState);
        return newState;
    }
}
