package io.portfolio.urlshortener.auth;

import io.portfolio.urlshortener.auth.AuthDtos.LoginRequest;
import io.portfolio.urlshortener.auth.AuthDtos.LogoutRequest;
import io.portfolio.urlshortener.auth.AuthDtos.RefreshRequest;
import io.portfolio.urlshortener.auth.AuthDtos.RegisterRequest;
import io.portfolio.urlshortener.auth.AuthDtos.RegisterResponse;
import io.portfolio.urlshortener.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/auth/**} — register, login, refresh, logout. The register/
 * login/refresh endpoints are public; logout requires authentication. All
 * paths under {@code /api/auth} are bucket-limited by
 * {@link io.portfolio.urlshortener.ratelimit.RateLimitFilter}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final DenylistService denylist;

    public AuthController(UserService userService,
                          JwtService jwtService,
                          RefreshTokenService refreshTokens,
                          DenylistService denylist) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.denylist = denylist;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getId(), user.getEmail()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request.email(), request.password());
        return mint(user);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        ParsedToken parsed = jwtService.parseRefresh(request.refreshToken());
        Long userId = refreshTokens.userIdFor(parsed.sessionId());
        // fail-open on read: null could be "unknown" or "Redis down" — require id match.
        if (userId == null || userId != parsed.userId()) {
            throw new InvalidTokenException();
        }
        refreshTokens.revoke(parsed.sessionId());
        User user = userService.byId(parsed.userId());
        return mint(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request,
                                       AuthenticatedUser current) {
        ParsedToken refresh = jwtService.parseRefresh(request.refreshToken());
        if (refresh.userId() != current.userId()) {
            throw new InvalidTokenException();
        }
        refreshTokens.revoke(refresh.sessionId());
        denylist.deny(current.jti(), current.remaining());
        return ResponseEntity.noContent().build();
    }

    private TokenResponse mint(User user) {
        String sessionId = refreshTokens.newSessionId();
        TokenPair pair = jwtService.issue(user, sessionId);
        refreshTokens.register(sessionId, user.getId(), jwtService.refreshTtl());
        return TokenResponse.of(pair.accessToken(), pair.refreshToken(), jwtService.accessTtl().toSeconds());
    }
}
