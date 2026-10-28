package rs.unit.backend.collection.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.authentication.application.AuthenticationService;
import rs.unit.backend.authentication.application.LoginAttemptLimiter;
import rs.unit.backend.authentication.application.PinService;
import rs.unit.backend.collection.domain.CollectionStep;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.model.PhysicalAction;
import rs.unit.backend.hardware.simulator.SimulatedHardware;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccount;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@SpringBootTest(properties = "unit.collection.monitor.enabled=false")
@ActiveProfiles("simulation")
@Transactional
class CollectionIntegrationTest {

    @Autowired private AuthenticationService authentication;
    @Autowired private CollectionService collections;
    @Autowired private CollectionRepository collectionRepository;
    @Autowired private DepositService deposits;
    @Autowired private SimulatedHardware hardware;
    @Autowired private UserAccountRepository users;
    @Autowired private PinService pins;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @MockitoBean private Clock clock;
    @MockitoBean private LoginAttemptLimiter limiter;

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    private UUID cashierId;
    private UUID citId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);

        jdbc.update("DELETE FROM cash_machine.cash_collection_event");
        jdbc.update("DELETE FROM cash_machine.cash_collection");
        jdbc.update("DELETE FROM cash_machine.deposit_banknote_operation");
        jdbc.update("DELETE FROM cash_machine.deposit");
        jdbc.update("DELETE FROM cash_machine.cash_cycle");

        jdbc.update(
                """
                INSERT INTO cash_machine.cash_cycle (
                    id,
                    machine_id,
                    opened_at
                )
                VALUES (?, 1, ?)
                """,
                UUID.randomUUID(),
                java.sql.Timestamp.from(now.minusSeconds(60)));

        jdbc.update("UPDATE cash_machine.user_account SET active = FALSE");

        cashierId = save(UserRole.CASHIER, "1234");
        citId = save(UserRole.CIT, "5678");

        hardware.replaceState(
                new HardwareSnapshot(
                        AcceptorState.READY,
                        CashboxState.PRESENT,
                        DoorState.CLOSED,
                        LockState.LOCKED,
                        ExtensionBoardState.ONLINE,
                        PrinterState.READY));
    }

    @Test
    void completesCollectionWithManualCassetteConfirmations() {
        AuthenticatedUser cashier = authentication.login(cashierId, UserRole.CASHIER, "1234");

        deposits.insert(cashier, UUID.randomUUID(), "EUR", 20);
        deposits.finish(cashier);

        AuthenticatedUser cit = authentication.loginCit("5678");

        CollectionView current = collections.current(cit);

        assertEquals(CollectionStep.OPEN_DOOR, current.step());
        assertEquals(LockState.UNLOCKED, hardware.snapshot().lock());

        collections.simulate(cit, current.id(), PhysicalAction.OPEN_DOOR);
        collections.synchronize();

        assertEquals(CollectionStep.REMOVE_CASSETTE, collections.current(cit).step());

        collections.confirmRemoval(cit, current.id());

        assertEquals(
                CollectionStep.INSERT_CASSETTE,
                collections.confirmRemoval(cit, current.id()).step());

        assertEquals(CashboxState.PRESENT, hardware.snapshot().cashbox());

        collections.confirmInsertion(cit, current.id());

        assertEquals(
                CollectionStep.CLOSE_DOOR, collections.confirmInsertion(cit, current.id()).step());

        collections.simulate(cit, current.id(), PhysicalAction.CLOSE_DOOR);
        collections.synchronize();

        assertEquals(CollectionStep.SECURING, collections.current(cit).step());

        collections.simulate(cit, current.id(), PhysicalAction.LOCK_DOOR);
        collections.synchronize();

        CollectionView completed = collections.current(cit);

        assertEquals(CollectionStep.COMPLETED, completed.step());
        assertEquals(2000L, completed.collectedTotalCents());
        assertTrue(completed.doorClosed());
        assertTrue(completed.doorLocked());

        assertEquals(0L, collectionRepository.cycleTotal(collectionRepository.currentCycleId()));

        Long dailyTotal =
                jdbc.queryForObject(
                        """
                        SELECT SUM(denomination::bigint * 100)
                        FROM cash_machine.deposit_banknote_operation
                        WHERE outcome = 'ACCEPTED'
                            AND recorded_at >= ?
                            AND recorded_at < ?
                        """,
                        Long.class,
                        java.sql.Timestamp.from(Instant.parse("2025-12-31T23:00:00Z")),
                        java.sql.Timestamp.from(Instant.parse("2026-01-01T23:00:00Z")));

        assertEquals(2000L, dailyTotal);

        collections.confirmInsertion(cit, current.id());
        collections.synchronize();

        Long cycles =
                jdbc.queryForObject("SELECT COUNT(*) FROM cash_machine.cash_cycle", Long.class);

        assertEquals(2L, cycles);
    }

    @Test
    void waitsForDoorClosureWithoutStartingSecuringTimeout() {
        AuthenticatedUser cit = authentication.loginCit("5678");
        UUID id = reachCloseDoor(cit);

        when(clock.instant()).thenReturn(now.plusSeconds(60));

        collections.synchronize();

        assertEquals(CollectionStep.CLOSE_DOOR, collections.current(cit).step());
        assertEquals(null, collections.current(cit).securingDeadline());
    }

    @Test
    void requiresReauthenticationAfterLockTimeout() {
        AuthenticatedUser cit = authentication.loginCit("5678");
        UUID id = reachCloseDoor(cit);

        collections.simulate(cit, id, PhysicalAction.CLOSE_DOOR);
        collections.synchronize();

        when(clock.instant()).thenReturn(now.plusSeconds(5));

        collections.synchronize();

        CollectionView incomplete = collections.current(cit);

        assertEquals(CollectionStep.INCOMPLETE, incomplete.step());
        assertEquals("LOCK_TIMEOUT", incomplete.errorCode());

        collections.simulate(cit, id, PhysicalAction.LOCK_DOOR);
        collections.synchronize();

        assertEquals(CollectionStep.INCOMPLETE, collections.current(cit).step());

        collections.restart(cit, id);

        AuthenticatedUser resumed = authentication.loginCit("5678");

        assertEquals(id, collections.current(resumed).id());

        collections.synchronize();

        assertEquals(CollectionStep.COMPLETED, collections.current(resumed).step());
    }

    @Test
    void rejectsCitWhileDepositIsActive() {
        authentication.login(cashierId, UserRole.CASHIER, "1234");

        assertStatus(HttpStatus.CONFLICT, () -> authentication.loginCit("5678"));

        assertEquals(LockState.LOCKED, hardware.snapshot().lock());
        assertTrue(collectionRepository.findUnfinished().isEmpty());
    }

    @Test
    void rejectsDepositWhileCollectionIsActive() {
        authentication.loginCit("5678");

        assertStatus(
                HttpStatus.CONFLICT,
                () -> authentication.login(cashierId, UserRole.CASHIER, "1234"));
    }

    @Test
    void rejectsSkippingCassetteConfirmationSteps() {
        AuthenticatedUser cit = authentication.loginCit("5678");
        UUID id = collections.current(cit).id();

        assertStatus(HttpStatus.CONFLICT, () -> collections.confirmInsertion(cit, id));
    }

    @Test
    void expiredCitSessionLeavesCollectionBlockedForRecovery() {
        AuthenticatedUser cit = authentication.loginCit("5678");

        when(clock.instant()).thenReturn(now.plusSeconds(900));

        collections.synchronize();

        assertStatus(HttpStatus.UNAUTHORIZED, () -> collections.current(cit));

        var stored = collectionRepository.findUnfinished().orElseThrow();

        assertEquals(CollectionStep.INCOMPLETE, stored.step());
        assertEquals("SESSION_EXPIRED", stored.errorCode());
    }

    @Test
    void marksCollectionIncompleteWhenLockFails() {
        AuthenticatedUser cit = authentication.loginCit("5678");

        HardwareSnapshot before = hardware.snapshot();

        hardware.replaceState(
                new HardwareSnapshot(
                        before.acceptor(),
                        before.cashbox(),
                        before.door(),
                        LockState.FAULT,
                        before.extensionBoard(),
                        before.printer()));

        collections.synchronize();

        CollectionView result = collections.current(cit);

        assertEquals(CollectionStep.INCOMPLETE, result.step());
        assertEquals("GATE_HARDWARE_UNAVAILABLE", result.errorCode());
    }

    private UUID reachCloseDoor(AuthenticatedUser cit) {
        UUID id = collections.current(cit).id();

        collections.simulate(cit, id, PhysicalAction.OPEN_DOOR);
        collections.synchronize();

        collections.confirmRemoval(cit, id);
        collections.confirmInsertion(cit, id);

        return id;
    }

    private UUID save(UserRole role, String pin) {
        UUID id = UUID.randomUUID();

        users.save(new UserAccount(id, "Test " + role, role, pins.encode(pin), true));

        entityManager.flush();

        return id;
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, action::run);

        assertEquals(expected, exception.getStatusCode());
    }
}
