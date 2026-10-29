package rs.unit.backend.diagnostics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import rs.unit.backend.diagnostics.model.MachineAlert;
import rs.unit.backend.diagnostics.model.MachineAlert.Component;
import rs.unit.backend.diagnostics.model.MachineAlert.Severity;
import rs.unit.backend.diagnostics.model.MachineDiagnostics;
import rs.unit.backend.diagnostics.model.MachineDiagnostics.Indicator;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;

class MachineDiagnosticsPolicyTest {

    @Test
    void healthyHardwareHasNoAlerts() {
        MachineDiagnostics diagnostics = MachineDiagnosticsPolicy.evaluate(readyHardware());

        assertEquals(Indicator.GREEN, diagnostics.indicator());
        assertEquals(0, diagnostics.alertCount());
        assertTrue(diagnostics.alerts().isEmpty());
    }

    @Test
    void acceptingBanknoteIsNotReportedAsFault() {
        MachineDiagnostics diagnostics =
                MachineDiagnosticsPolicy.evaluate(withState(Component.ACCEPTOR, "ACCEPTING"));

        assertEquals(Indicator.GREEN, diagnostics.indicator());
        assertEquals(0, diagnostics.alertCount());
    }

    @ParameterizedTest
    @CsvSource({
        "ACCEPTOR, JAMMED, ACCEPTOR_JAMMED, ERROR, true",
        "ACCEPTOR, OFFLINE, ACCEPTOR_OFFLINE, ERROR, true",
        "ACCEPTOR, UNKNOWN, ACCEPTOR_UNKNOWN, ERROR, true",
        "CASHBOX, FULL, CASHBOX_FULL, WARNING, true",
        "CASHBOX, ABSENT, CASHBOX_ABSENT, WARNING, true",
        "CASHBOX, UNKNOWN, CASHBOX_UNKNOWN, ERROR, true",
        "DOOR, OPEN, DOOR_OPEN, WARNING, true",
        "DOOR, UNKNOWN, DOOR_UNKNOWN, ERROR, true",
        "LOCK, UNLOCKED, LOCK_UNLOCKED, WARNING, true",
        "LOCK, FAULT, LOCK_FAULT, ERROR, true",
        "LOCK, UNKNOWN, LOCK_UNKNOWN, ERROR, true",
        "EXTENSION_BOARD, OFFLINE, EXTENSION_BOARD_OFFLINE, ERROR, true",
        "EXTENSION_BOARD, UNKNOWN, EXTENSION_BOARD_UNKNOWN, ERROR, true",
        "PRINTER, OUT_OF_PAPER, PRINTER_OUT_OF_PAPER, WARNING, false",
        "PRINTER, OFFLINE, PRINTER_OFFLINE, WARNING, false",
        "PRINTER, FAULT, PRINTER_FAULT, WARNING, false",
        "PRINTER, UNKNOWN, PRINTER_UNKNOWN, WARNING, false"
    })
    void reportsExpectedProblem(
            Component source,
            String state,
            String expectedCode,
            Severity expectedSeverity,
            boolean expectedBlocking) {
        MachineDiagnostics diagnostics =
                MachineDiagnosticsPolicy.evaluate(withState(source, state));

        assertEquals(1, diagnostics.alertCount());

        MachineAlert alert = diagnostics.alerts().getFirst();

        assertEquals(source, alert.source());
        assertEquals(expectedCode, alert.code());
        assertEquals(expectedSeverity, alert.severity());
        assertEquals(expectedBlocking, alert.blocksDeposit());
        assertFalse(alert.message().isBlank());

        Indicator expectedIndicator =
                expectedSeverity == Severity.ERROR ? Indicator.RED : Indicator.AMBER;

        assertEquals(expectedIndicator, diagnostics.indicator());
    }

    @Test
    void errorTakesPriorityOverWarningsAndAllProblemsRemainVisible() {
        HardwareSnapshot hardware =
                new HardwareSnapshot(
                        AcceptorState.READY,
                        CashboxState.FULL,
                        DoorState.CLOSED,
                        LockState.FAULT,
                        ExtensionBoardState.ONLINE,
                        PrinterState.OFFLINE);

        MachineDiagnostics diagnostics = MachineDiagnosticsPolicy.evaluate(hardware);

        assertEquals(Indicator.RED, diagnostics.indicator());
        assertEquals(3, diagnostics.alertCount());
        assertEquals(
                java.util.List.of("CASHBOX_FULL", "LOCK_FAULT", "PRINTER_OFFLINE"),
                diagnostics.alerts().stream().map(MachineAlert::code).toList());
    }

    @Test
    void resolvingProblemRemovesAlertFromNextEvaluation() {
        MachineDiagnostics broken =
                MachineDiagnosticsPolicy.evaluate(withState(Component.PRINTER, "OFFLINE"));
        MachineDiagnostics recovered = MachineDiagnosticsPolicy.evaluate(readyHardware());

        assertEquals(1, broken.alertCount());
        assertEquals(0, recovered.alertCount());
        assertEquals(Indicator.GREEN, recovered.indicator());
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

    private HardwareSnapshot withState(Component component, String value) {
        HardwareSnapshot initial = readyHardware();

        return switch (component) {
            case ACCEPTOR ->
                    new HardwareSnapshot(
                            AcceptorState.valueOf(value),
                            initial.cashbox(),
                            initial.door(),
                            initial.lock(),
                            initial.extensionBoard(),
                            initial.printer());
            case CASHBOX ->
                    new HardwareSnapshot(
                            initial.acceptor(),
                            CashboxState.valueOf(value),
                            initial.door(),
                            initial.lock(),
                            initial.extensionBoard(),
                            initial.printer());
            case DOOR ->
                    new HardwareSnapshot(
                            initial.acceptor(),
                            initial.cashbox(),
                            DoorState.valueOf(value),
                            initial.lock(),
                            initial.extensionBoard(),
                            initial.printer());
            case LOCK ->
                    new HardwareSnapshot(
                            initial.acceptor(),
                            initial.cashbox(),
                            initial.door(),
                            LockState.valueOf(value),
                            initial.extensionBoard(),
                            initial.printer());
            case EXTENSION_BOARD ->
                    new HardwareSnapshot(
                            initial.acceptor(),
                            initial.cashbox(),
                            initial.door(),
                            initial.lock(),
                            ExtensionBoardState.valueOf(value),
                            initial.printer());
            case PRINTER ->
                    new HardwareSnapshot(
                            initial.acceptor(),
                            initial.cashbox(),
                            initial.door(),
                            initial.lock(),
                            initial.extensionBoard(),
                            PrinterState.valueOf(value));

            case SYSTEM ->
                    throw new IllegalArgumentException(
                            "SYSTEM is not a physical hardware component");
        };
    }
}
