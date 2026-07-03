# ADR-010: Non-sharded control DB, explicitly off the hot path

**Status:** Accepted (M0)

Users, refresh tokens, user_links index, click aggregates, raw click events
live on ONE Postgres (primary + streaming replica) — the "control DB".

Why not shard it: tiny cardinality relative to url_mapping, and its queries
(login, list-my-links, stats) are all user-scoped and latency-tolerant.
Sharding it buys nothing and complicates every query.

SPOF honesty: control DB down ⇒ auth/list/stats degrade (503), but the two
product-critical paths — redirect and anonymous create — are PROVEN unaffected
(chaos test kills control DB under load). The replica covers read availability;
promoting it is a manual runbook step, consciously out of scope.
