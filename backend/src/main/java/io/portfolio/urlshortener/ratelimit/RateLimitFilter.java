package io.portfolio.urlshortener.ratelimit;

import io.portfolio.urlshortener.contracts.RateLimiter;
import io.portfolio.urlshortener.contracts.RateLimiter.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Applies the token-bucket rate limiter to inbound HTTP requests.
 *
 * <p><b>Routing table</b> (first match wins):
 * <ul>
 *   <li>{@code /api/auth/**} → limiter {@code auth}</li>
 *   <li>{@code /api/links**} with write method → limiter {@code write}</li>
 *   <li>Single-segment {@code /{code}} with {@code GET} → limiter {@code redirect}
 *       (excludes any api/actuator/swagger prefix)</li>
 *   <li>everything else → not rate limited</li>
 * </ul>
 *
 * <p><b>Subject</b>: authenticated principal ({@code user:<name>}) if the
 * Spring Security context carries a non-anonymous authentication, else
 * {@code ip:<addr>} where {@code addr} is the first {@code X-Forwarded-For}
 * entry (nginx sets it in compose) falling back to {@code remoteAddr}.
 * We trust XFF here — production would validate the trusted proxy list.
 *
 * <p><b>Bucket key</b>: {@code rl:{<subject>}:<limiter>} — the hash-tag braces
 * pin every operation on one bucket to a single Redis Cluster slot (ADR-005).
 *
 * <p><b>Response mapping</b>:
 * <ul>
 *   <li>allowed → chain proceeds.</li>
 *   <li>denied ({@code storeUnavailable=false}) → 429 with {@code Retry-After}
 *       header (ceil seconds, minimum 1) and JSON body.</li>
 *   <li>fail-open store unavailable → chain proceeds.</li>
 *   <li>fail-closed store unavailable → 503 with JSON body.</li>
 * </ul>
 *
 * <p><b>Bean availability</b>: shares {@code ratelimit.enabled=true} with
 * {@link RedisTokenBucketRateLimiter} — turning the flag off drops both beans
 * so the {@code AllowAllRateLimiter} stub runs alone and no filter engages.
 *
 * <p><b>Ordering</b>: no {@code @Order} set — M2 integration owns filter
 * ordering (instance header → request id → rate limit → security).
 */
@Component
@ConditionalOnProperty(prefix = "ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of(
            HttpMethod.POST.name(), HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(), HttpMethod.DELETE.name());

    /** Redirect short-code path — must not shadow api/actuator/swagger routes. */
    private static final Pattern SHORT_CODE_PATH = Pattern.compile("^/[0-9a-zA-Z_\\-]{1,32}$");

    private static final Set<String> RESERVED_PATH_PREFIXES = Set.of(
            "/api/", "/actuator/", "/swagger-ui/", "/v3/api-docs", "/favicon.ico");

    private static final String BODY_DENIED = "{\"error\":\"rate limit exceeded\"}";
    private static final String BODY_UNAVAILABLE = "{\"error\":\"rate limiter unavailable\"}";

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String limiterName = classify(request);
        if (limiterName == null) {
            chain.doFilter(request, response);
            return;
        }

        String subject = subject(request);
        String key = "rl:{" + subject + "}:" + limiterName;

        RateLimitResult decision = limiter.check(key, limiterName);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        if (decision.storeUnavailable()) {
            // fail-closed path (fail-open would have returned allowed=true above).
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, BODY_UNAVAILABLE);
            return;
        }

        long seconds = Math.max(1L, (decision.retryAfterMillis() + 999L) / 1000L);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        write(response, 429, BODY_DENIED);
    }

    /** @return limiter name, or {@code null} to bypass rate limiting. */
    private static String classify(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/auth/")) {
            return "auth";
        }
        if (path.startsWith("/api/links") && WRITE_METHODS.contains(method)) {
            return "write";
        }
        if (HttpMethod.GET.matches(method) && SHORT_CODE_PATH.matcher(path).matches()
                && !hasReservedPrefix(path)) {
            return "redirect";
        }
        return null;
    }

    private static boolean hasReservedPrefix(String path) {
        for (String prefix : RESERVED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String subject(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String name = auth.getName();
            if (name != null && !name.isBlank()) {
                return "user:" + name;
            }
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = (comma < 0 ? xff : xff.substring(0, comma)).trim();
            if (!first.isEmpty()) {
                return "ip:" + first;
            }
        }
        String remote = request.getRemoteAddr();
        return "ip:" + (remote == null ? "unknown" : remote);
    }

    private static void write(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
    }
}
