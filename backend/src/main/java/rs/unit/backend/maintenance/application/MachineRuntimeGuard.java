package rs.unit.backend.maintenance.application;

import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.maintenance.domain.MachineRunState;
import rs.unit.backend.maintenance.infrastructure.persistence.MaintenanceRepository;

@Service
public class MachineRuntimeGuard {

    private final MaintenanceRepository repository;
    private final Clock clock;

    public MachineRuntimeGuard(MaintenanceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public long generationForLogin() {
        repository.lockMachine();
        requireRunning();

        return repository.runtime().generation();
    }

    @Transactional(readOnly = true)
    public boolean isRunning() {
        return repository.runtime().state() == MachineRunState.RUNNING;
    }

    @Transactional(readOnly = true)
    public boolean isSessionValid(AuthenticatedUser user) {
        var runtime = repository.runtime();

        return user.applicationGeneration() == runtime.generation()
                && (runtime.state() == MachineRunState.RUNNING
                        || runtime.state() == MachineRunState.RESETTING);
    }

    @Transactional(readOnly = true)
    public void requireOperational(AuthenticatedUser user) {
        var runtime = repository.runtime();

        if (user.applicationGeneration() != runtime.generation()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "The application has restarted. Sign in again.");
        }

        if (!clock.instant().isBefore(user.expiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The session has expired.");
        }

        requireRunning();
    }

    @Transactional(readOnly = true)
    public void requireRunning() {
        if (!isRunning()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The machine application is not ready for this operation.");
        }
    }

    @Transactional(readOnly = true)
    public MachineRuntimeView view() {
        var runtime = repository.runtime();

        boolean showNotice =
                runtime.noticeUntil() != null && clock.instant().isBefore(runtime.noticeUntil());

        return new MachineRuntimeView(
                runtime.state(),
                runtime.generation(),
                runtime.commandId(),
                runtime.action(),
                runtime.completesAt(),
                showNotice
                        ? "Component drivers reset. Physical hardware faults may still require intervention."
                        : null);
    }
}
