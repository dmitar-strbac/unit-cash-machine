package rs.unit.backend.machine.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import rs.unit.backend.collection.domain.CollectionStep;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository.StoredCollection;
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

class MachineStatusCollectionAlertTest {

    @ParameterizedTest
    @CsvSource({
        "LOCK_TIMEOUT, WARNING, CIT_IN_PROGRESS, Close and lock the door to complete cash collection.",
        "APPLICATION_RESTARTED, ERROR, CIT_INCOMPLETE, Cash collection requires recovery.",
        "SESSION_EXPIRED, ERROR, CIT_INCOMPLETE, Cash collection requires recovery."
    })
    void incompleteCollectionReportsAppropriateAlert(
            String errorCode,
            String expectedSeverity,
            String expectedCode,
            String expectedMessage) {
        MachineStatusService service =
                serviceWith(collection(CollectionStep.INCOMPLETE, errorCode));

        var status = service.getStatus();

        var alert =
                status.alerts().stream()
                        .filter(candidate -> candidate.code().equals(expectedCode))
                        .findFirst()
                        .orElseThrow();

        assertEquals(expectedSeverity, alert.severity().name());
        assertEquals(expectedMessage, alert.message());
        assertTrue(alert.blocksDeposit());

        assertEquals(
                1L,
                status.alerts().stream()
                        .filter(candidate -> candidate.code().startsWith("CIT_"))
                        .count());
    }

    @ParameterizedTest
    @CsvSource({"OPEN_DOOR", "REMOVE_CASSETTE", "INSERT_CASSETTE", "CLOSE_DOOR", "SECURING"})
    void activeCollectionReportsProgressWarning(CollectionStep step) {
        MachineStatusService service = serviceWith(collection(step, null));

        var alert =
                service.getStatus().alerts().stream()
                        .filter(candidate -> candidate.code().equals("CIT_IN_PROGRESS"))
                        .findFirst()
                        .orElseThrow();

        assertEquals("WARNING", alert.severity().name());
        assertEquals("Cash collection is in progress.", alert.message());
        assertTrue(alert.blocksDeposit());
    }

    private MachineStatusService serviceWith(StoredCollection collection) {
        MachineConfigurationRepository configurations = mock(MachineConfigurationRepository.class);
        HardwareStatusPort hardware = mock(HardwareStatusPort.class);
        CollectionRepository collections = mock(CollectionRepository.class);

        MachineRuntimeGuard runtime = mock(MachineRuntimeGuard.class);

        when(runtime.isRunning()).thenReturn(true);
        when(runtime.view())
                .thenReturn(
                        new MachineRuntimeView(MachineRunState.RUNNING, 0, null, null, null, null));

        when(configurations.findById((short) 1))
                .thenReturn(Optional.of(mock(MachineConfiguration.class)));
        when(collections.findUnfinished()).thenReturn(Optional.of(collection));
        when(hardware.snapshot())
                .thenReturn(
                        new HardwareSnapshot(
                                AcceptorState.READY,
                                CashboxState.PRESENT,
                                DoorState.CLOSED,
                                LockState.UNLOCKED,
                                ExtensionBoardState.ONLINE,
                                PrinterState.READY));

        return new MachineStatusService(configurations, hardware, collections, runtime);
    }

    private StoredCollection collection(CollectionStep step, String errorCode) {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");

        return new StoredCollection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                now.plusSeconds(900),
                step,
                now,
                now,
                now,
                step == CollectionStep.SECURING ? now.plusSeconds(5) : null,
                null,
                null,
                errorCode);
    }
}
