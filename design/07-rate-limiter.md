# Design a Rate Limiter
> Deceptively deep question. Tests: algorithms, distributed coordination, Redis atomicity, edge cases.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| Per-user or per-IP or per-API-key? | Changes the key design |
| Hard limit (block) or soft limit (throttle/queue)? | Return 429 or slow down? |
| Single server or distributed? | Distributed = coordination problem |
| What granularity? | Per-second, per-minute, per-day? Multiple windows? |
| Consistent across data centers? | Strict vs approximate rate limiting |
| Where does it live? | Client-side, API gateway, per-service? |
| Different limits for different endpoints? | Rate limit rules/config management |

**Typical answer:** API gateway level, per API-key, per-minute limit, distributed, return 429, different limits per tier (free/paid).

---

## Rate Limiting Algorithms — Know All 4

### Algorithm 1: Token Bucket

```
Conceptually: a bucket holds N tokens. Each request consumes 1 token.
Tokens refill at a fixed rate (e.g., 100 tokens/min).
If bucket empty → reject.

Properties:
  ✓ Allows bursts (bucket fills up, user can burst)
  ✓ Smooth average rate
  ✓ Memory efficient (2 values per key: token_count, last_refill_time)
  ✗ Race condition in distributed system without atomic ops

Used by: AWS API Gateway, Stripe
```

### Algorithm 2: Leaky Bucket

```
Requests enter a queue (bucket). Processed at fixed rate.
If queue full → reject.

Properties:
  ✓ Strict output rate — no bursts allowed
  ✓ Smooths traffic spikes
  ✗ Not fair during bursts (early requests wait)
  ✗ Requires queue per user — memory heavy

Used by: Traffic shaping in networking
```

### Algorithm 3: Fixed Window Counter

```
Time divided into fixed windows (e.g., 00:00-01:00, 01:00-02:00).
Counter incremented per request per window.
If counter > limit → reject.

Properties:
  ✓ Simple, memory efficient
  ✗ Boundary problem: user can send 100 at 00:59 and 100 at 01:01 = 200 in 2 seconds

Key: rate:{userId}:{minute}  value: count  TTL: 2 minutes
```

### Algorithm 4: Sliding Window Log

```
Store timestamp of every request in a sorted set.
On each request: remove timestamps older than window, count remaining.
If count >= limit → reject.

Properties:
  ✓ No boundary problem — true sliding window
  ✗ Memory heavy — store every request timestamp

Redis: ZSET per user, score = timestamp
```

### Algorithm 5: Sliding Window Counter (Best for Production)

```
Hybrid of fixed window + sliding window.
Current window count + weighted previous window count.

rate = prev_window_count * (overlap_ratio) + current_window_count

Example:
  Window: 1 minute. Limit: 100.
  Current minute started 75% through.
  Previous minute: 80 requests. Current minute: 30 requests.
  
  estimated_count = 80 * (1 - 0.75) + 30 = 80 * 0.25 + 30 = 20 + 30 = 50 → allow

Properties:
  ✓ Approximation of true sliding window
  ✓ Memory efficient (2 values per key, not all timestamps)
  ✓ Good enough for most production needs
  ✗ Not perfectly accurate (approximation)

Used by: Cloudflare (their description matches this approach)
```

---

## HLD Diagram

```
            ┌─────────────────────────────────┐
            │           Client                │
            └────────────────┬────────────────┘
                             │ HTTP
            ┌────────────────▼────────────────┐
            │      NGINX API Gateway          │
            │   [Lua Rate Limit Module]       │ ← check Redis before routing
            └────────────────┬────────────────┘
                             │ check rate
            ┌────────────────▼────────────────┐
            │     {Redis Cluster}             │
            │  rate:{apiKey}:{window} → count │
            │  Lua script for atomicity       │
            └────────────────┬────────────────┘
                   allow ←───┴───→ deny (429)
            ┌────────────────▼────────────────┐
            │        Backend Services         │
            └─────────────────────────────────┘

            ┌─────────────────────────────────┐
            │     Rate Limit Config Service   │ ← stores rules per tier
            │     ((MySQL))                   │   (free: 100/min, pro: 1000/min)
            │     {Redis Cache}               │ ← rules cached, TTL 5min
            └─────────────────────────────────┘

            ┌─────────────────────────────────┐
            │     Rate Limit Analytics        │ → Kafka → Druid
            │  (who's hitting limits, trends) │
            └─────────────────────────────────┘
```

---

## Key Design Decisions

### 1. Atomicity Problem in Redis

```
The core problem: check-then-increment is NOT atomic

// WRONG — race condition
int count = redis.get(key);
if (count < limit) {
    redis.incr(key);  // another thread can increment between get and incr
    allow();
} else {
    deny();
}

// RIGHT — Lua script executes atomically in Redis (single-threaded)
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

local current = redis.call("INCR", key)
if current == 1 then
    redis.call("EXPIRE", key, window)
end

if current > limit then
    return 0  -- deny
else
    return 1  -- allow
end

// Java: jedis.eval(luaScript, keys, args)
// This Lua script runs atomically — no race condition possible
```

### 2. Sliding Window with Redis ZSET

```lua
-- Sliding window log using Redis Sorted Set
-- More memory, more accurate than counter approach

local key = KEYS[1]
local now = tonumber(ARGV[1])          -- current timestamp ms
local window = tonumber(ARGV[2])       -- 60000 ms = 1 minute
local limit = tonumber(ARGV[3])

local window_start = now - window

-- Remove expired entries
redis.call("ZREMRANGEBYSCORE", key, 0, window_start)

-- Count requests in window
local count = redis.call("ZCARD", key)

if count >= limit then
    return 0  -- deny
end

-- Add current request
redis.call("ZADD", key, now, now)      -- score=timestamp, member=timestamp
redis.call("EXPIRE", key, math.ceil(window / 1000) + 1)

return 1  -- allow
```

### 3. Distributed Rate Limiting Across DCs

```
Problem: User makes 50 requests to DC1 and 50 requests to DC2.
Each DC thinks they're within the 100/min limit → 100 total requests pass.

Options:

A. Centralized Redis (single cluster, all DCs):
   ✓ Accurate
   ✗ Cross-DC latency adds ~50-100ms to every request
   ✗ Single point of failure

B. Local Redis + Async Sync:
   → Each DC has local Redis
   → Limit per DC = total_limit / num_DCs (100/2 = 50 per DC)
   → Occasional sync to aggregate counts
   ✓ Fast (local lookup)
   ✗ Approximate — bursts can exceed limit between sync windows

C. Race-to-win with distributed counter (DynamoDB / Redis Cluster):
   → Global counter with CAS operations
   → Acceptable for < 50ms latency requirements

What you say:
  "For strict accuracy I'd use centralized Redis with regional read replicas
   for config but writes going to primary. For high-performance APIs where
   approximate limiting is OK, I'd use local counters with per-DC quotas."
```

### 4. NGINX + Lua Implementation

```nginx
# nginx.conf
http {
    lua_shared_dict rate_limit_store 10m;
    
    server {
        location /api/ {
            access_by_lua_file /etc/nginx/lua/rate_limit.lua;
            proxy_pass http://backend;
        }
    }
}
```

```lua
-- /etc/nginx/lua/rate_limit.lua
local redis = require "resty.redis"
local red = redis:new()
red:connect("redis-cluster.internal", 6379)

local api_key = ngx.req.get_headers()["X-API-Key"]
if not api_key then
    ngx.status = 401
    ngx.say('{"error": "missing api key"}')
    return ngx.exit(401)
end

-- Get limit for this API key tier from cache
local limit = get_tier_limit(api_key)  -- Redis lookup, cached 5min
local window = 60  -- 1 minute

local key = "rate:" .. api_key
local result = red:eval(sliding_window_lua_script, 1, key,
    ngx.now() * 1000, window * 1000, limit)

if result == 0 then
    ngx.header["Retry-After"] = "60"
    ngx.header["X-RateLimit-Limit"] = limit
    ngx.status = 429
    ngx.say('{"error": "rate limit exceeded"}')
    return ngx.exit(429)
end

-- Add rate limit headers (good practice)
ngx.header["X-RateLimit-Limit"] = limit
ngx.header["X-RateLimit-Remaining"] = limit - red:zcard(key)
```

### 5. Response Headers (Production Practice)

```
Always return these headers — shows operational maturity:

HTTP/1.1 200 OK
X-RateLimit-Limit: 1000          ← total limit for this window
X-RateLimit-Remaining: 847       ← remaining in current window
X-RateLimit-Reset: 1735689600    ← Unix timestamp when window resets

HTTP/1.1 429 Too Many Requests
Retry-After: 34                  ← seconds until they can retry
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 0
```

### 6. Tiered Rate Limiting Rules

```sql
CREATE TABLE rate_limit_rules (
    rule_id       INT PRIMARY KEY AUTO_INCREMENT,
    tier          ENUM('free', 'starter', 'pro', 'enterprise'),
    endpoint      VARCHAR(200),   -- '/api/v1/search' or '*' for global
    limit_count   INT,
    window_seconds INT,
    burst_factor  DECIMAL(3,1) DEFAULT 1.5,  -- allow 1.5x burst
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Examples:
-- free tier: 100 requests/min globally
-- pro tier:  1000 requests/min, 200 requests/sec on /search
-- enterprise: custom per contract
```

---

## Edge Cases to Mention

```
1. Clock skew across distributed nodes:
   → Use NTP synchronized clocks, or use Redis server time (TIME command)
   → Small skew (< 100ms) is acceptable for most rate limiters

2. Redis failure:
   → Fail open (allow all) vs fail closed (deny all)
   → Most APIs: fail open with local fallback counter
   → Security-critical APIs: fail closed

3. User shares an IP (NAT, corporate proxy):
   → IP-based limiting too aggressive for corporate users
   → Prefer API-key based limiting
   → IP limiting only for unauthenticated endpoints

4. Distributed denial via legitimate accounts:
   → Account-level + IP-level limits both needed
   → Anomaly detection (Druid): flag accounts with unusual spike patterns

5. Key expiry race:
   → Lua script handles INCR + EXPIRE atomically — no race possible

6. Large burst at window start:
   → Sliding window counter handles this — no fixed window boundary problem
```

---

## Interview Tips

- **Know all 5 algorithms** by name — interviewers ask "what algorithms exist?" before asking which to pick.
- The **Lua atomicity point** is the technical depth they want — "Redis is single-threaded and Lua scripts execute atomically, solving the race condition inherent in check-then-increment."
- **Distributed limiting across DCs** — bring this up. Say "the hard part is multi-region" and explain trade-offs.
- Mention **response headers** — this shows you've shipped APIs, not just designed them.
- **Fail open vs fail closed** is a great trade-off discussion — security vs availability.
- Mention NGINX + Lua as the actual implementation: shows you've done it, not just read about it.
