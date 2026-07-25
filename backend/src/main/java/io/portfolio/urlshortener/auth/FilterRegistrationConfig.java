package io.portfolio.urlshortener.auth;

import io.portfolio.urlshortener.ratelimit.RateLimitFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two Spring-Boot-managed filters ({@link RateLimitFilter} and
 * {@link JwtAuthenticationFilter}) would otherwise be auto-registered by the
 * servlet container AND wired into the Spring Security chain from
 * {@link SecurityConfig} — running each request through them twice.
 *
 * <p>We disable the auto-registration for both. They run exclusively inside
 * the security filter chain in the correct order (rate-limit → JWT).
 */
@Configuration
public class FilterRegistrationConfig {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RateLimitFilter.class)
    public FilterRegistrationBean<RateLimitFilter> disableRateLimitAutoRegistration(
            RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>();
        RateLimitFilter f = rateLimitFilter;
        if (f != null) {
            reg.setFilter(f);
        }
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtAuthFilterAutoRegistration(
            JwtAuthenticationFilter jwtFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(jwtFilter);
        reg.setEnabled(false);
        return reg;
    }
}
