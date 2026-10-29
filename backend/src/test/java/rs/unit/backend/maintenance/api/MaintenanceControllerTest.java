package rs.unit.backend.maintenance.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.maintenance.application.ComponentService;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.maintenance.application.MachineRuntimeView;
import rs.unit.backend.maintenance.application.MaintenanceCommandView;
import rs.unit.backend.maintenance.application.MaintenanceService;
import rs.unit.backend.maintenance.domain.MachineRunState;
import rs.unit.backend.maintenance.domain.MaintenanceAction;
import rs.unit.backend.user.domain.UserRole;

class MaintenanceControllerTest {

    private final MaintenanceService maintenance = mock(MaintenanceService.class);
    private final MachineRuntimeGuard runtime = mock(MachineRuntimeGuard.class);
    private final ComponentService components = mock(ComponentService.class);

    private final MaintenanceController controller =
            new MaintenanceController(maintenance, runtime, components);

    private final AuthenticatedUser user =
            new AuthenticatedUser(
                    UUID.randomUUID(),
                    "Test Maintainer",
                    UserRole.MAINTENANCE,
                    Instant.parse("2026-01-01T12:15:00Z"));

    @Test
    void runtimeReturnsCurrentRuntimeView() {
        var expected = new MachineRuntimeView(MachineRunState.RUNNING, 3, null, null, null, null);

        when(runtime.view()).thenReturn(expected);

        assertSame(expected, controller.runtime());
        verify(runtime).view();
    }

    @Test
    void executeForwardsUserRequestIdentifierAndAction() {
        UUID id = UUID.randomUUID();
        var expected = command(id, MaintenanceAction.RESET);
        var request = new MaintenanceController.CommandRequest(id, MaintenanceAction.RESET);

        when(maintenance.execute(user, id, MaintenanceAction.RESET)).thenReturn(expected);

        assertSame(expected, controller.execute(user, request));
        verify(maintenance).execute(user, id, MaintenanceAction.RESET);
    }

    @Test
    void commandForwardsUserAndCommandIdentifier() {
        UUID id = UUID.randomUUID();
        var expected = command(id, MaintenanceAction.RESET);

        when(maintenance.command(user, id)).thenReturn(expected);

        assertSame(expected, controller.command(user, id));
        verify(maintenance).command(user, id);
    }

    @Test
    void componentsReturnsServiceResult() {
        List<ComponentService.ComponentView> expected = List.of();
        when(components.components(user)).thenReturn(expected);

        assertSame(expected, controller.components(user));
        verify(components).components(user);
    }

    @Test
    void powerOnUsesFixedPowerOnAction() {
        var simulator = new RuntimeSimulationController(maintenance);
        UUID id = UUID.randomUUID();
        var expected = command(id, MaintenanceAction.POWER_ON);
        var request = new RuntimeSimulationController.StartRequest(id);

        when(maintenance.resumeSimulator(id, MaintenanceAction.POWER_ON)).thenReturn(expected);

        assertSame(expected, simulator.powerOn(request));
        verify(maintenance).resumeSimulator(id, MaintenanceAction.POWER_ON);
    }

    @Test
    void applicationStartUsesFixedStartApplicationAction() {
        var simulator = new RuntimeSimulationController(maintenance);
        UUID id = UUID.randomUUID();
        var expected = command(id, MaintenanceAction.START_APPLICATION);
        var request = new RuntimeSimulationController.StartRequest(id);

        when(maintenance.resumeSimulator(id, MaintenanceAction.START_APPLICATION))
                .thenReturn(expected);

        assertSame(expected, simulator.start(request));
        verify(maintenance).resumeSimulator(id, MaintenanceAction.START_APPLICATION);
    }

    private MaintenanceCommandView command(UUID id, MaintenanceAction action) {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");

        return new MaintenanceCommandView(
                id, action, user.id(), "PENDING", now, now.plusSeconds(3), null);
    }
}
