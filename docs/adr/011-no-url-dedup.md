# ADR-011: No long-URL deduplication; other input-policy decisions

**Status:** Accepted (M0)

- Two users shortening the same URL get distinct codes. Dedup would require a
  reverse index (url-hash → code) on the write path, leak "someone already
  shortened this" across users, and break per-user ownership/analytics/TTL.
  Storage saved is negligible. REJECTED.
- Max URL length: 8192 chars (400 above).
- Alias policy: `^[a-zA-Z0-9_-]{4,30}$`, reserved blocklist
  {api, auth, actuator, swagger-ui, swagger, metrics, health, admin, v3}.
  Collision → 409, no suggestions (predictable, no enumeration surface).
- Open-redirect protection: http(s) only; resolved host must not be loopback,
  RFC1918 private, link-local, or the service's own host.
- Idempotent create: optional `Idempotency-Key` header; Redis
  `idem:{userOrIp}:{key}` → cached 201 body for 24h; prevents double-create on
  client retry through the LB.
