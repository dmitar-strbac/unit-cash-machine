package rs.unit.backend.maintenance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.simulator.SimulatedHardware;
import rs.unit.backend.maintenance.domain.MachineRunState;
import rs.unit.backend.maintenance.domain.MaintenanceAction;
import rs.unit.backend.maintenance.infrastructure.persistence.MaintenanceRepository;
import rs.unit.backend.support.MachineTestState;
import rs.unit.backend.user.domain.UserRole;

@SpringBootTest(
        properties = {
            "unit.collection.monitor.enabled=false",
            "unit.deposit.monitor.enabled=false",
            "unit.maintenance.monitor.enabled=false"
        })
@Transactional
class MaintenanceIntegrationTest {

    @Autowired private MaintenanceService maintenance;
    @Autowired private MachineRuntimeGuard runtime;
    @Autowired private ComponentService components;
    @Autowired private DepositService deposits;
    @Autowired private SimulatedHardware hardware;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MaintenanceRepository repository;

    @MockitoBean private Clock clock;

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    private AuthenticatedUser manager;
    private AuthenticatedUser maintainer;
    private AuthenticatedUser cashier;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
        MachineTestState.reset(jdbc);

        manager = user(UserRole.MANAGEMENT);
        maintainer = user(UserRole.MAINTENANCE);
        cashier = user(UserRole.CASHIER);
    }

    @Test
    void resetPreservesSessionAndPhysicalHardwareFaults() {
        var faulty =
                new HardwareSnapshot(
                        AcceptorState.JAMMED,
                        CashboxState.ABSENT,
                        DoorState.OPEN,
                        LockState.UNLOCKED,
                        ExtensionBoardState.OFFLINE,
                        PrinterState.OUT_OF_PAPER);

        hardware.replaceState(faulty);

        UUID id = UUID.randomUUID();

        var accepted = maintenance.execute(manager, id, MaintenanceAction.RESET);

        assertEquals("PENDING", accepted.status());
        assertEquals(MachineRunState.RESETTING, runtime.view().state());
        assertTrue(runtime.isSessionValid(manager));

        when(clock.instant()).thenReturn(now.plusSeconds(2));
        maintenance.synchronize();

        assertEquals(MachineRunState.RUNNING, runtime.view().state());
        assertTrue(runtime.isSessionValid(manager));
        assertEquals(faulty, hardware.snapshot());
        assertNotNull(runtime.view().notice());

        var resetComponents =
                components.components(maintainer).stream()
                        .filter(component -> !component.derived())
                        .toList();

        assertEquals(6, resetComponents.size());
        assertTrue(resetComponents.stream().allMatch(component -> component.resetCount() == 1));

        maintenance.synchronize();

        assertTrue(
                components.components(maintainer).stream()
                        .filter(component -> !component.derived())
                        .allMatch(component -> component.resetCount() == 1));

        when(clock.instant()).thenReturn(now.plusSeconds(12));

        assertEquals(null, runtime.view().notice());
    }

    @Test
    void rebootInvalidatesExistingSessionsAndCompletesAtDeadline() {
        UUID id = UUID.randomUUID();

        maintenance.execute(manager, id, MaintenanceAction.REBOOT);

        assertEquals(MachineRunState.REBOOTING, runtime.view().state());
        assertFalse(runtime.isSessionValid(manager));
        assertFalse(runtime.isSessionValid(maintainer));

        when(clock.instant()).thenReturn(now.plusMillis(2999));
        maintenance.synchronize();

        assertEquals(MachineRunState.REBOOTING, runtime.view().state());

        when(clock.instant()).thenReturn(now.plusSeconds(3));
        maintenance.synchronize();

        assertEquals(MachineRunState.RUNNING, runtime.view().state());
        assertFalse(runtime.isSessionValid(manager));

        long generation = runtime.generationForLogin();

        var newSession =
                new AuthenticatedUser(
                        manager.id(),
                        manager.displayName(),
                        manager.role(),
                        now.plusSeconds(900),
                        UUID.randomUUID(),
                        generation);

        assertTrue(runtime.isSessionValid(newSession));
    }

    @Test
    void repeatedResetDoesNotCreateAnotherCommand() {
        UUID id = UUID.randomUUID();

        var first = maintenance.execute(manager, id, MaintenanceAction.RESET);
        var second = maintenance.execute(manager, id, MaintenanceAction.RESET);

        assertEquals(first.id(), second.id());

        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM cash_machine.maintenance_command", Long.class));

        assertStatus(
                HttpStatus.CONFLICT,
                () -> maintenance.execute(manager, id, MaintenanceAction.REBOOT));

        assertStatus(
                HttpStatus.SERVICE_UNAVAILABLE,
                () -> maintenance.execute(manager, UUID.randomUUID(), MaintenanceAction.RESET));
    }

    @Test
    void managerCannotShutDownOrExitApplication() {
        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> maintenance.execute(manager, UUID.randomUUID(), MaintenanceAction.SHUT_DOWN));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () ->
                        maintenance.execute(
                                manager, UUID.randomUUID(), MaintenanceAction.EXIT_APPLICATION));

        assertEquals(MachineRunState.RUNNING, runtime.view().state());
    }

    @Test
    void activeDepositBlocksMaintenance() {
        deposits.start(cashier);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> maintenance.execute(maintainer, UUID.randomUUID(), MaintenanceAction.REBOOT));

        assertEquals(MachineRunState.RUNNING, runtime.view().state());
    }

    @Test
    void cashierCannotExecuteMaintenanceCommands() {
        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> maintenance.execute(cashier, UUID.randomUUID(), MaintenanceAction.RESET));
    }

    @Test
    void shutdownRequiresPowerOnBeforeNormalOperation() {
        maintenance.execute(maintainer, UUID.randomUUID(), MaintenanceAction.SHUT_DOWN);

        assertEquals(MachineRunState.SHUT_DOWN, runtime.view().state());
        assertFalse(runtime.isSessionValid(maintainer));

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, runtime::generationForLogin);

        UUID startId = UUID.randomUUID();

        maintenance.resumeSimulator(startId, MaintenanceAction.POWER_ON);
        maintenance.resumeSimulator(startId, MaintenanceAction.POWER_ON);

        when(clock.instant()).thenReturn(now.plusSeconds(3));
        maintenance.synchronize();

        assertEquals(MachineRunState.RUNNING, runtime.view().state());
        assertFalse(runtime.isSessionValid(maintainer));
    }

    @Test
    void exitRequiresApplicationStartInsteadOfPowerOn() {
        maintenance.execute(maintainer, UUID.randomUUID(), MaintenanceAction.EXIT_APPLICATION);

        assertEquals(MachineRunState.APPLICATION_STOPPED, runtime.view().state());

        assertStatus(
                HttpStatus.CONFLICT,
                () -> maintenance.resumeSimulator(UUID.randomUUID(), MaintenanceAction.POWER_ON));

        maintenance.resumeSimulator(UUID.randomUUID(), MaintenanceAction.START_APPLICATION);

        when(clock.instant()).thenReturn(now.plusSeconds(3));
        maintenance.synchronize();

        assertEquals(MachineRunState.RUNNING, runtime.view().state());
    }

    @Test
    void componentValuesAreDerivedFromCurrentHardware() {
        var list = components.components(maintainer);

        assertEquals(9, list.size());

        assertEquals(
                "true",
                list.stream()
                        .filter(component -> component.id().equals("MACHINE_SECURE"))
                        .findFirst()
                        .orElseThrow()
                        .state());

        assertStatus(HttpStatus.FORBIDDEN, () -> components.components(manager));
    }

    @ParameterizedTest
    @CsvSource({"0, 3000", "-1, 3000", "1500, 0", "1500, -1"})
    void maintenanceDurationsMustBePositive(long resetMillis, long rebootMillis) {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new MaintenanceService(
                                        repository, runtime, clock, resetMillis, rebootMillis));

        assertEquals("Maintenance durations must be positive.", exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = MaintenanceAction.class,
            names = {"POWER_ON", "START_APPLICATION"})
    void simulatorActionsCannotBeExecutedAsOperatorCommands(MaintenanceAction action) {
        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> maintenance.execute(maintainer, UUID.randomUUID(), action));

        assertEquals(MachineRunState.RUNNING, runtime.view().state());
    }

    @ParameterizedTest
    @EnumSource(
            value = MaintenanceAction.class,
            names = {"RESET", "REBOOT", "SHUT_DOWN", "EXIT_APPLICATION"})
    void simulatorRejectsActionsOtherThanPowerOnAndApplicationStart(MaintenanceAction action) {
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> maintenance.resumeSimulator(UUID.randomUUID(), action));

        assertEquals(MachineRunState.RUNNING, runtime.view().state());
    }

    @Test
    void expiredOperatorCannotExecuteCommands() {
        var expired =
                new AuthenticatedUser(
                        manager.id(),
                        manager.displayName(),
                        manager.role(),
                        now,
                        manager.loginId(),
                        manager.applicationGeneration());

        assertStatus(
                HttpStatus.UNAUTHORIZED,
                () -> maintenance.execute(expired, UUID.randomUUID(), MaintenanceAction.RESET));

        assertEquals(MachineRunState.RUNNING, runtime.view().state());
    }

    @Test
    void anotherOperatorCannotReuseCommandIdentifier() {
        UUID id = UUID.randomUUID();
        maintenance.execute(manager, id, MaintenanceAction.RESET);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> maintenance.execute(maintainer, id, MaintenanceAction.RESET));
    }

    @Test
    void operatorCanReadExistingCommandDuringReset() {
        UUID id = UUID.randomUUID();
        var expected = maintenance.execute(manager, id, MaintenanceAction.RESET);

        var actual = maintenance.command(manager, id);

        assertEquals(expected, actual);
        assertEquals("PENDING", actual.status());
    }

    @Test
    void missingCommandReturnsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> maintenance.command(manager, UUID.randomUUID()));
    }

    @Test
    void restartedSessionCannotReadCommands() {
        UUID id = UUID.randomUUID();
        maintenance.execute(manager, id, MaintenanceAction.REBOOT);

        assertStatus(HttpStatus.UNAUTHORIZED, () -> maintenance.command(manager, id));
    }

    @Test
    void runtimeRejectsSessionFromPreviousGeneration() {
        maintenance.execute(manager, UUID.randomUUID(), MaintenanceAction.REBOOT);

        assertStatus(HttpStatus.UNAUTHORIZED, () -> runtime.requireOperational(manager));
    }

    @Test
    void runtimeRejectsExpiredSessionFromCurrentGeneration() {
        var expired =
                new AuthenticatedUser(
                        manager.id(),
                        manager.displayName(),
                        manager.role(),
                        now,
                        manager.loginId(),
                        runtime.view().generation());

        assertStatus(HttpStatus.UNAUTHORIZED, () -> runtime.requireOperational(expired));
    }

    @Test
    void matchingGenerationDoesNotMakeStoppedApplicationSessionValid() {
        maintenance.execute(maintainer, UUID.randomUUID(), MaintenanceAction.SHUT_DOWN);

        var matchingGeneration =
                new AuthenticatedUser(
                        maintainer.id(),
                        maintainer.displayName(),
                        maintainer.role(),
                        now.plusSeconds(900),
                        UUID.randomUUID(),
                        runtime.view().generation());

        assertFalse(runtime.isSessionValid(matchingGeneration));

        assertStatus(
                HttpStatus.SERVICE_UNAVAILABLE,
                () -> runtime.requireOperational(matchingGeneration));
    }

    @Test
    void expiredMaintainerCannotInspectComponents() {
        var expired =
                new AuthenticatedUser(
                        maintainer.id(),
                        maintainer.displayName(),
                        maintainer.role(),
                        now,
                        maintainer.loginId(),
                        maintainer.applicationGeneration());

        assertStatus(HttpStatus.UNAUTHORIZED, () -> components.components(expired));
    }

    @Test
    void restartedMaintainerCannotInspectComponents() {
        maintenance.execute(manager, UUID.randomUUID(), MaintenanceAction.REBOOT);

        assertStatus(HttpStatus.UNAUTHORIZED, () -> components.components(maintainer));
    }

    @Test
    void missingRuntimeCannotBeUpdatedSilently() {
        jdbc.update("DELETE FROM cash_machine.machine_runtime WHERE id = 1");

        InvalidDataAccessApiUsageException exception =
                assertThrows(
                        InvalidDataAccessApiUsageException.class,
                        () -> repository.changeRuntime(MachineRunState.RUNNING, null, null, false));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("Machine runtime configuration is missing.", exception.getMessage());
    }

    @Test
    void completedCommandCannotBeCompletedAgain() {
        UUID id = UUID.randomUUID();
        maintenance.execute(maintainer, id, MaintenanceAction.SHUT_DOWN);

        InvalidDataAccessApiUsageException exception =
                assertThrows(
                        InvalidDataAccessApiUsageException.class,
                        () -> repository.completeCommand(id, now, false));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("The maintenance command is not pending.", exception.getMessage());
    }

    @Test
    void unknownCommandCannotBeCompleted() {
        InvalidDataAccessApiUsageException exception =
                assertThrows(
                        InvalidDataAccessApiUsageException.class,
                        () -> repository.completeCommand(UUID.randomUUID(), now, false));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("The maintenance command is not pending.", exception.getMessage());
    }

    private AuthenticatedUser user(UserRole role) {
        UUID id = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO cash_machine.user_account (
                    id, display_name, role, pin_hash, active
                )
                VALUES (?, ?, ?, 'test-only-unused-hash', TRUE)
                """,
                id,
                "Test " + role.name(),
                role.name());

        return new AuthenticatedUser(id, "Test " + role.name(), role, now.plusSeconds(900));
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, action::run);

        assertEquals(status, exception.getStatusCode());
    }
}
