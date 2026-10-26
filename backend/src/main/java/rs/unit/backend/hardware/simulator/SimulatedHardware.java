package rs.unit.backend.hardware.simulator;

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

    private final HardwareSnapshot initialState =
            new HardwareSnapshot(
                    AcceptorState.READY,
                    CashboxState.PRESENT,
                    DoorState.CLOSED,
                    LockState.LOCKED,
                    ExtensionBoardState.ONLINE,
                    PrinterState.READY);

    @Override
    public HardwareSnapshot snapshot() {
        return initialState;
    }
}
