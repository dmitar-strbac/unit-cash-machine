package rs.unit.backend.machine.domain;

import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;

public final class MachineReadinessPolicy {

    private MachineReadinessPolicy() {}

    public static boolean isSecure(HardwareSnapshot hardware) {
        return hardware.door() == DoorState.CLOSED && hardware.lock() == LockState.LOCKED;
    }

    public static boolean isDepositHardwareReady(HardwareSnapshot hardware) {
        return isSecure(hardware)
                && hardware.acceptor() == AcceptorState.READY
                && hardware.cashbox() == CashboxState.PRESENT
                && hardware.extensionBoard() == ExtensionBoardState.ONLINE;
    }

    public static boolean isPrintingAvailable(HardwareSnapshot hardware) {
        return hardware.printer() == PrinterState.READY;
    }
}
