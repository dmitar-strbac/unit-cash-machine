package rs.unit.backend.maintenance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.port.HardwareStatusPort;
import rs.unit.backend.machine.domain.MachineReadinessPolicy;
import rs.unit.backend.maintenance.infrastructure.persistence.MaintenanceRepository;
import rs.unit.backend.user.domain.UserRole;

@Service
public class ComponentService {

    private final MaintenanceRepository repository;
    private final MachineRuntimeGuard runtime;
    private final HardwareStatusPort hardware;
    private final Clock clock;

    public ComponentService(
            MaintenanceRepository repository,
            MachineRuntimeGuard runtime,
            HardwareStatusPort hardware,
            Clock clock) {
        this.repository = repository;
        this.runtime = runtime;
        this.hardware = hardware;
        this.clock = clock;
    }

    @Transactional
    public List<ComponentView> components(AuthenticatedUser user) {
        if (user.role() != UserRole.MAINTENANCE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only maintenance can inspect components.");
        }

        repository.lockMachine();

        if (!clock.instant().isBefore(user.expiresAt()) || !runtime.isSessionValid(user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The session has expired.");
        }

        var snapshot = hardware.snapshot();

        var resets =
                repository.componentResets().stream()
                        .collect(
                                Collectors.toMap(
                                        MaintenanceRepository.ComponentReset::id, value -> value));

        List<ComponentView> result = new ArrayList<>();

        result.add(component("ACCEPTOR", "Banknote acceptor", snapshot.acceptor().name(), resets));
        result.add(component("CASHBOX", "Cash cassette", snapshot.cashbox().name(), resets));
        result.add(component("DOOR", "Door sensor", snapshot.door().name(), resets));
        result.add(component("LOCK", "Electronic lock", snapshot.lock().name(), resets));
        result.add(
                component(
                        "EXTENSION_BOARD",
                        "Extension board",
                        snapshot.extensionBoard().name(),
                        resets));
        result.add(component("PRINTER", "Receipt printer", snapshot.printer().name(), resets));

        result.add(
                derived(
                        "DOORS_CLOSED",
                        "Door closed",
                        Boolean.toString(snapshot.door() == DoorState.CLOSED)));

        result.add(
                derived(
                        "LOCKS_SECURE",
                        "Lock secured",
                        Boolean.toString(snapshot.lock() == LockState.LOCKED)));

        result.add(
                derived(
                        "MACHINE_SECURE",
                        "Machine secured",
                        Boolean.toString(MachineReadinessPolicy.isSecure(snapshot))));

        return List.copyOf(result);
    }

    private ComponentView component(
            String id,
            String description,
            String state,
            Map<String, MaintenanceRepository.ComponentReset> resets) {
        var reset = resets.get(id);

        return new ComponentView(
                id,
                description,
                state,
                true,
                false,
                Map.of(
                        "model", "Unit simulated " + description,
                        "serialNumber", "SIM-" + id + "-001",
                        "driverVersion", "SIM-1.0"),
                reset.resetCount(),
                reset.lastResetAt());
    }

    private ComponentView derived(String id, String description, String state) {
        return new ComponentView(id, description, state, true, true, Map.of(), 0, null);
    }

    public record ComponentView(
            String id,
            String description,
            String state,
            boolean simulated,
            boolean derived,
            Map<String, String> metadata,
            long resetCount,
            Instant lastResetAt) {

        public ComponentView {
            metadata = Map.copyOf(metadata);
        }
    }
}
