package rs.unit.backend.collection.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.collection.domain.CollectionStep;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository.StoredCollection;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.model.PhysicalAction;
import rs.unit.backend.hardware.port.HardwareControlPort;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private CollectionRepository repository;
    @Mock private DepositService deposits;
    @Mock private HardwareControlPort hardware;
    @Mock private UserAccountRepository users;
    @Mock private Clock clock;

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final UUID operatorId = UUID.randomUUID();
    private final UUID loginId = UUID.randomUUID();
    private final UUID cycleId = UUID.randomUUID();

    private UUID id;
    private UUID runtimeId;
    private AuthenticatedUser cit;
    private CollectionService service;

    @BeforeEach
    void setUp() {
        cit =
                new AuthenticatedUser(
                        operatorId, "Test CIT", UserRole.CIT, now.plusSeconds(900), loginId);

        lenient().when(clock.instant()).thenReturn(now);
        lenient().when(deposits.prepareForCollection()).thenReturn(true);
        lenient().when(repository.currentCycleId()).thenReturn(cycleId);
        lenient().when(repository.findUnfinished()).thenReturn(Optional.empty());

        lenient().when(hardware.snapshot()).thenReturn(gate(DoorState.CLOSED, LockState.LOCKED));

        lenient()
                .when(users.findById(operatorId))
                .thenReturn(
                        Optional.of(
                                new UserAccount(
                                        operatorId,
                                        "Test CIT",
                                        UserRole.CIT,
                                        "unused-hash",
                                        true)));

        service = newService(5000);
        service.beginOrResume(cit);

        var idCaptor = ArgumentCaptor.forClass(UUID.class);
        var runtimeCaptor = ArgumentCaptor.forClass(UUID.class);

        verify(repository)
                .create(idCaptor.capture(), eq(cycleId), eq(cit), runtimeCaptor.capture(), eq(now));

        id = idCaptor.getValue();
        runtimeId = runtimeCaptor.getValue();

        use(record(CollectionStep.OPEN_DOOR), gate(DoorState.CLOSED, LockState.UNLOCKED));

        clearInvocations(repository, deposits, hardware, users);
    }

    @Test
    void rejectsNonPositiveSecuringTimeout() {
        assertThrows(IllegalArgumentException.class, () -> newService(0));
        assertThrows(IllegalArgumentException.class, () -> newService(-1));
    }

    @Test
    void rejectsNonCitUser() {
        var cashier =
                new AuthenticatedUser(operatorId, "Cashier", UserRole.CASHIER, cit.expiresAt());

        assertStatus(HttpStatus.FORBIDDEN, () -> service.beginOrResume(cashier));
        assertFalse(service.hasUnfinishedCollection(cashier));
    }

    @Test
    void rejectsExpiredAuthentication() {
        var expired = new AuthenticatedUser(operatorId, "Test CIT", UserRole.CIT, now, loginId);

        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.beginOrResume(expired));
    }

    @Test
    void rejectsBeginningCollectionDuringDeposit() {
        when(deposits.prepareForCollection()).thenReturn(false);

        assertStatus(HttpStatus.CONFLICT, () -> service.beginOrResume(cit));
    }

    @ParameterizedTest
    @MethodSource("unavailableGateHardware")
    void rejectsBeginningCollectionWithUnavailableHardware(HardwareSnapshot snapshot) {
        when(hardware.snapshot()).thenReturn(snapshot);

        assertStatus(HttpStatus.CONFLICT, () -> service.beginOrResume(cit));
    }

    static Stream<HardwareSnapshot> unavailableGateHardware() {
        return Stream.of(
                board(gate(DoorState.CLOSED, LockState.LOCKED), ExtensionBoardState.OFFLINE),
                gate(DoorState.CLOSED, LockState.FAULT),
                gate(DoorState.CLOSED, LockState.UNKNOWN),
                gate(DoorState.UNKNOWN, LockState.LOCKED));
    }

    @ParameterizedTest
    @MethodSource("invalidStartingStates")
    void rejectsNewCollectionWithoutSecureIdleGate(HardwareSnapshot snapshot) {
        when(repository.findUnfinished()).thenReturn(Optional.empty());
        when(hardware.snapshot()).thenReturn(snapshot);

        assertStatus(HttpStatus.CONFLICT, () -> service.beginOrResume(cit));
    }

    static Stream<HardwareSnapshot> invalidStartingStates() {
        HardwareSnapshot accepting =
                new HardwareSnapshot(
                        AcceptorState.ACCEPTING,
                        CashboxState.PRESENT,
                        DoorState.CLOSED,
                        LockState.LOCKED,
                        ExtensionBoardState.ONLINE,
                        PrinterState.READY);

        return Stream.of(
                gate(DoorState.OPEN, LockState.UNLOCKED),
                gate(DoorState.CLOSED, LockState.UNLOCKED),
                accepting);
    }

    @Test
    void rejectsAnotherLiveCitSession() {
        assertStatus(HttpStatus.CONFLICT, () -> service.beginOrResume(cit));
    }

    @Test
    void resumesIncompleteCollectionAndUnlocksClosedDoor() {
        StoredCollection incomplete = record(CollectionStep.INCOMPLETE);

        when(repository.findUnfinished()).thenReturn(Optional.of(incomplete));
        when(repository.findById(id)).thenReturn(Optional.of(record(CollectionStep.OPEN_DOOR)));
        when(hardware.snapshot()).thenReturn(gate(DoorState.CLOSED, LockState.LOCKED));

        service.beginOrResume(cit);

        verify(repository).claim(id, cit, runtimeId);
        verify(hardware).unlockDoor();
    }

    @Test
    void resumesCollectionFromPreviousApplicationRun() {
        StoredCollection previous =
                ownership(
                        record(CollectionStep.REMOVE_CASSETTE),
                        operatorId,
                        loginId,
                        UUID.randomUUID(),
                        cit.expiresAt());

        use(previous, gate(DoorState.OPEN, LockState.UNLOCKED));

        service.beginOrResume(cit);

        verify(repository).claim(id, cit, runtimeId);
        verify(hardware, never()).unlockDoor();
    }

    @Test
    void resumesExpiredLeaseFromSameApplicationRun() {
        StoredCollection expired =
                ownership(
                        record(CollectionStep.REMOVE_CASSETTE),
                        operatorId,
                        loginId,
                        runtimeId,
                        now);

        use(expired, gate(DoorState.OPEN, LockState.UNLOCKED));

        service.beginOrResume(cit);

        verify(repository).claim(id, cit, runtimeId);
    }

    @Test
    void resumesOpenDoorStepWithoutUnlockingAlreadyOpenDoor() {
        StoredCollection expired =
                ownership(record(CollectionStep.OPEN_DOOR), operatorId, loginId, runtimeId, now);

        use(expired, gate(DoorState.OPEN, LockState.UNLOCKED));

        service.beginOrResume(cit);

        verify(repository).claim(id, cit, runtimeId);
        verify(hardware, never()).unlockDoor();
    }

    @Test
    void rejectsCurrentCollectionWhenLoginHasNoCollection() {
        when(repository.findByLoginId(loginId)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.CONFLICT, () -> service.current(cit));
    }

    @Test
    void rejectsMissingCollectionIdentifier() {
        UUID missing = UUID.randomUUID();

        when(repository.findById(missing)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.CONFLICT, () -> service.confirmRemoval(cit, missing));
    }

    @Test
    void rejectsDifferentOperatorLoginAndRuntime() {
        StoredCollection original = record(CollectionStep.OPEN_DOOR);

        StoredCollection[] inaccessible = {
            ownership(original, UUID.randomUUID(), loginId, runtimeId, cit.expiresAt()),
            ownership(original, operatorId, UUID.randomUUID(), runtimeId, cit.expiresAt()),
            ownership(original, operatorId, loginId, UUID.randomUUID(), cit.expiresAt())
        };

        for (StoredCollection collection : inaccessible) {
            when(repository.findByLoginId(loginId)).thenReturn(Optional.of(collection));

            assertStatus(HttpStatus.FORBIDDEN, () -> service.current(cit));
        }
    }

    @Test
    void rejectsExpiredCollectionLease() {
        StoredCollection expired =
                ownership(record(CollectionStep.OPEN_DOOR), operatorId, loginId, runtimeId, now);

        when(repository.findByLoginId(loginId)).thenReturn(Optional.of(expired));

        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.current(cit));
    }

    @Test
    void rejectsRemovalAtWrongStep() {
        assertStatus(HttpStatus.CONFLICT, () -> service.confirmRemoval(cit, id));
    }

    @Test
    void confirmsRemovalAndIgnoresRepeatedConfirmation() {
        use(record(CollectionStep.REMOVE_CASSETTE), gate(DoorState.OPEN, LockState.UNLOCKED));

        service.confirmRemoval(cit, id);

        verify(repository).confirmRemoval(id, now);

        clearInvocations(repository);

        use(record(CollectionStep.INSERT_CASSETTE), gate(DoorState.OPEN, LockState.UNLOCKED));

        service.confirmRemoval(cit, id);

        verify(repository, never()).confirmRemoval(any(UUID.class), any(Instant.class));
    }

    @Test
    void rejectsInsertionAtWrongStep() {
        use(record(CollectionStep.REMOVE_CASSETTE), gate(DoorState.OPEN, LockState.UNLOCKED));

        assertStatus(HttpStatus.CONFLICT, () -> service.confirmInsertion(cit, id));
    }

    @Test
    void confirmsInsertionAndIgnoresRepeatedConfirmation() {
        use(record(CollectionStep.INSERT_CASSETTE), gate(DoorState.OPEN, LockState.UNLOCKED));

        service.confirmInsertion(cit, id);

        verify(repository).confirmInsertion(id, now);

        clearInvocations(repository);

        use(record(CollectionStep.CLOSE_DOOR), gate(DoorState.OPEN, LockState.UNLOCKED));

        service.confirmInsertion(cit, id);

        verify(repository, never()).confirmInsertion(any(UUID.class), any(Instant.class));
    }

    @Test
    void rejectsConfirmationWhenDoorIsClosedOrLocked() {
        use(record(CollectionStep.REMOVE_CASSETTE), gate(DoorState.CLOSED, LockState.UNLOCKED));

        assertStatus(HttpStatus.CONFLICT, () -> service.confirmRemoval(cit, id));

        use(record(CollectionStep.REMOVE_CASSETTE), gate(DoorState.OPEN, LockState.LOCKED));

        assertStatus(HttpStatus.CONFLICT, () -> service.confirmRemoval(cit, id));
    }

    @Test
    void restartsOnlyIncompleteCollection() {
        assertStatus(HttpStatus.CONFLICT, () -> service.restart(cit, id));

        use(record(CollectionStep.INCOMPLETE), gate(DoorState.CLOSED, LockState.UNLOCKED));

        service.restart(cit, id);

        verify(repository).releaseLease(id, now);
    }

    @Test
    void delegatesPhysicalActionForUnfinishedCollection() {
        HardwareSnapshot result = gate(DoorState.OPEN, LockState.UNLOCKED);

        when(hardware.simulate(PhysicalAction.OPEN_DOOR)).thenReturn(result);

        assertEquals(result, service.simulate(cit, id, PhysicalAction.OPEN_DOOR));
    }

    @Test
    void rejectsPhysicalActionAfterCompletion() {
        use(record(CollectionStep.COMPLETED), gate(DoorState.CLOSED, LockState.LOCKED));

        assertStatus(
                HttpStatus.CONFLICT, () -> service.simulate(cit, id, PhysicalAction.OPEN_DOOR));
    }

    @Test
    void reportsWhetherLoginHasUnfinishedCollection() {
        assertTrue(service.hasUnfinishedCollection(cit));

        when(repository.findByLoginId(loginId)).thenReturn(Optional.empty());

        assertFalse(service.hasUnfinishedCollection(cit));

        when(repository.findByLoginId(loginId))
                .thenReturn(Optional.of(record(CollectionStep.COMPLETED)));

        assertFalse(service.hasUnfinishedCollection(cit));
    }

    @Test
    void synchronizationIgnoresMissingAndAlreadyIncompleteCollections() {
        when(repository.findUnfinished()).thenReturn(Optional.empty());

        service.synchronize();

        when(repository.findUnfinished())
                .thenReturn(Optional.of(record(CollectionStep.INCOMPLETE)));

        service.synchronize();

        verify(repository, never()).markIncomplete(any(UUID.class), any(String.class));
    }

    @Test
    void marksPreviousApplicationRunIncomplete() {
        StoredCollection previous =
                ownership(
                        record(CollectionStep.OPEN_DOOR),
                        operatorId,
                        loginId,
                        UUID.randomUUID(),
                        cit.expiresAt());

        use(previous, gate(DoorState.CLOSED, LockState.UNLOCKED));

        service.synchronize();

        verify(repository).markIncomplete(id, "APPLICATION_RESTARTED");
    }

    @Test
    void marksExpiredLeaseIncomplete() {
        StoredCollection expired =
                ownership(record(CollectionStep.OPEN_DOOR), operatorId, loginId, runtimeId, now);

        use(expired, gate(DoorState.CLOSED, LockState.UNLOCKED));

        service.synchronize();

        verify(repository).markIncomplete(id, "SESSION_EXPIRED");
    }

    @Test
    void marksMissingInactiveAndChangedRoleOperatorsUnavailable() {
        for (int scenario = 0; scenario < 3; scenario++) {
            Optional<UserAccount> account =
                    switch (scenario) {
                        case 0 -> Optional.empty();
                        case 1 ->
                                Optional.of(
                                        new UserAccount(
                                                operatorId,
                                                "Inactive CIT",
                                                UserRole.CIT,
                                                "unused",
                                                false));
                        default ->
                                Optional.of(
                                        new UserAccount(
                                                operatorId,
                                                "Changed role",
                                                UserRole.MANAGEMENT,
                                                "unused",
                                                true));
                    };

            when(users.findById(operatorId)).thenReturn(account);

            service.synchronize();

            verify(repository).markIncomplete(id, "ACCOUNT_UNAVAILABLE");
            clearInvocations(repository);
        }
    }

    @ParameterizedTest
    @MethodSource("monitorHardwareFailures")
    void marksGateHardwareUnavailable(HardwareSnapshot snapshot) {
        use(record(CollectionStep.OPEN_DOOR), snapshot);

        service.synchronize();

        verify(repository).markIncomplete(id, "GATE_HARDWARE_UNAVAILABLE");
    }

    static Stream<HardwareSnapshot> monitorHardwareFailures() {
        return Stream.of(
                board(gate(DoorState.CLOSED, LockState.UNLOCKED), ExtensionBoardState.OFFLINE),
                gate(DoorState.CLOSED, LockState.FAULT),
                gate(DoorState.CLOSED, LockState.UNKNOWN));
    }

    @ParameterizedTest
    @MethodSource("gateFailuresDuringWorkflow")
    void recordsInvalidGateTransition(
            CollectionStep step, HardwareSnapshot snapshot, String expectedError) {
        use(record(step), snapshot);

        service.synchronize();

        verify(repository).markIncomplete(id, expectedError);
    }

    static Stream<Arguments> gateFailuresDuringWorkflow() {
        return Stream.of(
                Arguments.of(
                        CollectionStep.OPEN_DOOR,
                        gate(DoorState.CLOSED, LockState.LOCKED),
                        "UNEXPECTED_GATE_STATE"),
                Arguments.of(
                        CollectionStep.OPEN_DOOR,
                        gate(DoorState.UNKNOWN, LockState.UNLOCKED),
                        "UNEXPECTED_GATE_STATE"),
                Arguments.of(
                        CollectionStep.REMOVE_CASSETTE,
                        gate(DoorState.CLOSED, LockState.UNLOCKED),
                        "GATE_CLOSED_TOO_EARLY"),
                Arguments.of(
                        CollectionStep.INSERT_CASSETTE,
                        gate(DoorState.OPEN, LockState.LOCKED),
                        "GATE_CLOSED_TOO_EARLY"),
                Arguments.of(
                        CollectionStep.CLOSE_DOOR,
                        gate(DoorState.UNKNOWN, LockState.UNLOCKED),
                        "DOOR_STATE_UNKNOWN"),
                Arguments.of(
                        CollectionStep.SECURING,
                        gate(DoorState.OPEN, LockState.UNLOCKED),
                        "DOOR_REOPENED"));
    }

    @Test
    void waitsForDoorOpening() {
        service.synchronize();

        verify(repository, never()).changeStep(any(UUID.class), any(CollectionStep.class), any());
    }

    @Test
    void advancesAfterDoorOpening() {
        use(record(CollectionStep.OPEN_DOOR), gate(DoorState.OPEN, LockState.UNLOCKED));

        service.synchronize();

        verify(repository).changeStep(id, CollectionStep.REMOVE_CASSETTE, null);
    }

    @Test
    void keepsCassetteStepsWhileDoorRemainsOpen() {
        use(record(CollectionStep.REMOVE_CASSETTE), gate(DoorState.OPEN, LockState.UNLOCKED));
        service.synchronize();

        use(record(CollectionStep.INSERT_CASSETTE), gate(DoorState.OPEN, LockState.UNLOCKED));
        service.synchronize();

        verify(repository, never()).markIncomplete(any(UUID.class), any(String.class));
    }

    @Test
    void waitsForDoorClosure() {
        use(record(CollectionStep.CLOSE_DOOR), gate(DoorState.OPEN, LockState.UNLOCKED));

        service.synchronize();

        verify(repository, never()).changeStep(any(UUID.class), any(CollectionStep.class), any());
    }

    @Test
    void startsSecuringDeadlineAfterDoorClosure() {
        use(record(CollectionStep.CLOSE_DOOR), gate(DoorState.CLOSED, LockState.UNLOCKED));

        service.synchronize();

        verify(repository).changeStep(id, CollectionStep.SECURING, now.plusSeconds(5));
    }

    @Test
    void waitsForLockBeforeDeadline() {
        use(record(CollectionStep.SECURING), gate(DoorState.CLOSED, LockState.UNLOCKED));

        service.synchronize();

        verify(repository, never()).markIncomplete(any(UUID.class), any(String.class));
        verify(repository, never())
                .complete(any(StoredCollection.class), any(Instant.class), anyLong());
    }

    @Test
    void marksLockTimeoutAtDeadline() {
        use(record(CollectionStep.SECURING), gate(DoorState.CLOSED, LockState.UNLOCKED));

        when(clock.instant()).thenReturn(now.plusSeconds(5));

        service.synchronize();

        verify(repository).markIncomplete(id, "LOCK_TIMEOUT");
    }

    @Test
    void completesSecureGateFromCloseDoorAndSecuringSteps() {
        for (CollectionStep step :
                new CollectionStep[] {CollectionStep.CLOSE_DOOR, CollectionStep.SECURING}) {
            StoredCollection collection = record(step);

            use(collection, gate(DoorState.CLOSED, LockState.LOCKED));

            when(repository.cycleTotal(cycleId)).thenReturn(2000L);

            service.synchronize();

            verify(repository).complete(collection, now, 2000L);
            clearInvocations(repository);
        }
    }

    @Test
    void refusesCompletionWithMissingConfirmations() {
        StoredCollection valid = record(CollectionStep.CLOSE_DOOR);

        for (boolean missingRemoval : new boolean[] {true, false}) {
            StoredCollection invalid =
                    new StoredCollection(
                            valid.id(),
                            valid.cashCycleId(),
                            valid.operatorId(),
                            valid.loginId(),
                            valid.runtimeId(),
                            valid.leaseExpiresAt(),
                            valid.step(),
                            valid.startedAt(),
                            missingRemoval ? null : now,
                            null,
                            valid.securingDeadline(),
                            valid.finishedAt(),
                            valid.collectedTotalCents(),
                            valid.errorCode());

            use(invalid, gate(DoorState.CLOSED, LockState.LOCKED));

            assertThrows(IllegalStateException.class, service::synchronize);
        }
    }

    @Test
    void synchronizationDoesNotModifyCompletedCollection() {
        use(record(CollectionStep.COMPLETED), gate(DoorState.CLOSED, LockState.LOCKED));

        service.synchronize();

        verify(repository, never()).changeStep(any(UUID.class), any(CollectionStep.class), any());

        verify(repository, never()).markIncomplete(any(UUID.class), any(String.class));

        verify(repository, never())
                .complete(any(StoredCollection.class), any(Instant.class), anyLong());
    }

    @Test
    void lateLockDoesNotCompleteCollectionFromPreviousApplicationRun() {
        StoredCollection previous =
                ownership(
                        record(CollectionStep.INCOMPLETE),
                        operatorId,
                        loginId,
                        UUID.randomUUID(),
                        cit.expiresAt());

        use(previous, gate(DoorState.CLOSED, LockState.LOCKED));

        service.synchronize();

        assertNoCollectionCompletion();
    }

    @Test
    void lateLockDoesNotCompleteCollectionWithExpiredLease() {
        StoredCollection expired =
                ownership(record(CollectionStep.INCOMPLETE), operatorId, loginId, runtimeId, now);

        use(expired, gate(DoorState.CLOSED, LockState.LOCKED));

        service.synchronize();

        assertNoCollectionCompletion();
    }

    @Test
    void lateLockDoesNotCompleteCollectionForMissingOrInactiveOperator() {
        use(record(CollectionStep.INCOMPLETE), gate(DoorState.CLOSED, LockState.LOCKED));

        when(users.findById(operatorId)).thenReturn(Optional.empty());

        service.synchronize();

        assertNoCollectionCompletion();

        when(users.findById(operatorId))
                .thenReturn(
                        Optional.of(
                                new UserAccount(
                                        operatorId,
                                        "Inactive CIT",
                                        UserRole.CIT,
                                        "unused",
                                        false)));

        service.synchronize();

        assertNoCollectionCompletion();
    }

    @Test
    void lateLockDoesNotCompleteCollectionAfterOperatorRoleChanges() {
        use(record(CollectionStep.INCOMPLETE), gate(DoorState.CLOSED, LockState.LOCKED));

        when(users.findById(operatorId))
                .thenReturn(
                        Optional.of(
                                new UserAccount(
                                        operatorId,
                                        "Changed role",
                                        UserRole.MANAGEMENT,
                                        "unused",
                                        true)));

        service.synchronize();

        assertNoCollectionCompletion();
    }

    @Test
    void lateLockDoesNotCompleteCollectionWhenBoardIsOffline() {
        use(
                record(CollectionStep.INCOMPLETE),
                board(gate(DoorState.CLOSED, LockState.LOCKED), ExtensionBoardState.OFFLINE));

        service.synchronize();

        assertNoCollectionCompletion();
    }

    @Test
    void lateLockDoesNotCompleteCollectionWhileDoorIsOpen() {
        use(record(CollectionStep.INCOMPLETE), gate(DoorState.OPEN, LockState.LOCKED));

        service.synchronize();

        assertNoCollectionCompletion();
    }

    @Test
    void lateLockDoesNotCompleteCollectionWhileLockRemainsUnlocked() {
        use(record(CollectionStep.INCOMPLETE), gate(DoorState.CLOSED, LockState.UNLOCKED));

        service.synchronize();

        assertNoCollectionCompletion();
    }

    @Test
    void lateLockDoesNotResolveOtherCollectionFailures() {
        StoredCollection source = record(CollectionStep.INCOMPLETE);

        StoredCollection hardwareFailure =
                new StoredCollection(
                        source.id(),
                        source.cashCycleId(),
                        source.operatorId(),
                        source.loginId(),
                        source.runtimeId(),
                        source.leaseExpiresAt(),
                        source.step(),
                        source.startedAt(),
                        source.removalConfirmedAt(),
                        source.insertionConfirmedAt(),
                        source.securingDeadline(),
                        source.finishedAt(),
                        source.collectedTotalCents(),
                        "GATE_HARDWARE_UNAVAILABLE");

        use(hardwareFailure, gate(DoorState.CLOSED, LockState.LOCKED));

        service.synchronize();

        assertNoCollectionCompletion();
    }

    @Test
    void lateLockCompletesCollectionWithValidSessionAndSecureGate() {
        StoredCollection collection = record(CollectionStep.INCOMPLETE);

        use(collection, gate(DoorState.CLOSED, LockState.LOCKED));

        when(repository.cycleTotal(cycleId)).thenReturn(2000L);

        service.synchronize();

        verify(repository).complete(collection, now, 2000L);
    }

    private void assertNoCollectionCompletion() {
        verify(repository, never())
                .complete(any(StoredCollection.class), any(Instant.class), anyLong());
    }

    private CollectionService newService(long timeoutMillis) {
        return new CollectionService(repository, deposits, hardware, users, clock, timeoutMillis);
    }

    private void use(StoredCollection collection, HardwareSnapshot snapshot) {
        lenient().when(repository.findUnfinished()).thenReturn(Optional.of(collection));
        lenient().when(repository.findById(id)).thenReturn(Optional.of(collection));
        lenient().when(repository.findByLoginId(loginId)).thenReturn(Optional.of(collection));
        lenient().when(hardware.snapshot()).thenReturn(snapshot);
    }

    private StoredCollection record(CollectionStep step) {
        boolean removalConfirmed =
                step == CollectionStep.INSERT_CASSETTE
                        || step == CollectionStep.CLOSE_DOOR
                        || step == CollectionStep.SECURING
                        || step == CollectionStep.INCOMPLETE
                        || step == CollectionStep.COMPLETED;

        boolean insertionConfirmed =
                step == CollectionStep.CLOSE_DOOR
                        || step == CollectionStep.SECURING
                        || step == CollectionStep.INCOMPLETE
                        || step == CollectionStep.COMPLETED;

        return new StoredCollection(
                id,
                cycleId,
                operatorId,
                loginId,
                runtimeId,
                cit.expiresAt(),
                step,
                now,
                removalConfirmed ? now : null,
                insertionConfirmed ? now : null,
                step == CollectionStep.SECURING ? now.plusSeconds(5) : null,
                step == CollectionStep.COMPLETED ? now : null,
                step == CollectionStep.COMPLETED ? 0L : null,
                step == CollectionStep.INCOMPLETE ? "LOCK_TIMEOUT" : null);
    }

    private static StoredCollection ownership(
            StoredCollection source,
            UUID operatorId,
            UUID loginId,
            UUID runtimeId,
            Instant expiresAt) {
        return new StoredCollection(
                source.id(),
                source.cashCycleId(),
                operatorId,
                loginId,
                runtimeId,
                expiresAt,
                source.step(),
                source.startedAt(),
                source.removalConfirmedAt(),
                source.insertionConfirmedAt(),
                source.securingDeadline(),
                source.finishedAt(),
                source.collectedTotalCents(),
                source.errorCode());
    }

    private static HardwareSnapshot gate(DoorState door, LockState lock) {
        return new HardwareSnapshot(
                AcceptorState.READY,
                CashboxState.PRESENT,
                door,
                lock,
                ExtensionBoardState.ONLINE,
                PrinterState.READY);
    }

    private static HardwareSnapshot board(HardwareSnapshot source, ExtensionBoardState board) {
        return new HardwareSnapshot(
                source.acceptor(),
                source.cashbox(),
                source.door(),
                source.lock(),
                board,
                source.printer());
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, action::run);

        assertEquals(expected, exception.getStatusCode());
    }
}
