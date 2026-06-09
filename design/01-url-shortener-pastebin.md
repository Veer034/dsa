# Design TinyURL / Pastebin
> Classic warm-up question. Tests: hashing, DB choice, caching, redirects, scale math.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| How many URLs shortened per day? | Drives storage and write throughput estimate |
| What's the expected redirect QPS? | Reads >> Writes — shapes caching strategy |
| Should short URLs expire? | Influences DB schema and TTL design |
| Custom aliases allowed? | Collision handling changes significantly |
| Do we need analytics (clicks, geo)? | Adds async pipeline complexity |
| Global or single region? | CDN + replication strategy |

**Typical answer the interviewer gives:** 100M URLs/day writes, 10:1 read ratio (1B redirects/day), URLs expire after 1 year by default, analytics are nice-to-have.

---

## Scale Estimation (Say This Out Loud)

```
Writes:  100M / day  → ~1,200 writes/sec
Reads:   1B / day    → ~12,000 reads/sec (reads dominate)
Storage: 100M * 365 * ~500 bytes ≈ 18 TB/year
Short URL length: base62(7 chars) = 62^7 = ~3.5 trillion combinations (plenty)
```

---

## HLD Diagram

```
                        ┌─────────────────────────────────────┐
                        │           Client / Browser          │
                        └──────────────┬──────────────────────┘
                                       │ HTTPS
                              ┌────────▼────────┐
                              │   CDN / NGINX   │  ← Caches hot redirects at edge
                              └────────┬────────┘
                                       │
                              ┌────────▼────────┐
                              │   API Gateway   │  ← Rate limiting (NGINX+Lua)
                              └────┬───────┬────┘
                                   │       │
                    ┌──────────────▼─┐   ┌─▼──────────────────┐
                    │  Shorten API   │   │   Redirect API      │
                    │  [Java/SBoot]  │   │   [Java/SBoot]      │
                    └──────┬─────────┘   └──────┬──────────────┘
                           │                    │
               ┌───────────▼──┐         ┌───────▼────────┐
               │  ID Generator│         │  {Redis Cache} │ ← shortCode → longURL
               │  (Snowflake) │         │   TTL = 24h    │
               └───────┬──────┘         └───────┬────────┘
                       │                        │ cache miss
               ┌───────▼────────────────────────▼────────┐
               │           ((MySQL / Aurora))             │
               │   urls(short_code, long_url, user_id,    │
               │         created_at, expires_at)          │
               └───────────────────────┬─────────────────┘
                                       │ async
                              ┌────────▼──────────┐
                              │   <Kafka Topic>   │  url.clicked
                              └────────┬──────────┘
                                       │
                              ┌────────▼──────────┐
                              │  Analytics Writer │ → ((Druid / ClickHouse))
                              └───────────────────┘
```

---

## Key Design Decisions

### 1. How to Generate the Short Code?

**Option A — MD5/SHA256 + truncate:**
Hash the long URL, take first 7 chars. Problem: collision possible, same URL gives same code (might be a feature).

**Option B — Auto-increment ID → Base62 encode:**
DB auto-increment → convert to base62 string. Simple, no collisions. Problem: predictable/enumerable.

**Option C — Snowflake ID → Base62 (Recommended):**
- Distributed, time-sortable, no central coordination bottleneck
- 64-bit ID → base62 encode → 7-8 char string
- Mention: Twitter's Snowflake, Sony's Sonyflake, or your own

```
Why Snowflake over UUID?
→ UUID is 128-bit, base62 gives 22 chars — too long for a "short" URL
→ Snowflake is 64-bit, gives 7–8 chars cleanly
```

### 2. Database Choice

**MySQL with proper indexing** is fine at this scale.
- `short_code` as PRIMARY KEY (already indexed)
- Sharding by `short_code` prefix if needed at extreme scale
- Mention: Could use **Cassandra/ScyllaDB** if write volume is 10x higher — ScyllaDB handles millions of writes/sec with no hotspot issues

### 3. Caching Strategy

```
Redis key:   shortCode → longURL   (TTL = 24h or expiry_time)
Read path:   Check Redis first → cache hit returns instantly
             Cache miss → read MySQL → populate Redis → return

Eviction:    LRU policy. Hot URLs stay, cold ones evict.
Cache ratio: 80% reads served from cache (20% of URLs = 80% traffic — Pareto)
```

### 4. Bloom Filter for Existence Check

Before hitting DB on redirect, use a **Bloom Filter** to check if a short code was ever issued.
- False positives → check DB anyway (rare)
- False negatives never happen — if Bloom says no, it's definitely a 404
- Saves DB hits for clearly invalid/dead short codes (bots, typos)

```java
// Guava BloomFilter example
BloomFilter<String> filter = BloomFilter.create(
    Funnels.stringFunnel(Charset.defaultCharset()),
    100_000_000,  // expected insertions
    0.01          // 1% false positive rate
);
filter.put(shortCode);         // on create
filter.mightContain(shortCode); // on redirect — fast pre-check
```

### 5. Custom Aliases

- Check alias availability in DB + Bloom Filter
- Reserve a namespace (e.g., aliases < 4 chars are system-reserved)
- Collision → return error, let user pick another

### 6. Expiry

- Store `expires_at` in MySQL
- A background **Spring Batch job** runs nightly, soft-deletes expired rows
- Redis TTL handles cache expiry automatically

---

## LLD — Redirect Service Deep Dive

```
GET /abc123
    │
    ├─ Check Bloom Filter → not present → 404 immediately
    │
    ├─ Check Redis: GET abc123
    │       hit  → 301/302 redirect to longURL
    │       miss ↓
    │
    ├─ Query MySQL: SELECT long_url, expires_at WHERE short_code = 'abc123'
    │       not found → 404
    │       expired   → 410 Gone
    │       found     → populate Redis, redirect
    │
    └─ Publish click event to Kafka (async, fire-and-forget)
            topic: url.clicked
            payload: { shortCode, userId, ip, userAgent, timestamp }
```

### HTTP Status Choice: 301 vs 302

```
301 Permanent Redirect:
  → Browser caches it — fewer hits to our servers
  → Bad for analytics: we never see repeat visits
  → Good for: static content, old URL migrations

302 Temporary Redirect:
  → Browser always hits our server
  → Every click is logged — full analytics
  → Good for: URL shorteners that need click tracking

Decision: Use 302 if analytics matter (and they always do in SaaS).
```

---

## LLD — Shorten API

```java
// Service layer — not full production code, interview-level skeleton

@Service
public class UrlShortenerService {

    public String shortenUrl(String longUrl, String customAlias, Instant expiresAt) {
        
        String code = (customAlias != null) 
            ? validateAndUseAlias(customAlias) 
            : generateCode();  // Snowflake ID → base62
        
        UrlMapping mapping = new UrlMapping(code, longUrl, expiresAt);
        urlRepository.save(mapping);      // write to MySQL
        bloomFilter.put(code);            // update in-memory bloom filter
        // Redis NOT written on create — lazy population on first read
        
        return "https://tiny.io/" + code;
    }

    private String generateCode() {
        long id = snowflakeIdGenerator.nextId();  // distributed ID
        return Base62.encode(id);                 // custom base62 encoder
    }
}
```

---

## Database Schema

```sql
CREATE TABLE url_mappings (
    short_code   VARCHAR(12)   PRIMARY KEY,
    long_url     TEXT          NOT NULL,
    user_id      BIGINT,
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    expires_at   DATETIME,
    click_count  BIGINT        DEFAULT 0,   -- approximate, async updated
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)       -- for cleanup job
);
```

---

## NGINX + Lua Rate Limiting

```lua
-- nginx.conf snippet — Lua-based rate limiting per IP
local key = "rate:" .. ngx.var.remote_addr
local limit = 100   -- 100 requests/minute
local window = 60

local count = redis:incr(key)
if count == 1 then redis:expire(key, window) end

if count > limit then
    ngx.status = 429
    ngx.say('{"error": "rate limit exceeded"}')
    return ngx.exit(429)
end
```

---

## Pastebin Differences (If Asked)

| Aspect | TinyURL | Pastebin |
|--------|---------|---------|
| Payload | URL (< 2KB) | Text content (up to 10MB) |
| Storage | MySQL row | Object Storage (S3 / Azure Blob) |
| Key | Short code | Same short code approach |
| Content-Type | Redirect | Serve raw text or rendered HTML |
| Expiry | URL-level | Content-level (burn after read option) |

For Pastebin: store content in **S3/Azure Blob**, store metadata (key, size, expiry, syntax-highlight lang) in MySQL.

---

## Interview Tips for This Question

- Always mention **301 vs 302 trade-off** — interviewers love this.
- Bring up **Bloom Filter** proactively — shows you think about edge cases.
- Mention **Snowflake over MD5** and explain why (predictability vs collision).
- Don't forget **expiry cleanup** — shows operational thinking.
- If they push on scale: mention **ScyllaDB for write-heavy** scenarios, read replicas for MySQL, and sharding by hash prefix.
