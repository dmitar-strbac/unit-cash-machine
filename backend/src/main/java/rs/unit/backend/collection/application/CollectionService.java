package rs.unit.backend.collection.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.collection.domain.CollectionStep;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository.StoredCollection;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.PhysicalAction;
import rs.unit.backend.hardware.port.HardwareControlPort;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@Service
public class CollectionService {

    private final CollectionRepository repository;
    private final DepositService deposits;
    private final HardwareControlPort hardware;
    private final UserAccountRepository users;
    private final Clock clock;
    private final Duration securingTimeout;
    private final UUID runtimeId = UUID.randomUUID();

    public CollectionService(
            CollectionRepository repository,
            DepositService deposits,
            HardwareControlPort hardware,
            UserAccountRepository users,
            Clock clock,
            @Value("${unit.collection.securing-timeout-ms:5000}") long securingTimeoutMillis) {
        if (securingTimeoutMillis <= 0) {
            throw new IllegalArgumentException("The securing timeout must be positive.");
        }

        this.repository = repository;
        this.deposits = deposits;
        this.hardware = hardware;
        this.users = users;
        this.clock = clock;
        this.securingTimeout = Duration.ofMillis(securingTimeoutMillis);
    }

    @Transactional
    public void beginOrResume(AuthenticatedUser user) {
        requireCit(user);

        repository.lockMachine();

        if (!deposits.prepareForCollection()) {
            throw conflict("Finish the active deposit before starting cash collection.");
        }

        Instant now = clock.instant();
        HardwareSnapshot snapshot = hardware.snapshot();

        requireOperationalLock(snapshot);

        StoredCollection existing = repository.findUnfinished().orElse(null);

        if (existing == null) {
            if (snapshot.door() != DoorState.CLOSED
                    || snapshot.lock() != LockState.LOCKED
                    || snapshot.acceptor() == AcceptorState.ACCEPTING) {
                throw conflict("The machine is not ready to start cash collection.");
            }

            UUID id = UUID.randomUUID();

            repository.create(id, repository.currentCycleId(), user, runtimeId, now);
            hardware.unlockDoor();

            repository.event(id, user.id(), "STARTED", now, hardware.snapshot());

            return;
        }

        if (existing.step() != CollectionStep.INCOMPLETE
                && existing.runtimeId().equals(runtimeId)
                && now.isBefore(existing.leaseExpiresAt())) {
            throw conflict("Another CIT session is already using this machine.");
        }

        repository.claim(existing.id(), user, runtimeId);

        StoredCollection claimed = repository.findById(existing.id()).orElseThrow();

        if (claimed.step() == CollectionStep.OPEN_DOOR && snapshot.door() == DoorState.CLOSED) {
            hardware.unlockDoor();
        }

        repository.event(existing.id(), user.id(), "RESUMED", now, hardware.snapshot());
    }

    @Transactional(readOnly = true)
    public CollectionView current(AuthenticatedUser user) {
        requireCit(user);

        StoredCollection collection =
                repository
                        .findByLoginId(user.loginId())
                        .orElseThrow(
                                () -> conflict("No collection is associated with this login."));

        requireOwner(collection, user);

        return view(collection);
    }

    @Transactional
    public CollectionView confirmRemoval(AuthenticatedUser user, UUID collectionId) {
        repository.lockMachine();

        StoredCollection collection = owned(user, collectionId);

        if (collection.removalConfirmedAt() != null) {
            return view(collection);
        }

        if (collection.step() != CollectionStep.REMOVE_CASSETTE) {
            throw conflict("Cassette removal cannot be confirmed at this step.");
        }

        requireOpenGate(hardware.snapshot());

        Instant now = clock.instant();

        repository.confirmRemoval(collectionId, now);
        repository.event(collectionId, user.id(), "REMOVAL_CONFIRMED", now, hardware.snapshot());

        return view(repository.findById(collectionId).orElseThrow());
    }

    @Transactional
    public CollectionView confirmInsertion(AuthenticatedUser user, UUID collectionId) {
        repository.lockMachine();

        StoredCollection collection = owned(user, collectionId);

        if (collection.insertionConfirmedAt() != null) {
            return view(collection);
        }

        if (collection.step() != CollectionStep.INSERT_CASSETTE) {
            throw conflict("Cassette insertion cannot be confirmed at this step.");
        }

        requireOpenGate(hardware.snapshot());

        Instant now = clock.instant();

        repository.confirmInsertion(collectionId, now);
        repository.event(collectionId, user.id(), "INSERTION_CONFIRMED", now, hardware.snapshot());

        return view(repository.findById(collectionId).orElseThrow());
    }

    @Transactional
    public void restart(AuthenticatedUser user, UUID collectionId) {
        repository.lockMachine();

        StoredCollection collection = owned(user, collectionId);

        if (collection.step() != CollectionStep.INCOMPLETE) {
            throw conflict("Restart is available only for an incomplete collection.");
        }

        Instant now = clock.instant();

        repository.releaseLease(collectionId, now);
        repository.event(
                collectionId, user.id(), "REAUTHENTICATION_REQUESTED", now, hardware.snapshot());
    }

    @Transactional
    public HardwareSnapshot simulate(
            AuthenticatedUser user, UUID collectionId, PhysicalAction action) {
        repository.lockMachine();

        StoredCollection collection = owned(user, collectionId);

        if (collection.step() == CollectionStep.COMPLETED) {
            throw conflict("This collection has already completed.");
        }

        return hardware.simulate(action);
    }

    @Transactional(readOnly = true)
    public boolean hasUnfinishedCollection(AuthenticatedUser user) {
        if (user.role() != UserRole.CIT) {
            return false;
        }

        return repository
                .findByLoginId(user.loginId())
                .filter(collection -> collection.step() != CollectionStep.COMPLETED)
                .isPresent();
    }

    @Transactional
    public void synchronize() {
        repository.lockMachine();

        StoredCollection collection = repository.findUnfinished().orElse(null);

        if (collection == null) {
            return;
        }

        if (collection.step() == CollectionStep.INCOMPLETE) {
            completeAfterLateLock(collection);
            return;
        }

        Instant now = clock.instant();
        HardwareSnapshot snapshot = hardware.snapshot();

        if (!collection.runtimeId().equals(runtimeId)) {
            incomplete(collection, "APPLICATION_RESTARTED", now, snapshot);
            return;
        }

        if (!now.isBefore(collection.leaseExpiresAt())) {
            incomplete(collection, "SESSION_EXPIRED", now, snapshot);
            return;
        }

        boolean operatorAvailable =
                users.findById(collection.operatorId())
                        .filter(account -> account.isActive())
                        .filter(account -> account.getRole() == UserRole.CIT)
                        .isPresent();

        if (!operatorAvailable) {
            incomplete(collection, "ACCOUNT_UNAVAILABLE", now, snapshot);
            return;
        }

        if (snapshot.extensionBoard() != ExtensionBoardState.ONLINE
                || snapshot.lock() == LockState.FAULT
                || snapshot.lock() == LockState.UNKNOWN) {
            incomplete(collection, "GATE_HARDWARE_UNAVAILABLE", now, snapshot);
            return;
        }

        switch (collection.step()) {
            case OPEN_DOOR -> {
                if (snapshot.lock() != LockState.UNLOCKED || snapshot.door() == DoorState.UNKNOWN) {
                    incomplete(collection, "UNEXPECTED_GATE_STATE", now, snapshot);
                } else if (snapshot.door() == DoorState.OPEN) {
                    repository.changeStep(collection.id(), CollectionStep.REMOVE_CASSETTE, null);
                }
            }

            case REMOVE_CASSETTE, INSERT_CASSETTE -> {
                if (snapshot.door() != DoorState.OPEN || snapshot.lock() != LockState.UNLOCKED) {
                    incomplete(collection, "GATE_CLOSED_TOO_EARLY", now, snapshot);
                }
            }

            case CLOSE_DOOR -> {
                if (snapshot.door() == DoorState.UNKNOWN) {
                    incomplete(collection, "DOOR_STATE_UNKNOWN", now, snapshot);
                } else if (snapshot.door() == DoorState.CLOSED) {
                    if (snapshot.lock() == LockState.LOCKED) {
                        complete(collection, now, snapshot);
                    } else {
                        repository.changeStep(
                                collection.id(),
                                CollectionStep.SECURING,
                                now.plus(securingTimeout));
                    }
                }
            }

            case SECURING -> {
                if (snapshot.door() != DoorState.CLOSED) {
                    incomplete(collection, "DOOR_REOPENED", now, snapshot);
                } else if (snapshot.lock() == LockState.LOCKED) {
                    complete(collection, now, snapshot);
                } else if (!now.isBefore(collection.securingDeadline())) {
                    incomplete(collection, "LOCK_TIMEOUT", now, snapshot);
                }
            }

            case INCOMPLETE, COMPLETED -> {}
        }
    }

    private void complete(StoredCollection collection, Instant now, HardwareSnapshot snapshot) {
        if (collection.removalConfirmedAt() == null || collection.insertionConfirmedAt() == null) {
            throw new IllegalStateException("Cassette confirmations are missing.");
        }

        long total = repository.cycleTotal(collection.cashCycleId());

        repository.complete(collection, now, total);
        repository.event(collection.id(), collection.operatorId(), "COMPLETED", now, snapshot);
    }

    private void completeAfterLateLock(StoredCollection collection) {
        Instant now = clock.instant();

        if (!"LOCK_TIMEOUT".equals(collection.errorCode())
                || !collection.runtimeId().equals(runtimeId)
                || !now.isBefore(collection.leaseExpiresAt())) {
            return;
        }

        boolean operatorAvailable =
                users.findById(collection.operatorId())
                        .filter(account -> account.isActive())
                        .filter(account -> account.getRole() == UserRole.CIT)
                        .isPresent();

        if (!operatorAvailable) {
            return;
        }

        HardwareSnapshot snapshot = hardware.snapshot();

        if (snapshot.extensionBoard() == ExtensionBoardState.ONLINE
                && snapshot.door() == DoorState.CLOSED
                && snapshot.lock() == LockState.LOCKED) {
            complete(collection, now, snapshot);
        }
    }

    private void incomplete(
            StoredCollection collection, String reason, Instant now, HardwareSnapshot snapshot) {
        repository.markIncomplete(collection.id(), reason);
        repository.event(collection.id(), null, reason, now, snapshot);
    }

    private StoredCollection owned(AuthenticatedUser user, UUID collectionId) {
        requireCit(user);

        StoredCollection collection =
                repository
                        .findById(collectionId)
                        .orElseThrow(() -> conflict("The collection does not exist."));

        requireOwner(collection, user);

        return collection;
    }

    private void requireOwner(StoredCollection collection, AuthenticatedUser user) {
        if (!collection.operatorId().equals(user.id())
                || !collection.loginId().equals(user.loginId())
                || !collection.runtimeId().equals(runtimeId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This collection is not assigned to the current login.");
        }

        if (!clock.instant().isBefore(collection.leaseExpiresAt())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Sign in again to continue the collection.");
        }
    }

    private CollectionView view(StoredCollection collection) {
        HardwareSnapshot snapshot = hardware.snapshot();

        return new CollectionView(
                collection.id(),
                collection.step(),
                snapshot.door() == DoorState.CLOSED,
                snapshot.lock() == LockState.LOCKED,
                collection.removalConfirmedAt() != null,
                collection.insertionConfirmedAt() != null,
                collection.startedAt(),
                collection.securingDeadline(),
                collection.finishedAt(),
                collection.collectedTotalCents(),
                collection.errorCode());
    }

    private void requireCit(AuthenticatedUser user) {
        if (user.role() != UserRole.CIT) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only CIT operators can perform cash collection.");
        }

        if (!clock.instant().isBefore(user.expiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The session has expired.");
        }
    }

    private static void requireOperationalLock(HardwareSnapshot snapshot) {
        if (snapshot.extensionBoard() != ExtensionBoardState.ONLINE
                || snapshot.lock() == LockState.FAULT
                || snapshot.lock() == LockState.UNKNOWN
                || snapshot.door() == DoorState.UNKNOWN) {
            throw conflict("The gate hardware is unavailable.");
        }
    }

    private static void requireOpenGate(HardwareSnapshot snapshot) {
        requireOperationalLock(snapshot);

        if (snapshot.door() != DoorState.OPEN || snapshot.lock() != LockState.UNLOCKED) {
            throw conflict("The door must remain open and unlocked at this step.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
