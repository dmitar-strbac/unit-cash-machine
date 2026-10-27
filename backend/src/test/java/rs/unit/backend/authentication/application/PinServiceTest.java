package rs.unit.backend.authentication.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PinServiceTest {

    private final PinService service = new PinService(new BCryptPasswordEncoder(4));

    @ParameterizedTest
    @ValueSource(strings = {"0123", "123456789012"})
    void encodedPinMatchesOriginalValue(String pin) {
        String encoded = service.encode(pin);

        assertNotEquals(pin, encoded);
        assertTrue(service.matches(pin, encoded));
        assertFalse(service.matches("9999", encoded));
    }

    @Test
    void samePinProducesDifferentSaltedHashes() {
        String first = service.encode("1234");
        String second = service.encode("1234");

        assertNotEquals(first, second);
        assertTrue(service.matches("1234", first));
        assertTrue(service.matches("1234", second));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "123", "1234567890123", "12a4", " 1234", "1234 "})
    void invalidPinCannotBeEncodedOrMatched(String pin) {
        String encoded = service.encode("1234");

        assertThrows(IllegalArgumentException.class, () -> service.encode(pin));
        assertFalse(service.matches(pin, encoded));
    }
}
