package rs.unit.backend.authentication.application;

import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PinService {

    private static final Pattern PIN_PATTERN = Pattern.compile("[0-9]{4,12}");

    private final PasswordEncoder passwordEncoder;

    public PinService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String encode(String pin) {
        if (!isValid(pin)) {
            throw new IllegalArgumentException("PIN must contain 4 to 12 digits.");
        }

        return passwordEncoder.encode(pin);
    }

    public boolean matches(String pin, String encodedPin) {
        return isValid(pin) && passwordEncoder.matches(pin, encodedPin);
    }

    private boolean isValid(String pin) {
        return pin != null && PIN_PATTERN.matcher(pin).matches();
    }
}
