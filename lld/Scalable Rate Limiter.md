# LLD: Scalable Rate Limiter

> **Experience Level:** 10+ Years Java | Spring Boot · Kafka · Redis · MySQL · Elasticsearch · ScyllaDB · Druid

---

## 🔁 Clarifying Questions (You → Interviewer)

| # | Question | Why It Matters |
|---|----------|----------------|
| 1 | Should the rate limiter be per user, per IP, per API key, or global? | Determines the key strategy |
| 2 | What algorithm is preferred — Token Bucket, Leaky Bucket, Fixed Window, or Sliding Window? | Core algorithm choice |
| 3 | Is this in-process (single JVM) or distributed (multiple nodes)? | Redis vs local strategy |
| 4 | What happens when limit is exceeded — hard reject (429) or queue the request? | Overflow handling |
| 5 | Do we need different limits per endpoint or per user tier (free vs paid)? | Config/policy abstraction |
| 6 | Should limits be enforced at the API Gateway, service level, or both? | Deployment topology |
| 7 | What's the acceptable latency overhead for rate limit checks? | Whether Redis round-trip is acceptable |
| 8 | Do we need burst allowance (e.g., 100 req/min with spike of 20 extra)? | Token Bucket vs Fixed Window |

---

## 🔁 Expected Follow-up Questions (Interviewer → You)

- "What's the difference between Token Bucket and Leaky Bucket?"
- "How does Sliding Window Log fix the boundary problem in Fixed Window?"
- "How does your Redis Lua script ensure atomicity?"
- "Walk me through how you'd handle Redis failure — fail open or fail closed?"
- "How would you rate limit across 10 microservices consistently?"
- "How do you prevent a user from exploiting the fixed window reset boundary?"
- "How would you test your rate limiter under concurrent load?"

---

## Algorithm Comparison

| Algorithm | Memory | Burst Handling | Accuracy | Best For |
|-----------|--------|----------------|----------|----------|
| Fixed Window Counter | Low | ❌ Boundary spike | Medium | Simple APIs |
| Sliding Window Log | High | ✅ Accurate | High | Audit-sensitive APIs |
| Sliding Window Counter | Medium | ✅ Good | High | **General use** |
| Token Bucket | Low | ✅ Excellent | High | APIs with bursts |
| Leaky Bucket | Low | ❌ No burst | High | Strict rate smoothing |

---

## Algorithm 1: Fixed Window Counter

```java
public class FixedWindowRateLimiter {
    private final int maxRequests;
    private final long windowMillis;

    // key → [windowStartMs, count]
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    public synchronized boolean allow(String clientId) {
        long now = System.currentTimeMillis();
        counters.compute(clientId, (k, v) -> {
            if (v == null || now - v[0] >= windowMillis)
                return new long[]{now, 0};
            return v;
        });
        long[] window = counters.get(clientId);
        if (window[1] < maxRequests) {
            window[1]++;
            return true;
        }
        return false;
    }
}
```

> ⚠️ **Boundary Problem**: A user can make `2 * maxRequests` calls straddling the window boundary (last second of window N + first second of window N+1).

---

## Algorithm 2: Sliding Window Log

```java
public class SlidingWindowLogRateLimiter {
    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();

    public SlidingWindowLogRateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    public synchronized boolean allow(String clientId) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowMillis;

        requestLogs.computeIfAbsent(clientId, k -> new ArrayDeque<>());
        Deque<Long> log = requestLogs.get(clientId);

        // Evict expired timestamps
        while (!log.isEmpty() && log.peekFirst() <= windowStart) {
            log.pollFirst();
        }

        if (log.size() < maxRequests) {
            log.addLast(now);
            return true;
        }
        return false;
    }
}
```

> ⚠️ Memory-heavy: stores every request timestamp. Impractical for high-traffic systems.

---

## Algorithm 3: Token Bucket ✅ (Recommended)

```java
public class TokenBucketRateLimiter {
    private final int maxTokens;
    private final double refillRatePerMs; // tokens added per ms

    private static class Bucket {
        double tokens;
        long lastRefillTime;

        Bucket(int maxTokens) {
            this.tokens = maxTokens;
            this.lastRefillTime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int maxTokens, int refillPerSecond) {
        this.maxTokens = maxTokens;
        this.refillRatePerMs = (double) refillPerSecond / 1000.0;
    }

    public synchronized boolean allow(String clientId) {
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(maxTokens));
        refill(bucket);
        if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill(Bucket bucket) {
        long now = System.currentTimeMillis();
        double elapsed = now - bucket.lastRefillTime;
        double tokensToAdd = elapsed * refillRatePerMs;
        bucket.tokens = Math.min(maxTokens, bucket.tokens + tokensToAdd);
        bucket.lastRefillTime = now;
    }
}
```

---

## Algorithm 4: Leaky Bucket

```java
public class LeakyBucketRateLimiter {
    private final int bucketCapacity;   // max queue size
    private final long leakIntervalMs;  // time between draining one request

    private static class Bucket {
        int currentSize;
        long lastLeakTime;
        Bucket(long now) { currentSize = 0; lastLeakTime = now; }
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LeakyBucketRateLimiter(int bucketCapacity, long leakIntervalMs) {
        this.bucketCapacity = bucketCapacity;
        this.leakIntervalMs = leakIntervalMs;
    }

    public synchronized boolean allow(String clientId) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(now));

        // Leak (drain) requests that should have been processed
        long elapsed = now - bucket.lastLeakTime;
        long leaked = elapsed / leakIntervalMs;
        if (leaked > 0) {
            bucket.currentSize = (int) Math.max(0, bucket.currentSize - leaked);
            bucket.lastLeakTime = now;
        }

        if (bucket.currentSize < bucketCapacity) {
            bucket.currentSize++;
            return true;
        }
        return false;
    }
}
```

---

## Distributed Rate Limiter with Redis (Production-Grade)

### Why Redis?

- Atomic Lua scripts eliminate race conditions across JVM instances
- Sub-millisecond latency
- Built-in TTL for automatic window expiry

### Redis Lua Script — Sliding Window Counter

```lua
-- KEYS[1] = rate_limit:{clientId}:{windowStart}
-- ARGV[1] = max requests
-- ARGV[2] = window TTL in seconds
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end
if current > tonumber(ARGV[1]) then
    return 0
end
return 1
```

### Java Service

```java
@Service
public class RedisRateLimiter {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end\n" +
            "if current > tonumber(ARGV[1]) then return 0 end\n" +
            "return 1"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    /**
     * @param clientId  user ID or API key
     * @param maxReqs   max requests per window
     * @param windowSec window duration in seconds
     */
    public boolean isAllowed(String clientId, int maxReqs, int windowSec) {
        long windowStart = System.currentTimeMillis() / (windowSec * 1000L);
        String key = String.format("rl:%s:%d", clientId, windowStart);

        Long result = redisTemplate.execute(
            RATE_LIMIT_SCRIPT,
            Collections.singletonList(key),
            String.valueOf(maxReqs),
            String.valueOf(windowSec)
        );

        return result != null && result == 1L;
    }
}
```

### Redis Token Bucket Script

```lua
-- KEYS[1] = token_bucket:{clientId}
-- ARGV[1] = max tokens (capacity)
-- ARGV[2] = refill rate per second
-- ARGV[3] = current timestamp (ms)
local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill')
local tokens = tonumber(bucket[1]) or tonumber(ARGV[1])
local last_refill = tonumber(bucket[2]) or tonumber(ARGV[3])
local now = tonumber(ARGV[3])
local rate = tonumber(ARGV[2])
local capacity = tonumber(ARGV[1])

local elapsed = (now - last_refill) / 1000.0
local new_tokens = math.min(capacity, tokens + elapsed * rate)

if new_tokens >= 1 then
    redis.call('HMSET', KEYS[1], 'tokens', new_tokens - 1, 'last_refill', now)
    redis.call('EXPIRE', KEYS[1], 3600)
    return 1
end
return 0
```

---

## Spring Boot Filter Integration

```java
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req  = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String clientId = extractClientId(req); // from JWT, API key, or IP

        RateLimitPolicy policy = getPolicyForEndpoint(req.getRequestURI());

        if (!rateLimiter.isAllowed(clientId, policy.maxRequests(), policy.windowSeconds())) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429
            res.setHeader("X-RateLimit-Limit",     String.valueOf(policy.maxRequests()));
            res.setHeader("X-RateLimit-Remaining", "0");
            res.setHeader("Retry-After",            String.valueOf(policy.windowSeconds()));
            res.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String extractClientId(HttpServletRequest req) {
        String apiKey = req.getHeader("X-API-Key");
        return apiKey != null ? apiKey : req.getRemoteAddr();
    }

    private RateLimitPolicy getPolicyForEndpoint(String uri) {
        if (uri.startsWith("/api/free/"))    return new RateLimitPolicy(100, 60);
        if (uri.startsWith("/api/premium/")) return new RateLimitPolicy(1000, 60);
        return new RateLimitPolicy(50, 60); // default
    }
}

public record RateLimitPolicy(int maxRequests, int windowSeconds) {}
```

---

## Multi-Tier Rate Limiting

```
Request → API Gateway (global limit: 10k RPS)
       → Per-User Limiter (Redis: 100 req/min per user)
       → Per-Endpoint Limiter (Redis: 10 req/sec on /login)
       → Service-level Limiter (in-memory Token Bucket for burst protection)
```

```java
@Service
public class MultiTierRateLimiter {

    @Autowired private RedisRateLimiter redisLimiter;
    private final TokenBucketRateLimiter localLimiter = new TokenBucketRateLimiter(500, 500);

    public boolean isAllowed(String userId, String endpoint) {
        // Tier 1: local in-process burst protection (no Redis overhead)
        if (!localLimiter.allow("global")) return false;

        // Tier 2: per-user Redis limit
        if (!redisLimiter.isAllowed("user:" + userId, 100, 60)) return false;

        // Tier 3: per-endpoint limit
        if (!redisLimiter.isAllowed("ep:" + endpoint, 50, 1)) return false;

        return true;
    }
}
```

---

## Failure Handling Strategy

```java
public boolean isAllowed(String clientId, int maxReqs, int windowSec) {
    try {
        return redisCheck(clientId, maxReqs, windowSec);
    } catch (RedisConnectionFailureException ex) {
        log.warn("Redis unavailable, applying fail-open policy for: {}", clientId);
        // Fail-open: allow traffic to avoid outage cascade
        // Fail-closed alternative: return false (blocks all traffic)
        return true;
    }
}
```

---

## Response Headers (Standard)

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1718123460
Retry-After: 60
```

---

## MySQL: Rate Limit Config Table

```sql
CREATE TABLE rate_limit_policy (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_tier  VARCHAR(20) NOT NULL,  -- FREE, PREMIUM, ADMIN
    endpoint     VARCHAR(100),          -- NULL = applies to all
    max_requests INT NOT NULL,
    window_secs  INT NOT NULL,
    algorithm    ENUM('FIXED_WINDOW','SLIDING_WINDOW','TOKEN_BUCKET') DEFAULT 'TOKEN_BUCKET',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tier_endpoint (client_tier, endpoint)
);
```

---

## Extension Points

- **Adaptive rate limiting**: Increase limits for low-error users dynamically
- **Rate limit by geo**: Different limits per region using IP geolocation
- **Kafka audit log**: Emit rate-limit events to Kafka for Druid dashboards
- **Prometheus metrics**: Expose allowed/rejected counters via Micrometer
- **Redis Cluster**: Shard rate limit keys across Redis nodes for horizontal scale