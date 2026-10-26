package rs.unit.backend.machine.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;

class MachineReadinessPolicyTest {

    @Test
    void healthyHardwareIsSecureAndReady() {
        HardwareSnapshot hardware = readyHardware();

        assertTrue(MachineReadinessPolicy.isSecure(hardware));
        assertTrue(MachineReadinessPolicy.isDepositHardwareReady(hardware));
        assertTrue(MachineReadinessPolicy.isPrintingAvailable(hardware));
    }

    @ParameterizedTest
    @EnumSource(value = DoorState.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
    void unsafeDoorBlocksDeposit(DoorState door) {
        HardwareSnapshot initial = readyHardware();
        HardwareSnapshot hardware =
                new HardwareSnapshot(
                        initial.acceptor(),
                        initial.cashbox(),
                        door,
                        initial.lock(),
                        initial.extensionBoard(),
                        initial.printer());

        assertFalse(MachineReadinessPolicy.isSecure(hardware));
        assertFalse(MachineReadinessPolicy.isDepositHardwareReady(hardware));
    }

    @ParameterizedTest
    @EnumSource(value = LockState.class, names = "LOCKED", mode = EnumSource.Mode.EXCLUDE)
    void unsecuredLockBlocksDeposit(LockState lock) {
        HardwareSnapshot initial = readyHardware();
        HardwareSnapshot hardware =
                new HardwareSnapshot(
                        initial.acceptor(),
                        initial.cashbox(),
                        initial.door(),
                        lock,
                        initial.extensionBoard(),
                        initial.printer());

        assertFalse(MachineReadinessPolicy.isSecure(hardware));
        assertFalse(MachineReadinessPolicy.isDepositHardwareReady(hardware));
    }

    @ParameterizedTest
    @EnumSource(value = AcceptorState.class, names = "READY", mode = EnumSource.Mode.EXCLUDE)
    void unavailableOrBusyAcceptorBlocksNewDeposit(AcceptorState acceptor) {
        HardwareSnapshot initial = readyHardware();
        HardwareSnapshot hardware =
                new HardwareSnapshot(
                        acceptor,
                        initial.cashbox(),
                        initial.door(),
                        initial.lock(),
                        initial.extensionBoard(),
                        initial.printer());

        assertFalse(MachineReadinessPolicy.isDepositHardwareReady(hardware));
    }

    @ParameterizedTest
    @EnumSource(value = CashboxState.class, names = "PRESENT", mode = EnumSource.Mode.EXCLUDE)
    void unavailableCashboxBlocksDeposit(CashboxState cashbox) {
        HardwareSnapshot initial = readyHardware();
        HardwareSnapshot hardware =
                new HardwareSnapshot(
                        initial.acceptor(),
                        cashbox,
                        initial.door(),
                        initial.lock(),
                        initial.extensionBoard(),
                        initial.printer());

        assertFalse(MachineReadinessPolicy.isDepositHardwareReady(hardware));
    }

    @ParameterizedTest
    @EnumSource(value = ExtensionBoardState.class, names = "ONLINE", mode = EnumSource.Mode.EXCLUDE)
    void unavailableExtensionBoardBlocksDeposit(ExtensionBoardState board) {
        HardwareSnapshot initial = readyHardware();
        HardwareSnapshot hardware =
                new HardwareSnapshot(
                        initial.acceptor(),
                        initial.cashbox(),
                        initial.door(),
                        initial.lock(),
                        board,
                        initial.printer());

        assertFalse(MachineReadinessPolicy.isDepositHardwareReady(hardware));
    }

    @ParameterizedTest
    @EnumSource(value = PrinterState.class, names = "READY", mode = EnumSource.Mode.EXCLUDE)
    void printerProblemBlocksPrintingButAllowsDeposit(PrinterState printer) {
        HardwareSnapshot initial = readyHardware();
        HardwareSnapshot hardware =
                new HardwareSnapshot(
                        initial.acceptor(),
                        initial.cashbox(),
                        initial.door(),
                        initial.lock(),
                        initial.extensionBoard(),
                        printer);

        assertTrue(MachineReadinessPolicy.isSecure(hardware));
        assertTrue(MachineReadinessPolicy.isDepositHardwareReady(hardware));
        assertFalse(MachineReadinessPolicy.isPrintingAvailable(hardware));
    }

    private HardwareSnapshot readyHardware() {
        return new HardwareSnapshot(
                AcceptorState.READY,
                CashboxState.PRESENT,
                DoorState.CLOSED,
                LockState.LOCKED,
                ExtensionBoardState.ONLINE,
                PrinterState.READY);
    }
}
