package rs.unit.backend.report.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.hardware.port.HardwareStatusPort;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfiguration;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfigurationRepository;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.printing.application.ReceiptFormatter;
import rs.unit.backend.printing.infrastructure.persistence.ReceiptRepository;
import rs.unit.backend.report.infrastructure.persistence.ReportRepository;
import rs.unit.backend.user.domain.UserRole;

class ReportingServiceTest {

    private final ReportRepository reports = mock(ReportRepository.class);
    private final ReceiptRepository receipts = mock(ReceiptRepository.class);

    private final MachineConfigurationRepository configurations =
            mock(MachineConfigurationRepository.class);

    private final HardwareStatusPort hardware = mock(HardwareStatusPort.class);
    private final ReceiptFormatter formatter = mock(ReceiptFormatter.class);

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    private final MachineRuntimeGuard runtime = mock(MachineRuntimeGuard.class);

    private final ReportingService service =
            new ReportingService(
                    reports,
                    receipts,
                    configurations,
                    hardware,
                    formatter,
                    Clock.fixed(now, ZoneOffset.UTC),
                    runtime);

    @Test
    void missingMachineConfigurationProducesClearFailure() {
        when(configurations.findById((short) 1)).thenReturn(Optional.empty());

        var manager =
                new AuthenticatedUser(
                        UUID.randomUUID(),
                        "Test Manager",
                        UserRole.MANAGEMENT,
                        now.plusSeconds(900));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> service.overview(manager));

        assertEquals("Machine configuration is missing.", exception.getMessage());
    }

    @Test
    void unavailableHashAlgorithmProducesWrappedFailure() {
        var cause = new NoSuchAlgorithmException("Unavailable test provider");

        try (var algorithms = mockStatic(MessageDigest.class)) {
            algorithms.when(() -> MessageDigest.getInstance("SHA-256")).thenThrow(cause);

            IllegalStateException exception =
                    assertThrows(
                            IllegalStateException.class,
                            () -> service.receiptByAccess("a".repeat(64)));

            assertEquals("SHA-256 is unavailable.", exception.getMessage());
            assertSame(cause, exception.getCause());
        }
    }

    @Test
    void finishedDepositRetainsReceiptWhenApplicationCannotPrint() {
        UUID depositId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        var deposit =
                new ReceiptRepository.DepositReceipt(
                        depositId, userId, 17, now, "Test Cashier", 2, 3000);

        var configuration = mock(MachineConfiguration.class);

        when(receipts.findByDepositId(depositId)).thenReturn(Optional.empty());
        when(receipts.depositReceipt(depositId)).thenReturn(Optional.of(deposit));
        when(configurations.findById((short) 1)).thenReturn(Optional.of(configuration));
        when(formatter.deposit(configuration, deposit)).thenReturn("Saved receipt");
        when(runtime.isRunning()).thenReturn(false);

        service.captureDeposit(depositId);

        verify(receipts)
                .create(
                        any(UUID.class),
                        eq("DEPOSIT"),
                        eq(depositId),
                        eq(userId),
                        isNull(),
                        eq(now),
                        eq(3000L),
                        eq("Saved receipt"));

        verify(receipts)
                .recordAttempt(
                        any(UUID.class),
                        any(UUID.class),
                        eq(now),
                        eq("FAILED"),
                        eq("MACHINE_UNAVAILABLE"));

        verifyNoInteractions(hardware);
    }
}
