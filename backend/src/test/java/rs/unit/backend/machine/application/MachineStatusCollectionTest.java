package rs.unit.backend.machine.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import rs.unit.backend.collection.domain.CollectionStep;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository.StoredCollection;
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

class MachineStatusCollectionTest {

    @ParameterizedTest
    @CsvSource({
        "REMOVE_CASSETTE, CIT_IN_PROGRESS, WARNING, AMBER, Cash collection is in progress.",
        "INCOMPLETE, CIT_INCOMPLETE, ERROR, RED, Cash collection requires recovery."
    })
    void reportsCollectionAlertEvenWhenHardwareIsReady(
            CollectionStep step,
            String expectedCode,
            Severity expectedSeverity,
            Indicator expectedIndicator,
            String expectedMessage) {
        MachineConfigurationRepository configurations = mock(MachineConfigurationRepository.class);

        HardwareStatusPort hardware = mock(HardwareStatusPort.class);

        CollectionRepository collections = mock(CollectionRepository.class);

        MachineConfiguration configuration = mock(MachineConfiguration.class);
        StoredCollection collection = mock(StoredCollection.class);

        when(configurations.findById((short) 1)).thenReturn(Optional.of(configuration));

        when(configuration.getMachineCode()).thenReturn("UNIT-SIM-001");
        when(configuration.getDisplayName()).thenReturn("Unit Cash Machine");
        when(configuration.getLocation()).thenReturn("Test location");
        when(configuration.getCurrency()).thenReturn("EUR");
        when(configuration.getTimeZone()).thenReturn("Europe/Belgrade");

        when(hardware.snapshot())
                .thenReturn(
                        new HardwareSnapshot(
                                AcceptorState.READY,
                                CashboxState.PRESENT,
                                DoorState.CLOSED,
                                LockState.LOCKED,
                                ExtensionBoardState.ONLINE,
                                PrinterState.READY));

        when(collections.findUnfinished()).thenReturn(Optional.of(collection));
        when(collection.step()).thenReturn(step);

        MachineStatusService service =
                new MachineStatusService(configurations, hardware, collections);

        MachineStatus status = service.getStatus();

        assertEquals(expectedIndicator, status.indicator());
        assertEquals(1, status.alertCount());
        assertEquals(1, status.alerts().size());

        var alert = status.alerts().get(0);

        assertEquals(expectedCode, alert.code());
        assertEquals(Component.LOCK, alert.source());
        assertEquals(expectedSeverity, alert.severity());
        assertEquals(expectedMessage, alert.message());
        assertTrue(alert.blocksDeposit());

        assertTrue(status.secure());
        assertTrue(status.depositHardwareReady());
        assertTrue(status.printingAvailable());
    }
}
