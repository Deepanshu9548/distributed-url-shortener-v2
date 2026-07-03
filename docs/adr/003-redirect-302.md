# ADR-003: 302 redirect, not 301

**Status:** Accepted (M0)

301 (permanent) lets browsers cache the mapping and skip our server on repeat
visits → click analytics silently undercount and TTL'd/deleted links keep
resolving from browser cache. 302 keeps every hit on our infrastructure —
cache-hit p95 <50ms makes the extra round-trip immaterial.
