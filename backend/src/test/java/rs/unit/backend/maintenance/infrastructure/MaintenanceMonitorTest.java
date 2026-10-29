package rs.unit.backend.maintenance.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import rs.unit.backend.maintenance.application.MaintenanceService;

class MaintenanceMonitorTest {

    @Test
    void scheduledInvocationSynchronizesMaintenance() {
        MaintenanceService service = mock(MaintenanceService.class);
        MaintenanceMonitor monitor = new MaintenanceMonitor(service);

        monitor.synchronize();

        verify(service).synchronize();
        verifyNoMoreInteractions(service);
    }
}
