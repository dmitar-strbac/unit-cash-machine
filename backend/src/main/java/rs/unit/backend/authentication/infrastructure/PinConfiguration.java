package rs.unit.backend.authentication.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PinConfiguration {

    @Bean
    public PasswordEncoder pinPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
