package io.portfolio.urlshortener.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-04T10:00:00Z");
    private static final User USER = new User(42L, "a@b.com", "a@b.com", "hash", NOW);

    @Autowired
    private MockMvc mockMvc;

    @MockBean private UserService userService;
    @MockBean private JwtService jwtService;
    @MockBean private RefreshTokenService refreshTokens;
    @MockBean private DenylistService denylist;

    private void stubMint() {
        when(refreshTokens.newSessionId()).thenReturn("sid-1");
        when(jwtService.issue(any(User.class), anyString()))
                .thenReturn(new TokenPair("access-jwt", "refresh-jwt", "sid-1", "jti-1"));
        when(jwtService.accessTtl()).thenReturn(Duration.ofMinutes(15));
        when(jwtService.refreshTtl()).thenReturn(Duration.ofDays(7));
    }

    @Test
    void register_returns201WithUserIdAndEmail() throws Exception {
        when(userService.register(anyString(), anyString())).thenReturn(USER);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"hunter42x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.email").value("a@b.com"));
    }

    @Test
    void register_duplicate_returns409ErrorBody() throws Exception {
        when(userService.register(anyString(), anyString())).thenThrow(new EmailAlreadyExistsException());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"hunter42x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("email already registered"));
    }

    @Test
    void register_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"hunter42x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void login_returnsTokenPairAndRegistersSession() throws Exception {
        when(userService.login(anyString(), anyString())).thenReturn(USER);
        stubMint();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"hunter42x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));

        verify(refreshTokens).register(eq("sid-1"), eq(42L), eq(Duration.ofDays(7)));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(userService.login(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"wrongpass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid credentials"));
    }

    @Test
    void refresh_rotates_oldSessionRevoked() throws Exception {
        when(jwtService.parseRefresh("refresh-old")).thenReturn(
                new ParsedToken(ParsedToken.Type.REFRESH, 42L, null, "rjti", "sid-old", NOW.plus(Duration.ofDays(6))));
        when(refreshTokens.userIdFor("sid-old")).thenReturn(42L);
        when(userService.byId(42L)).thenReturn(USER);
        stubMint();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"));

        verify(refreshTokens).revoke("sid-old");
        verify(refreshTokens).register(eq("sid-1"), eq(42L), any(Duration.class));
    }

    @Test
    void refresh_unknownOrRevokedSession_returns401() throws Exception {
        when(jwtService.parseRefresh("refresh-old")).thenReturn(
                new ParsedToken(ParsedToken.Type.REFRESH, 42L, null, "rjti", "sid-old", NOW.plus(Duration.ofDays(6))));
        when(refreshTokens.userIdFor("sid-old")).thenReturn(null);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-old\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesSessionAndDenylistsJti() throws Exception {
        when(jwtService.parseRefresh("refresh-jwt")).thenReturn(
                new ParsedToken(ParsedToken.Type.REFRESH, 42L, null, "rjti", "sid-1", NOW.plus(Duration.ofDays(6))));

        AuthenticatedUser current = new AuthenticatedUser(42L, "a@b.com", "jti-1",
                Instant.now().plus(Duration.ofMinutes(10)));

        mockMvc.perform(post("/api/auth/logout")
                        .requestAttr("auth.currentUser", current)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-jwt\"}"))
                .andExpect(status().isNoContent());

        verify(refreshTokens).revoke("sid-1");
        verify(denylist).deny(eq("jti-1"), any(Duration.class));
    }

    @Test
    void logout_refreshOfDifferentUser_returns401() throws Exception {
        when(jwtService.parseRefresh("refresh-jwt")).thenReturn(
                new ParsedToken(ParsedToken.Type.REFRESH, 99L, null, "rjti", "sid-9", NOW.plus(Duration.ofDays(6))));

        AuthenticatedUser current = new AuthenticatedUser(42L, "a@b.com", "jti-1",
                Instant.now().plus(Duration.ofMinutes(10)));

        mockMvc.perform(post("/api/auth/logout")
                        .requestAttr("auth.currentUser", current)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-jwt\"}"))
                .andExpect(status().isUnauthorized());
    }
}
