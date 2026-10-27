package rs.unit.backend.deposit.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DepositRepositoryTest {

    @Mock private JdbcTemplate jdbc;

    @Test
    void rejectsNullMachineIdentifier() {
        when(jdbc.queryForObject(anyString(), eq(Short.class))).thenReturn(null);

        var repository = new DepositRepository(jdbc);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, repository::lockMachine);

        assertEquals("Machine configuration is missing.", exception.getMessage());
    }
}
