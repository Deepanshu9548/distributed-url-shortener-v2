# ADR-009: Stateless JWT + refresh rotation + Redis denylist

**Status:** Accepted (M0)

Access token HS256, 15 min, carries `jti`. Refresh token 7 days, stored as
SHA-256 hash on control DB, rotated on every use (reuse of a rotated token →
401, session compromised signal). Logout: `jwt:deny:{jti}` in Redis with
TTL = remaining lifetime.

Why stateless: no session store on the request path → any app instance serves
any request → horizontal scaling stays trivial. Revocation gap is bounded by
the 15-minute expiry; the denylist closes it for explicit logout. Denylist
check FAILS OPEN when Redis is down (worst case: a logged-out token works for
≤15 min during a Redis outage) — availability over strictness for this domain;
documented in the degradation matrix.

The redirect path is outside the security filter chain entirely: zero auth
overhead on the hot path.
