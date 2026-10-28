package rs.unit.backend.deposit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.deposit.domain.BanknotePolicy.Outcome;
import rs.unit.backend.deposit.infrastructure.persistence.DepositRepository;
import rs.unit.backend.deposit.infrastructure.persistence.DepositRepository.StoredDeposit;
import rs.unit.backend.deposit.infrastructure.persistence.DepositRepository.StoredOperation;
import rs.unit.backend.deposit.infrastructure.persistence.DepositRepository.Totals;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.port.HardwareStatusPort;
import rs.unit.backend.user.domain.UserRole;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock private DepositRepository repository;

    @Mock private HardwareStatusPort hardware;

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final UUID userId = UUID.randomUUID();
    private final UUID loginId = UUID.randomUUID();

    private AuthenticatedUser cashier;
    private DepositService service;
    private StoredDeposit activeDeposit;
    private UUID runtimeId;

    @BeforeEach
    void setUp() {
        cashier =
                new AuthenticatedUser(
                        userId, "Test Cashier", UserRole.CASHIER, now.plusSeconds(900), loginId);

        service = new DepositService(repository, hardware, Clock.fixed(now, ZoneOffset.UTC));

        service.start(cashier);

        var depositIdCaptor = ArgumentCaptor.forClass(UUID.class);
        var runtimeIdCaptor = ArgumentCaptor.forClass(UUID.class);

        verify(repository)
                .create(
                        depositIdCaptor.capture(),
                        eq(userId),
                        eq(loginId),
                        runtimeIdCaptor.capture(),
                        eq(now),
                        eq(cashier.expiresAt()));

        runtimeId = runtimeIdCaptor.getValue();

        activeDeposit =
                new StoredDeposit(
                        depositIdCaptor.getValue(),
                        userId,
                        loginId,
                        runtimeId,
                        "ACTIVE",
                        now,
                        cashier.expiresAt(),
                        null);

        clearInvocations(repository);

        lenient().when(repository.findByLoginId(loginId)).thenReturn(Optional.of(activeDeposit));

        lenient().when(repository.totals(activeDeposit.id())).thenReturn(new Totals(0, 0));

        lenient().when(hardware.snapshot()).thenReturn(readyHardware());

        lenient().when(repository.hasValidSession(loginId, runtimeId, now)).thenReturn(true);
    }

    @Test
    void startsDepositAfterClosingUnavailableDeposits() {
        service.start(cashier);

        verify(repository).lockMachine();
        verify(repository).closeUnavailableDeposits(runtimeId, now);
        verify(repository)
                .create(
                        any(UUID.class),
                        eq(userId),
                        eq(loginId),
                        eq(runtimeId),
                        eq(now),
                        eq(cashier.expiresAt()));
    }

    @Test
    void rejectsStartWhenAnotherDepositIsActive() {
        when(repository.hasActiveDeposit()).thenReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> service.start(cashier));

        verify(repository, never())
                .create(
                        any(UUID.class),
                        any(UUID.class),
                        any(UUID.class),
                        any(UUID.class),
                        any(Instant.class),
                        any(Instant.class));
    }

    @Test
    void rejectsNonCashierAccess() {
        for (UserRole role :
                new UserRole[] {UserRole.MANAGEMENT, UserRole.MAINTENANCE, UserRole.CIT}) {
            var user = new AuthenticatedUser(userId, "Test User", role, cashier.expiresAt());

            assertStatus(HttpStatus.FORBIDDEN, () -> service.start(user));
            assertStatus(HttpStatus.FORBIDDEN, () -> service.current(user));
            assertStatus(
                    HttpStatus.FORBIDDEN, () -> service.insert(user, UUID.randomUUID(), "EUR", 20));
        }

        verify(repository, never()).lockMachine();
    }

    @Test
    void rejectsExpiredSessionForStartReadAndInsert() {
        var expired = new AuthenticatedUser(userId, "Test Cashier", UserRole.CASHIER, now, loginId);

        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.start(expired));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.current(expired));
        assertStatus(
                HttpStatus.UNAUTHORIZED,
                () -> service.insert(expired, UUID.randomUUID(), "EUR", 20));
    }

    @Test
    void returnsCurrentDepositWithPersistedTotals() {
        when(repository.totals(activeDeposit.id())).thenReturn(new Totals(7000, 2));

        DepositView result = service.current(cashier);

        assertEquals(activeDeposit.id(), result.id());
        assertEquals("ACTIVE", result.status());
        assertEquals("EUR", result.currency());
        assertEquals(7000, result.totalCents());
        assertEquals(2, result.banknoteCount());
        assertTrue(result.acceptingEnabled());
    }

    @Test
    void rejectsAccessWhenDepositIsMissing() {
        when(repository.findByLoginId(loginId)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.CONFLICT, () -> service.current(cashier));
    }

    @Test
    void rejectsDepositOwnedByAnotherUser() {
        when(repository.findByLoginId(loginId))
                .thenReturn(
                        Optional.of(
                                deposit(
                                        UUID.randomUUID(),
                                        runtimeId,
                                        "ACTIVE",
                                        cashier.expiresAt(),
                                        null)));

        assertStatus(HttpStatus.FORBIDDEN, () -> service.current(cashier));
    }

    @Test
    void rejectsDepositFromPreviousApplicationRun() {
        when(repository.findByLoginId(loginId))
                .thenReturn(
                        Optional.of(
                                deposit(
                                        userId,
                                        UUID.randomUUID(),
                                        "ACTIVE",
                                        cashier.expiresAt(),
                                        null)));

        assertStatus(HttpStatus.CONFLICT, () -> service.current(cashier));
    }

    @Test
    void completedDepositDoesNotEnableAcceptance() {
        when(repository.findByLoginId(loginId))
                .thenReturn(
                        Optional.of(
                                deposit(userId, runtimeId, "COMPLETED", cashier.expiresAt(), now)));

        DepositView result = service.current(cashier);

        assertFalse(result.acceptingEnabled());
        assertEquals(now, result.finishedAt());
    }

    @Test
    void expiredDepositDoesNotEnableAcceptance() {
        when(repository.findByLoginId(loginId))
                .thenReturn(Optional.of(deposit(userId, runtimeId, "ACTIVE", now, null)));

        assertFalse(service.current(cashier).acceptingEnabled());
    }

    @Test
    void unavailableHardwareDisablesAcceptanceAndRejectsInsertion() {
        when(hardware.snapshot()).thenReturn(blockedHardware());

        assertFalse(service.current(cashier).acceptingEnabled());

        assertStatus(
                HttpStatus.CONFLICT, () -> service.insert(cashier, UUID.randomUUID(), "EUR", 20));

        verify(repository, never())
                .recordOperation(
                        any(UUID.class),
                        any(UUID.class),
                        anyString(),
                        anyInt(),
                        any(Outcome.class),
                        any(Instant.class));
    }

    @Test
    void recordsAcceptedBanknote() {
        UUID operationId = UUID.randomUUID();
        when(repository.totals(activeDeposit.id())).thenReturn(new Totals(2000, 1));

        BanknoteResult result = service.insert(cashier, operationId, "EUR", 20);

        assertEquals(operationId, result.operationId());
        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertFalse(result.replayed());
        assertEquals(2000, result.deposit().totalCents());

        verify(repository)
                .recordOperation(operationId, activeDeposit.id(), "EUR", 20, Outcome.ACCEPTED, now);
    }

    @Test
    void returnsPreviousOperationWithoutRecordingItAgain() {
        UUID operationId = UUID.randomUUID();

        when(repository.findOperation(operationId))
                .thenReturn(
                        Optional.of(
                                new StoredOperation(
                                        operationId,
                                        activeDeposit.id(),
                                        "EUR",
                                        20,
                                        Outcome.ACCEPTED)));

        BanknoteResult result = service.insert(cashier, operationId, "EUR", 20);

        assertTrue(result.replayed());
        assertEquals(Outcome.ACCEPTED, result.outcome());

        verify(repository, never())
                .recordOperation(
                        any(UUID.class),
                        any(UUID.class),
                        anyString(),
                        anyInt(),
                        any(Outcome.class),
                        any(Instant.class));
    }

    @Test
    void rejectsOperationIdentifierFromAnotherDeposit() {
        UUID operationId = UUID.randomUUID();

        when(repository.findOperation(operationId))
                .thenReturn(
                        Optional.of(
                                new StoredOperation(
                                        operationId,
                                        UUID.randomUUID(),
                                        "EUR",
                                        20,
                                        Outcome.ACCEPTED)));

        assertStatus(HttpStatus.CONFLICT, () -> service.insert(cashier, operationId, "EUR", 20));
    }

    @Test
    void rejectsOperationIdentifierWithDifferentCurrency() {
        UUID operationId = UUID.randomUUID();

        when(repository.findOperation(operationId))
                .thenReturn(
                        Optional.of(
                                new StoredOperation(
                                        operationId,
                                        activeDeposit.id(),
                                        "EUR",
                                        20,
                                        Outcome.ACCEPTED)));

        assertStatus(HttpStatus.CONFLICT, () -> service.insert(cashier, operationId, "USD", 20));
    }

    @Test
    void rejectsOperationIdentifierWithDifferentDenomination() {
        UUID operationId = UUID.randomUUID();

        when(repository.findOperation(operationId))
                .thenReturn(
                        Optional.of(
                                new StoredOperation(
                                        operationId,
                                        activeDeposit.id(),
                                        "EUR",
                                        20,
                                        Outcome.ACCEPTED)));

        assertStatus(HttpStatus.CONFLICT, () -> service.insert(cashier, operationId, "EUR", 50));
    }

    @Test
    void rejectsNewBanknoteAfterDepositCompletion() {
        when(repository.findByLoginId(loginId))
                .thenReturn(
                        Optional.of(
                                deposit(userId, runtimeId, "COMPLETED", cashier.expiresAt(), now)));

        assertStatus(
                HttpStatus.CONFLICT, () -> service.insert(cashier, UUID.randomUUID(), "EUR", 20));
    }

    @Test
    void rejectsInvalidBanknoteArguments() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.insert(cashier, null, "EUR", 20));

        assertStatus(
                HttpStatus.BAD_REQUEST, () -> service.insert(cashier, UUID.randomUUID(), null, 20));

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.insert(cashier, UUID.randomUUID(), "eur", 20));

        assertStatus(
                HttpStatus.BAD_REQUEST, () -> service.insert(cashier, UUID.randomUUID(), "EUR", 0));

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.insert(cashier, UUID.randomUUID(), "EUR", -20));

        verify(repository, never()).lockMachine();
    }

    @Test
    void finishesActiveDeposit() {
        service.finish(cashier);

        verify(repository).lockMachine();
        verify(repository).complete(activeDeposit.id(), now);
    }

    @Test
    void finishingCompletedDepositDoesNotModifyIt() {
        when(repository.findByLoginId(loginId))
                .thenReturn(
                        Optional.of(
                                deposit(userId, runtimeId, "COMPLETED", cashier.expiresAt(), now)));

        service.finish(cashier);

        verify(repository, never()).complete(any(UUID.class), any(Instant.class));
    }

    @Test
    void finishingNonCashierSessionDoesNotAccessDeposits() {
        var manager =
                new AuthenticatedUser(
                        userId, "Test Manager", UserRole.MANAGEMENT, cashier.expiresAt());

        service.finish(manager);

        verify(repository, never()).lockMachine();
        verify(repository, never()).findByLoginId(any(UUID.class));
    }

    @Test
    void rejectsStartingDepositDuringUnfinishedCollection() {
        when(repository.hasUnfinishedCollection()).thenReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> service.start(cashier));
    }

    @Test
    void rejectsNewBanknoteDuringUnfinishedCollection() {
        when(repository.hasUnfinishedCollection()).thenReturn(true);

        assertStatus(
                HttpStatus.CONFLICT, () -> service.insert(cashier, UUID.randomUUID(), "EUR", 20));

        verify(repository, never())
                .recordOperation(
                        any(UUID.class),
                        any(UUID.class),
                        anyString(),
                        anyInt(),
                        any(Outcome.class),
                        any(Instant.class));
    }

    @Test
    void unfinishedCollectionDisablesBanknoteAcceptance() {
        when(repository.hasUnfinishedCollection()).thenReturn(true);

        assertFalse(service.current(cashier).acceptingEnabled());
    }

    @Test
    void inactiveSessionCannotReadDepositOrInsertBanknotes() {
        when(repository.hasValidSession(loginId, runtimeId, now)).thenReturn(false);

        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.current(cashier));

        assertStatus(
                HttpStatus.UNAUTHORIZED,
                () -> service.insert(cashier, UUID.randomUUID(), "EUR", 20));

        verify(repository, never())
                .recordOperation(
                        any(UUID.class),
                        any(UUID.class),
                        anyString(),
                        anyInt(),
                        any(Outcome.class),
                        any(Instant.class));
    }

    @Test
    void sessionIsInactiveAtAbsoluteExpirationWithoutCheckingDepositValidity() {
        var expired = new AuthenticatedUser(userId, "Test Cashier", UserRole.CASHIER, now, loginId);

        assertFalse(service.isSessionActive(expired));

        verify(repository).closeUnavailableDeposits(runtimeId, now);
        verify(repository).closeDepositsOfUnavailableAccounts(now);

        verify(repository, never())
                .hasValidSession(any(UUID.class), any(UUID.class), any(Instant.class));
    }

    private StoredDeposit deposit(
            UUID ownerId,
            UUID applicationRuntimeId,
            String status,
            Instant expiresAt,
            Instant finishedAt) {
        return new StoredDeposit(
                activeDeposit.id(),
                ownerId,
                loginId,
                applicationRuntimeId,
                status,
                now,
                expiresAt,
                finishedAt);
    }

    private static HardwareSnapshot readyHardware() {
        return new HardwareSnapshot(
                AcceptorState.READY,
                CashboxState.PRESENT,
                DoorState.CLOSED,
                LockState.LOCKED,
                ExtensionBoardState.ONLINE,
                PrinterState.READY);
    }

    private static HardwareSnapshot blockedHardware() {
        return new HardwareSnapshot(
                AcceptorState.READY,
                CashboxState.ABSENT,
                DoorState.CLOSED,
                LockState.LOCKED,
                ExtensionBoardState.ONLINE,
                PrinterState.READY);
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, action::run);

        assertEquals(expected, exception.getStatusCode());
    }
}
