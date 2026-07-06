package io.portfolio.urlshortener.auth;

import io.portfolio.urlshortener.ratelimit.RateLimitFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

/**
 * Stateless JWT security (ADR-009).
 *
 * <p><b>Public paths</b> — regardless of authentication:
 * <ul>
 *   <li>single-segment {@code GET /{code}} matching
 *       {@code /[0-9a-zA-Z_-]{1,32}} (the redirect route);</li>
 *   <li>{@code POST /api/auth/register|login|refresh};</li>
 *   <li>{@code GET /api/links/{code}} (public metadata read);</li>
 *   <li>{@code /actuator/health|prometheus|info},
 *       {@code /swagger-ui/**}, {@code /v3/api-docs/**}.</li>
 * </ul>
 *
 * <p><b>Filter order</b>: {@code RateLimitFilter} (M1-E, if present) →
 * {@link JwtAuthenticationFilter} → {@code UsernamePasswordAuthenticationFilter}.
 * RateLimitFilter's servlet-container auto-registration is disabled by
 * {@link FilterRegistrationConfig} so it only runs here — no double-execution.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "app.jwt", name = "secret")
public class SecurityConfig {

    /** Single-segment short-code path used by the redirect controller. */
    private static final String REDIRECT_PATTERN = "^/[0-9a-zA-Z_\\-]{1,32}$";

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength 10 is the Spring default. Tests can inject a strength-4 encoder for speed.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   RestAuthEntryPoint entryPoint,
                                                   RestAccessDeniedHandler deniedHandler,
                                                   ObjectProvider<RateLimitFilter> rateLimitFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Actuator + docs
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                "/favicon.ico").permitAll()
                        // Public auth endpoints
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        // Public link metadata read (README says so)
                        .requestMatchers(HttpMethod.GET, "/api/links/*").permitAll()
                        // Public link creation (anonymous shortening)
                        .requestMatchers(HttpMethod.POST, "/api/links").permitAll()
                        // Redirect route — single-segment short code, regex-guarded
                        .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, REDIRECT_PATTERN)).permitAll()
                        // Everything else under /api requires auth
                        .requestMatchers("/api/**").authenticated()
                        // Anything else public (nothing meaningful matches, but be permissive by default)
                        .anyRequest().permitAll());

        // JWT filter runs before Spring Security's default username/password filter.
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        // If M1-E is active, insert the rate-limit filter BEFORE the JWT filter so
        // an unauthenticated login attempt is still bucketed by IP.
        RateLimitFilter rl = rateLimitFilter.getIfAvailable();
        if (rl != null) {
            http.addFilterBefore(rl, JwtAuthenticationFilter.class);
        }

        return http.build();
    }
}
