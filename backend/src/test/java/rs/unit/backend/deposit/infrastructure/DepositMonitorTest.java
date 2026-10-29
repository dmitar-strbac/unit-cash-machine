package rs.unit.backend.deposit.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import rs.unit.backend.deposit.application.DepositService;

class DepositMonitorTest {

    @Test
    void scheduledInvocationReconcilesDeposits() {
        DepositService deposits = mock(DepositService.class);
        DepositMonitor monitor = new DepositMonitor(deposits);

        monitor.reconcile();

        verify(deposits).reconcile();
        verifyNoMoreInteractions(deposits);
    }
}
