package rs.unit.backend.machine.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository;
import rs.unit.backend.diagnostics.model.MachineAlert.Component;
import rs.unit.backend.diagnostics.model.MachineAlert.Severity;
import rs.unit.backend.diagnostics.model.MachineDiagnostics.Indicator;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.port.HardwareStatusPort;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfiguration;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfigurationRepository;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.maintenance.application.MachineRuntimeView;
import rs.unit.backend.maintenance.domain.MachineRunState;

class MachineStatusRuntimeTest {

    @ParameterizedTest
    @EnumSource(
            value = MachineRunState.class,
            names = {"RESETTING", "REBOOTING", "SHUT_DOWN", "APPLICATION_STOPPED"})
    void unavailableApplicationBlocksOperationsAndAddsSystemWarning(MachineRunState state) {
        var configurations = mock(MachineConfigurationRepository.class);
        var hardware = mock(HardwareStatusPort.class);
        var collections = mock(CollectionRepository.class);
        var runtime = mock(MachineRuntimeGuard.class);

        var runtimeView = new MachineRuntimeView(state, 1, null, null, null, null);

        when(configurations.findById((short) 1))
                .thenReturn(Optional.of(mock(MachineConfiguration.class)));
        when(collections.findUnfinished()).thenReturn(Optional.empty());
        when(runtime.view()).thenReturn(runtimeView);
        when(runtime.isRunning()).thenReturn(false);
        when(hardware.snapshot())
                .thenReturn(
                        new HardwareSnapshot(
                                AcceptorState.READY,
                                CashboxState.PRESENT,
                                DoorState.CLOSED,
                                LockState.LOCKED,
                                ExtensionBoardState.ONLINE,
                                PrinterState.READY));

        var service = new MachineStatusService(configurations, hardware, collections, runtime);

        var status = service.getStatus();

        assertTrue(status.secure());
        assertFalse(status.depositHardwareReady());
        assertFalse(status.printingAvailable());
        assertEquals(Indicator.AMBER, status.indicator());
        assertEquals(1, status.alertCount());
        assertSame(runtimeView, status.runtime());

        var alert = status.alerts().getFirst();

        assertEquals("MACHINE_" + state.name(), alert.code());
        assertEquals(Component.SYSTEM, alert.source());
        assertEquals(Severity.WARNING, alert.severity());
        assertEquals("Machine application state: " + state.name(), alert.message());
        assertTrue(alert.blocksDeposit());
    }
}
