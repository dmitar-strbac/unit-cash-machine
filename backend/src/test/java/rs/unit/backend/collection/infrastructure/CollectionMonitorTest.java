package rs.unit.backend.collection.infrastructure;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.unit.backend.collection.application.CollectionService;

@ExtendWith(MockitoExtension.class)
class CollectionMonitorTest {

    @Mock private CollectionService service;

    @Test
    void delegatesScheduledSynchronizationToApplicationService() {
        CollectionMonitor monitor = new CollectionMonitor(service);

        monitor.synchronize();

        verify(service).synchronize();
    }
}
