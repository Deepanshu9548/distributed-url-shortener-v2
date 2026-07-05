package io.portfolio.urlshortener.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tags every response with the serving instance so round-robin load balancing
 * (and therefore statelessness) is externally observable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceHeaderFilter extends OncePerRequestFilter {

    @Value("${app.node-id}")
    private String nodeId;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Instance", nodeId);
        filterChain.doFilter(request, response);
    }
}
