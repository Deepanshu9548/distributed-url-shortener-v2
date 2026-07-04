package io.portfolio.urlshortener.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registers the {@link AuthenticatedUserResolver} for controller injection.
 *
 * <p>Self-contained (constructs the resolver directly): {@code @WebMvcTest}
 * slices include every {@code WebMvcConfigurer} in the scan, so this class
 * must not depend on beans the slice doesn't create.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedUserResolver());
    }
}
