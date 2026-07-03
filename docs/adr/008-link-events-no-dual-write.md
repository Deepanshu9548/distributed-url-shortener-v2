# ADR-008: link-events topic instead of dual-write for user_links

**Status:** Accepted (M0)

## Problem
"List my links" needs a user→codes index, but codes live sharded by code hash.
Naive fix: on create, write shard AND control-DB index. That dual-write has no
transaction spanning both stores → index silently diverges on partial failure.

## Decision
Create path writes ONLY its shard, then publishes `LinkEvent(CREATED)` to the
`link-events` topic. A consumer (idempotent, ADR-007 mechanism) upserts
user_links on the control DB. UPDATED/DELETED events fan out cache invalidation
as well. If the publish itself fails, the url_mapping row is flagged
`needs_sync=true`; a periodic sweeper republishes flagged rows (outbox-lite).

## Consequences
- "My links" is eventually consistent (ms-scale) — acceptable per the NFRs;
  create response itself carries the new link, so the user never notices.
- One infrastructure pattern (Kafka + idempotent consumer) now serves two use
  cases — less machinery, stronger story.
