package io.portfolio.urlshortener.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <token>}, validates it as an access
 * token (signature, expiry, issuer, {@code typ}), consults the denylist
 * (ADR-009), and — on success — populates the {@link SecurityContextHolder}
 * with a {@link UsernamePasswordAuthenticationToken} whose principal is the
 * user id as a string. That id is exactly what
 * {@link io.portfolio.urlshortener.ratelimit.RateLimitFilter} will read as
 * {@code user:<id>} for rate-limit bucketing.
 *
 * <p>Missing header → no context set; the chain continues so public paths
 * still resolve. Invalid header → 401 JSON written directly (short-circuit).
 *
 * <p>Gated on {@code app.jwt.secret} — same conditional as the rest of the
 * auth stack — so contexts without JWT don't create a filter that would
 * fail to inject its collaborators.
 */
@Component
@ConditionalOnProperty(prefix = "app.jwt", name = "secret")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final DenylistService denylist;

    public JwtAuthenticationFilter(JwtService jwtService, DenylistService denylist) {
        this.jwtService = jwtService;
        this.denylist = denylist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        ParsedToken parsed;
        try {
            parsed = jwtService.parseAccess(token);
        } catch (InvalidTokenException e) {
            write401(response, e.getMessage());
            return;
        }

        if (denylist.isDenied(parsed.jti())) {
            write401(response, "token revoked");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                String.valueOf(parsed.userId()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        request.setAttribute(AuthenticatedUserResolver.ATTRIBUTE,
                new AuthenticatedUser(parsed.userId(), parsed.email(), parsed.jti(), parsed.expiresAt()));

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void write401(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + escape(message) + "\"}");
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
