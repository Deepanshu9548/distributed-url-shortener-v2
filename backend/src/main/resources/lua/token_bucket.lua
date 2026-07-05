-- Token-bucket rate limiter — ADR-005. Atomic (single Lua execution),
-- deterministic (all clock input via ARGV so it stays replication-safe on
-- Redis Cluster), zero side effects on error paths.
--
-- KEYS[1] = bucket hash key. State schema:
--   HASH { tokens = <double, current bucket level>,
--          last_refill_ms = <long, ms epoch of last accounting> }
--
-- ARGV[1] = capacity        (integer)   — max tokens
-- ARGV[2] = refill_per_ms   (double)    — tokens added per millisecond
-- ARGV[3] = now_ms          (long)      — caller's clock
-- ARGV[4] = requested       (integer)   — tokens to consume (usually 1)
--
-- Returns { allowed, retry_after_ms }
--   allowed = 1 (consumed) or 0 (denied)
--   retry_after_ms = 0 when allowed, else ms until `requested` tokens are
--   available at the current refill rate.

local key             = KEYS[1]
local capacity        = tonumber(ARGV[1])
local refill_per_ms   = tonumber(ARGV[2])
local now_ms          = tonumber(ARGV[3])
local requested       = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
local tokens         = tonumber(data[1])
local last_refill_ms = tonumber(data[2])

if tokens == nil or last_refill_ms == nil then
    tokens = capacity
    last_refill_ms = now_ms
else
    -- Lazy refill. Guard against clock going backwards by clamping elapsed to 0.
    local elapsed = now_ms - last_refill_ms
    if elapsed < 0 then elapsed = 0 end
    tokens = math.min(capacity, tokens + elapsed * refill_per_ms)
    last_refill_ms = now_ms
end

local allowed = 0
local retry_after_ms = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
else
    local needed = requested - tokens
    -- refill_per_ms > 0 is guaranteed by the caller (config validation).
    retry_after_ms = math.ceil(needed / refill_per_ms)
    if retry_after_ms < 1 then retry_after_ms = 1 end
end

-- Persist new state and self-clean idle buckets: TTL = 2 * time to refill to full.
redis.call('HMSET', key, 'tokens', tokens, 'last_refill_ms', last_refill_ms)
local ttl_ms = math.ceil((capacity / refill_per_ms) * 2)
if ttl_ms < 1000 then ttl_ms = 1000 end
redis.call('PEXPIRE', key, ttl_ms)

return { allowed, retry_after_ms }
