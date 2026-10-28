package rs.unit.backend.machine.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository;
import rs.unit.backend.hardware.port.HardwareStatusPort;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfigurationRepository;

class MachineStatusServiceTest {

    @Test
    void missingConfigurationFailsBeforeReadingHardware() {
        MachineConfigurationRepository repository = mock(MachineConfigurationRepository.class);
        HardwareStatusPort hardware = mock(HardwareStatusPort.class);
        CollectionRepository collectionRepository = mock(CollectionRepository.class);

        when(repository.findById((short) 1)).thenReturn(Optional.empty());

        MachineStatusService service =
                new MachineStatusService(repository, hardware, collectionRepository);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, service::getStatus);

        assertEquals("Machine configuration is missing.", exception.getMessage());
        verifyNoInteractions(hardware);
    }
}
