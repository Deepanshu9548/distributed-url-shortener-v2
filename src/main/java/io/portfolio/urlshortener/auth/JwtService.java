package io.portfolio.urlshortener.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 JWT issue + parse (ADR-009). Two token types: {@code access} (15m,
 * carries {@code email}) and {@code refresh} (7d, carries {@code sid} —
 * server-side session id used to revoke refresh families).
 *
 * <p>Parsing is strict: expiry, issuer, and {@code typ} are all validated;
 * any failure surfaces as {@link InvalidTokenException} (never as a jjwt
 * exception).
 *
 * <p>{@code @ConditionalOnProperty("app.jwt.secret")} keeps the bean (and
 * therefore any dependent auth infrastructure) out of the context when the
 * secret isn't configured — so slice tests that don't opt into JWT don't
 * pay for it.
 */
@Service
@ConditionalOnProperty(prefix = "app.jwt", name = "secret")
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_SESSION_ID = "sid";

    private final SecretKey signingKey;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final String issuer;
    private final Clock clock;

    public JwtService(JwtProperties props) {
        this(props, Clock.systemUTC());
    }

    /** Package-private for tests: inject a fixed {@link Clock}. */
    JwtService(JwtProperties props, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = props.accessTtl();
        this.refreshTtl = props.refreshTtl();
        this.issuer = props.issuer();
        this.clock = clock;
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    /**
     * Mint a fresh access + refresh pair for {@code user}. {@code sessionId}
     * is the refresh {@code sid}; the caller is responsible for storing it
     * in the refresh-session store.
     */
    public TokenPair issue(User user, String sessionId) {
        Instant now = clock.instant();
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String access = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(accessJti)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .claim(CLAIM_TYPE, "access")
                .claim(CLAIM_EMAIL, user.getEmail())
                .signWith(signingKey)
                .compact();

        String refresh = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(refreshJti)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .claim(CLAIM_TYPE, "refresh")
                .claim(CLAIM_SESSION_ID, sessionId)
                .signWith(signingKey)
                .compact();

        return new TokenPair(access, refresh, sessionId, accessJti);
    }

    public ParsedToken parseAccess(String token) {
        return parse(token, ParsedToken.Type.ACCESS);
    }

    public ParsedToken parseRefresh(String token) {
        return parse(token, ParsedToken.Type.REFRESH);
    }

    private ParsedToken parse(String token, ParsedToken.Type expected) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("token invalid");
        }

        String typ = claims.get(CLAIM_TYPE, String.class);
        if (typ == null || !typ.equalsIgnoreCase(expected.name().toLowerCase())) {
            throw new InvalidTokenException("token type mismatch");
        }

        long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidTokenException("token subject invalid");
        }

        return new ParsedToken(
                expected,
                userId,
                claims.get(CLAIM_EMAIL, String.class),
                claims.getId(),
                claims.get(CLAIM_SESSION_ID, String.class),
                claims.getExpiration().toInstant());
    }
}
