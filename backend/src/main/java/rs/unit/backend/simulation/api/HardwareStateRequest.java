package rs.unit.backend.simulation.api;

import jakarta.validation.constraints.NotNull;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;

public record HardwareStateRequest(
        @NotNull AcceptorState acceptor,
        @NotNull CashboxState cashbox,
        @NotNull DoorState door,
        @NotNull LockState lock,
        @NotNull ExtensionBoardState extensionBoard,
        @NotNull PrinterState printer) {

    public HardwareSnapshot toSnapshot() {
        return new HardwareSnapshot(acceptor, cashbox, door, lock, extensionBoard, printer);
    }
}
