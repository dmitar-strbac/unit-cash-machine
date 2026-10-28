package rs.unit.backend.collection.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository.StoredCollection;

@ExtendWith(MockitoExtension.class)
class CollectionRepositoryTest {

    @Mock private JdbcTemplate jdbc;

    @Test
    void returnsZeroWhenJdbcReturnsNullTotal() {
        UUID cycleId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(cycleId))).thenReturn(null);

        CollectionRepository repository = new CollectionRepository(jdbc);

        assertEquals(0, repository.cycleTotal(cycleId));
    }

    @Test
    void rejectsCompletingAlreadyClosedCashCycle() {
        StoredCollection collection = mock(StoredCollection.class);

        when(collection.cashCycleId()).thenReturn(UUID.randomUUID());

        CollectionRepository repository = new CollectionRepository(jdbc);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                repository.complete(
                                        collection, Instant.parse("2026-01-01T12:00:00Z"), 2000));

        assertEquals("The cash cycle has already been closed.", exception.getMessage());
    }
}
