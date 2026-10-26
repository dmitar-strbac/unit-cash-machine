package rs.unit.backend.diagnostics.domain;

import java.util.Objects;
import java.util.stream.Stream;
import rs.unit.backend.diagnostics.model.MachineAlert;
import rs.unit.backend.diagnostics.model.MachineAlert.Component;
import rs.unit.backend.diagnostics.model.MachineAlert.Severity;
import rs.unit.backend.diagnostics.model.MachineDiagnostics;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;

public final class MachineDiagnosticsPolicy {

    private MachineDiagnosticsPolicy() {}

    public static MachineDiagnostics evaluate(HardwareSnapshot hardware) {
        return new MachineDiagnostics(
                Stream.of(
                                acceptorAlert(hardware.acceptor()),
                                cashboxAlert(hardware.cashbox()),
                                doorAlert(hardware.door()),
                                lockAlert(hardware.lock()),
                                extensionBoardAlert(hardware.extensionBoard()),
                                printerAlert(hardware.printer()))
                        .filter(Objects::nonNull)
                        .toList());
    }

    private static MachineAlert acceptorAlert(AcceptorState state) {
        return switch (state) {
            case READY, ACCEPTING -> null;
            case JAMMED ->
                    new MachineAlert(
                            "ACCEPTOR_JAMMED",
                            Component.ACCEPTOR,
                            Severity.ERROR,
                            "Banknote jam detected.",
                            true);
            case OFFLINE ->
                    new MachineAlert(
                            "ACCEPTOR_OFFLINE",
                            Component.ACCEPTOR,
                            Severity.ERROR,
                            "Banknote acceptor is not operational.",
                            true);
            case UNKNOWN ->
                    new MachineAlert(
                            "ACCEPTOR_UNKNOWN",
                            Component.ACCEPTOR,
                            Severity.ERROR,
                            "Banknote acceptor state is unknown.",
                            true);
        };
    }

    private static MachineAlert cashboxAlert(CashboxState state) {
        return switch (state) {
            case PRESENT -> null;
            case FULL ->
                    new MachineAlert(
                            "CASHBOX_FULL",
                            Component.CASHBOX,
                            Severity.WARNING,
                            "Cashbox is full.",
                            true);
            case ABSENT ->
                    new MachineAlert(
                            "CASHBOX_ABSENT",
                            Component.CASHBOX,
                            Severity.WARNING,
                            "Cashbox is not installed.",
                            true);
            case UNKNOWN ->
                    new MachineAlert(
                            "CASHBOX_UNKNOWN",
                            Component.CASHBOX,
                            Severity.ERROR,
                            "Cashbox state is unknown.",
                            true);
        };
    }

    private static MachineAlert doorAlert(DoorState state) {
        return switch (state) {
            case CLOSED -> null;
            case OPEN ->
                    new MachineAlert(
                            "DOOR_OPEN", Component.DOOR, Severity.WARNING, "Door is open.", true);
            case UNKNOWN ->
                    new MachineAlert(
                            "DOOR_UNKNOWN",
                            Component.DOOR,
                            Severity.ERROR,
                            "Door state is unknown.",
                            true);
        };
    }

    private static MachineAlert lockAlert(LockState state) {
        return switch (state) {
            case LOCKED -> null;
            case UNLOCKED ->
                    new MachineAlert(
                            "LOCK_UNLOCKED",
                            Component.LOCK,
                            Severity.WARNING,
                            "Door is not locked.",
                            true);
            case FAULT ->
                    new MachineAlert(
                            "LOCK_FAULT",
                            Component.LOCK,
                            Severity.ERROR,
                            "Lock malfunction detected.",
                            true);
            case UNKNOWN ->
                    new MachineAlert(
                            "LOCK_UNKNOWN",
                            Component.LOCK,
                            Severity.ERROR,
                            "Lock state is unknown.",
                            true);
        };
    }

    private static MachineAlert extensionBoardAlert(ExtensionBoardState state) {
        return switch (state) {
            case ONLINE -> null;
            case OFFLINE ->
                    new MachineAlert(
                            "EXTENSION_BOARD_OFFLINE",
                            Component.EXTENSION_BOARD,
                            Severity.ERROR,
                            "Extension board is not operational.",
                            true);
            case UNKNOWN ->
                    new MachineAlert(
                            "EXTENSION_BOARD_UNKNOWN",
                            Component.EXTENSION_BOARD,
                            Severity.ERROR,
                            "Extension board state is unknown.",
                            true);
        };
    }

    private static MachineAlert printerAlert(PrinterState state) {
        return switch (state) {
            case READY -> null;
            case OUT_OF_PAPER ->
                    new MachineAlert(
                            "PRINTER_OUT_OF_PAPER",
                            Component.PRINTER,
                            Severity.WARNING,
                            "Printer is out of paper.",
                            false);
            case OFFLINE ->
                    new MachineAlert(
                            "PRINTER_OFFLINE",
                            Component.PRINTER,
                            Severity.WARNING,
                            "Not operational",
                            false);
            case FAULT ->
                    new MachineAlert(
                            "PRINTER_FAULT",
                            Component.PRINTER,
                            Severity.WARNING,
                            "Printer malfunction detected.",
                            false);
            case UNKNOWN ->
                    new MachineAlert(
                            "PRINTER_UNKNOWN",
                            Component.PRINTER,
                            Severity.WARNING,
                            "Printer state is unknown.",
                            false);
        };
    }
}
