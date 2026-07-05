package io.portfolio.urlshortener.ratelimit;

import io.portfolio.urlshortener.contracts.RateLimiter;
import io.portfolio.urlshortener.contracts.RateLimiter.RateLimitResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RateLimiter limiter;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        limiter = mock(RateLimiter.class);
        filter = new RateLimitFilter(limiter);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // --- routing table ------------------------------------------------------

    @Test
    void postApiLinks_usesWriteLimiter_withXffSubject() throws Exception {
        when(limiter.check(anyString(), anyString())).thenReturn(RateLimitResult.allowedResult());

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/links");
        req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(limiter).check(key.capture(), name.capture());
        assertThat(name.getValue()).isEqualTo("write");
        assertThat(key.getValue()).isEqualTo("rl:{ip:203.0.113.1}:write");
        assertThat(chain.getRequest()).isNotNull(); // chain proceeded
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void getRedirect_deniedProduces429WithRetryAfter() throws Exception {
        when(limiter.check(anyString(), anyString()))
                .thenReturn(RateLimitResult.denied(1200));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/abc123");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(limiter).check("rl:{ip:1.2.3.4}:redirect", "redirect");
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("2");
        assertThat(res.getContentAsString()).contains("rate limit exceeded");
        assertThat(res.getContentType()).contains("application/json");
        assertThat(chain.getRequest()).isNull(); // chain did NOT proceed
    }

    @Test
    void getApiLinksMetadata_isNotRateLimited() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/links/abc123");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(limiter);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void postApiAuth_usesAuthLimiter_and250msRoundsToRetryAfter1() throws Exception {
        when(limiter.check(anyString(), anyString()))
                .thenReturn(RateLimitResult.denied(250));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(limiter).check("rl:{ip:9.9.9.9}:auth", "auth");
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void actuatorHealth_isNotRateLimited() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(limiter);
    }

    @Test
    void swaggerAndApiDocs_areNotRateLimited() throws Exception {
        for (String path : List.of("/swagger-ui/index.html", "/v3/api-docs", "/favicon.ico")) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
        }
        verifyNoInteractions(limiter);
    }

    @Test
    void deeperShortCodePath_isNotRedirect() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/abc/deeper");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(limiter);
    }

    // --- degradation --------------------------------------------------------

    @Test
    void failOpen_storeUnavailable_allowsThrough() throws Exception {
        when(limiter.check(anyString(), anyString())).thenReturn(RateLimitResult.failOpen());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/abc123");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void failClosed_storeUnavailable_returns503() throws Exception {
        when(limiter.check(anyString(), anyString())).thenReturn(RateLimitResult.failClosed());

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/links");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getContentAsString()).contains("rate limiter unavailable");
        assertThat(res.getContentType()).contains("application/json");
        assertThat(res.getHeader("Retry-After")).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    // --- subject extraction -------------------------------------------------

    @Test
    void authenticatedPrincipal_wins_overIp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(limiter.check(anyString(), anyString())).thenReturn(RateLimitResult.allowedResult());

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/links");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(limiter).check("rl:{user:alice}:write", "write");
    }

    @Test
    void blankXff_fallsBackToRemoteAddr() throws Exception {
        when(limiter.check(anyString(), anyString())).thenReturn(RateLimitResult.allowedResult());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/abc123");
        req.addHeader("X-Forwarded-For", "");
        req.setRemoteAddr("7.7.7.7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(limiter).check("rl:{ip:7.7.7.7}:redirect", "redirect");
    }

    @Test
    void allowedFast_chainProceedsAndLimiterCalledOnce() throws Exception {
        when(limiter.check(anyString(), anyString())).thenReturn(RateLimitResult.allowedResult());

        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/api/links/abc");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(limiter, times(1)).check("rl:{ip:1.2.3.4}:write", "write");
        verifyNoMoreInteractions(limiter);
    }
}
