package com.cardbilling.billing.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for this resource server.
 *
 * <p>The default configuration - the one that applies in production and whenever no profile says
 * otherwise - requires a valid Bearer token issued by Keycloak on every endpoint except the
 * health probe and the API docs.
 *
 * <p>The {@code local} profile below replaces that with anonymous access, so the service can be
 * driven with curl during development without standing Keycloak up first. It is an explicit
 * opt-in: nothing enables it implicitly, and running without a profile gets the secured chain.
 * The integration tests use it deliberately, to test routing and business behaviour separately
 * from token validation.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Profile("!local")
    SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .build();
    }

    /**
     * Local development only. Never active unless the {@code local} profile is explicitly asked
     * for, and it is what keeps the secured chain above the default rather than the exception.
     */
    @Bean
    @Profile("local")
    SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
