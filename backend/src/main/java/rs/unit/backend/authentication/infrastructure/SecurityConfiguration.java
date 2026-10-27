package rs.unit.backend.authentication.infrastructure;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import rs.unit.backend.deposit.infrastructure.DepositLogoutHandler;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfRepository,
            Clock clock,
            UserAccountRepository userRepository,
            DepositLogoutHandler depositLogoutHandler)
            throws Exception {

        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfRepository)
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(
                        requests ->
                                requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/actuator/health",
                                                "/api/machine/status",
                                                "/api/users",
                                                "/api/auth/csrf")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/login",
                                                "/api/auth/cit-login")
                                        .permitAll()
                                        .requestMatchers("/api/auth/me")
                                        .authenticated()
                                        .requestMatchers("/api/simulation/**")
                                        .hasRole("MAINTENANCE")
                                        .requestMatchers(HttpMethod.GET, "/api/deposits/current")
                                        .hasRole("CASHIER")
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/deposits/current/banknotes")
                                        .hasRole("CASHIER")
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, exception) -> {
                                                    response.setStatus(401);
                                                    response.setContentType(
                                                            "application/problem+json");
                                                    response.getWriter()
                                                            .write(
                                                                    """
                                                                    {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Authentication required."}
                                                                    """);
                                                })
                                        .accessDeniedHandler(
                                                (request, response, exception) -> {
                                                    response.setStatus(403);
                                                    response.setContentType(
                                                            "application/problem+json");
                                                    response.getWriter()
                                                            .write(
                                                                    """
                                                                    {"type":"about:blank","title":"Forbidden","status":403,"detail":"Access denied or invalid CSRF token."}
                                                                    """);
                                                }))
                .logout(
                        logout ->
                                logout.logoutUrl("/api/auth/logout")
                                        .addLogoutHandler(depositLogoutHandler)
                                        .invalidateHttpSession(true)
                                        .clearAuthentication(true)
                                        .deleteCookies("UNIT_SESSION")
                                        .logoutSuccessHandler(
                                                (request, response, authentication) ->
                                                        response.setStatus(204)));

        http.addFilterAfter(
                new SessionValidityFilter(clock, userRepository),
                SecurityContextHolderFilter.class);

        return http.build();
    }
}
