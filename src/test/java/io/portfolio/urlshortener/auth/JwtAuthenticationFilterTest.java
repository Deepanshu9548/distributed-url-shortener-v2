package io.portfolio.urlshortener.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private DenylistService denylist;
    private JwtAuthenticationFilter filter;

    /** Captures the security context as it was DURING the chain call. */
    private Authentication authSeenByChain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        denylist = mock(DenylistService.class);
        filter = new JwtAuthenticationFilter(jwtService, denylist);
        authSeenByChain = null;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain capturingChain() {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                authSeenByChain = SecurityContextHolder.getContext().getAuthentication();
                super.doFilter(request, response);
            }
        };
    }

    @Test
    void validBearer_populatesSecurityContextAndRequestAttribute() throws Exception {
        ParsedToken parsed = new ParsedToken(ParsedToken.Type.ACCESS, 42L, "a@b.com", "jti-1", null,
                Instant.now().plusSeconds(600));
        when(jwtService.parseAccess("good-token")).thenReturn(parsed);
        when(denylist.isDenied("jti-1")).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/me/links");
        req.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, capturingChain());

        assertThat(authSeenByChain).isNotNull();
        assertThat(authSeenByChain.getName()).isEqualTo("42");
        AuthenticatedUser user = (AuthenticatedUser) req.getAttribute("auth.currentUser");
        assertThat(user.userId()).isEqualTo(42L);
        assertThat(user.jti()).isEqualTo("jti-1");
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void missingHeader_passesThroughUnauthenticated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/abc123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = capturingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull(); // chain proceeded
        assertThat(authSeenByChain).isNull();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void invalidToken_shortCircuits401Json() throws Exception {
        when(jwtService.parseAccess(anyString())).thenThrow(new InvalidTokenException("token expired"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/me/links");
        req.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentType()).contains("application/json");
        assertThat(res.getContentAsString()).contains("token expired");
        assertThat(chain.getRequest()).isNull(); // chain NOT reached
    }

    @Test
    void denylistedJti_shortCircuits401() throws Exception {
        ParsedToken parsed = new ParsedToken(ParsedToken.Type.ACCESS, 42L, "a@b.com", "revoked-jti", null,
                Instant.now().plusSeconds(600));
        when(jwtService.parseAccess("revoked")).thenReturn(parsed);
        when(denylist.isDenied("revoked-jti")).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/me/links");
        req.addHeader("Authorization", "Bearer revoked");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("revoked");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void contextIsClearedAfterChain() throws Exception {
        ParsedToken parsed = new ParsedToken(ParsedToken.Type.ACCESS, 42L, "a@b.com", "jti-1", null,
                Instant.now().plusSeconds(600));
        when(jwtService.parseAccess("good-token")).thenReturn(parsed);
        when(denylist.isDenied("jti-1")).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/me/links");
        req.addHeader("Authorization", "Bearer good-token");

        filter.doFilter(req, new MockHttpServletResponse(), capturingChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
