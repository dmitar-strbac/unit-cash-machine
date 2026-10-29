package rs.unit.backend.maintenance.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.maintenance.domain.MachineRunState;
import rs.unit.backend.maintenance.domain.MaintenanceAction;
import rs.unit.backend.maintenance.infrastructure.persistence.MaintenanceRepository;
import rs.unit.backend.user.domain.UserRole;

@Service
public class MaintenanceService {

    private final MaintenanceRepository repository;
    private final MachineRuntimeGuard runtime;
    private final Clock clock;
    private final Duration resetDuration;
    private final Duration rebootDuration;

    public MaintenanceService(
            MaintenanceRepository repository,
            MachineRuntimeGuard runtime,
            Clock clock,
            @Value("${unit.maintenance.reset-duration-ms:1500}") long resetMillis,
            @Value("${unit.maintenance.reboot-duration-ms:3000}") long rebootMillis) {
        if (resetMillis <= 0 || rebootMillis <= 0) {
            throw new IllegalArgumentException("Maintenance durations must be positive.");
        }

        this.repository = repository;
        this.runtime = runtime;
        this.clock = clock;
        this.resetDuration = Duration.ofMillis(resetMillis);
        this.rebootDuration = Duration.ofMillis(rebootMillis);
    }

    @Transactional
    public MaintenanceCommandView execute(
            AuthenticatedUser user, UUID requestId, MaintenanceAction action) {
        repository.lockMachine();
        requireOperator(user);

        boolean permitted =
                switch (action) {
                    case RESET, REBOOT -> true;
                    case SHUT_DOWN, EXIT_APPLICATION -> user.role() == UserRole.MAINTENANCE;
                    case POWER_ON, START_APPLICATION -> false;
                };

        if (!permitted) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This maintenance action is not permitted.");
        }

        var previous = repository.findCommand(requestId).orElse(null);

        if (previous != null) {
            requireSameRequest(previous, action, user.id());
            return previous;
        }

        runtime.requireOperational(user);
        requireNoActiveWork();

        return begin(requestId, action, user.id());
    }

    @Transactional
    public MaintenanceCommandView resumeSimulator(UUID requestId, MaintenanceAction action) {
        repository.lockMachine();

        if (action != MaintenanceAction.POWER_ON && action != MaintenanceAction.START_APPLICATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid simulator action.");
        }

        var previous = repository.findCommand(requestId).orElse(null);

        if (previous != null) {
            requireSameRequest(previous, action, null);
            return previous;
        }

        MachineRunState expected =
                action == MaintenanceAction.POWER_ON
                        ? MachineRunState.SHUT_DOWN
                        : MachineRunState.APPLICATION_STOPPED;

        if (repository.runtime().state() != expected) {
            throw conflict("The machine is not in the required state.");
        }

        requireNoActiveWork();

        return begin(requestId, action, null);
    }

    @Transactional(readOnly = true)
    public MaintenanceCommandView command(AuthenticatedUser user, UUID id) {
        requireOperator(user);

        if (!runtime.isSessionValid(user)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Sign in again to view maintenance commands.");
        }

        return repository
                .findCommand(id)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Maintenance command not found."));
    }

    @Transactional
    public void synchronize() {
        repository.lockMachine();

        var current = repository.runtime();

        if (current.state() != MachineRunState.RESETTING
                && current.state() != MachineRunState.REBOOTING) {
            return;
        }

        Instant now = clock.instant();

        if (now.isBefore(current.completesAt())) {
            return;
        }

        repository.completeCommand(
                current.commandId(), now, current.action() == MaintenanceAction.RESET);
    }

    private MaintenanceCommandView begin(UUID id, MaintenanceAction action, UUID actor) {
        Instant now = clock.instant();

        MachineRunState target =
                switch (action) {
                    case RESET -> MachineRunState.RESETTING;
                    case REBOOT, POWER_ON, START_APPLICATION -> MachineRunState.REBOOTING;
                    case SHUT_DOWN -> MachineRunState.SHUT_DOWN;
                    case EXIT_APPLICATION -> MachineRunState.APPLICATION_STOPPED;
                };

        boolean pending =
                target == MachineRunState.RESETTING || target == MachineRunState.REBOOTING;

        Duration duration = action == MaintenanceAction.RESET ? resetDuration : rebootDuration;

        Instant completesAt = pending ? now.plus(duration) : now;

        repository.createCommand(id, action, actor, now, completesAt, pending);

        repository.changeRuntime(
                target, id, pending ? completesAt : null, action != MaintenanceAction.RESET);

        return repository.findCommand(id).orElseThrow();
    }

    private void requireOperator(AuthenticatedUser user) {
        if (user.role() != UserRole.MANAGEMENT && user.role() != UserRole.MAINTENANCE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Maintenance access is required.");
        }

        if (!clock.instant().isBefore(user.expiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The session has expired.");
        }
    }

    private void requireNoActiveWork() {
        if (repository.hasActiveWork()) {
            throw conflict(
                    "Finish the active deposit or CIT procedure before using system commands.");
        }
    }

    private void requireSameRequest(
            MaintenanceCommandView command, MaintenanceAction action, UUID actor) {
        if (command.action() != action || !Objects.equals(command.requestedBy(), actor)) {
            throw conflict("This request identifier belongs to another command.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
