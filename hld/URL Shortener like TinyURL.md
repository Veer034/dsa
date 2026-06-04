# HLD: URL Shortener like TinyURL

> **Experience Level:** 10+ Years Java | Spring Boot · Kafka · Redis · MySQL · Elasticsearch · ScyllaDB · Druid

---

## 🔁 Clarifying Questions (You → Interviewer)

| # | Question | Why It Matters |
|---|----------|----------------|
| 1 | What's the expected write QPS (URL creations) and read QPS (redirects)? | Shaping storage and caching decisions |
| 2 | Should short URLs be random or human-readable/custom aliases? | ID generation strategy |
| 3 | Do we need URL expiry (TTL) or are they permanent? | Storage cleanup + Redis TTL |
| 4 | Should we track analytics — click counts, geo, device, referrer? | Kafka + Druid involvement |
| 5 | Is user authentication required? Can anyone shorten a URL? | Auth and abuse prevention |
| 6 | What's the required redirect latency SLA? (< 10ms, < 100ms?) | Cache-first vs DB-first read path |
| 7 | Do we need to detect and reject malicious/phishing URLs? | URL validation pipeline |
| 8 | Should the same long URL always produce the same short URL (deduplication)? | Hash-based vs counter-based ID |

---

## 🔁 Expected Follow-up Questions (Interviewer → You)

- "How do you generate a unique 6-character short code at scale?"
- "What happens when two users shorten the same URL at the same millisecond?"
- "How do you handle Redis cache miss on redirect?"
- "How would your design survive a Redis failure?"
- "Why ScyllaDB over MySQL for the URL mapping table?"
- "Walk me through the end-to-end redirect flow for `tinyurl.com/abc123`."
- "How would you implement URL expiry?"
- "How would you scale writes to 50,000 URL creations per second?"

---

## Scale Estimation

```
Write QPS:   ~1,000 new URLs/sec
Read QPS:    ~100,000 redirects/sec   (100:1 read:write ratio)
Storage:     ~500 bytes/URL × 100M URLs = ~50 GB (MySQL)
Cache:       Top 20% URLs handle 80% traffic → ~10M entries cached
Short code:  6 chars × Base62 = 62^6 = ~56 billion unique codes
```

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          Clients                                 │
└───────────────────┬────────────────────────┬────────────────────┘
                    │ POST /shorten           │ GET /{shortCode}
                    ▼                         ▼
         ┌──────────────────┐      ┌──────────────────────┐
         │   Write Service   │      │   Redirect Service    │
         │  (Spring Boot)    │      │   (Spring Boot)       │
         └────────┬─────────┘      └──────────┬────────────┘
                  │                            │ Cache Lookup
                  │                            ▼
                  │                  ┌──────────────────┐
                  │                  │   Redis Cluster   │
                  │                  │  (shortCode→URL)  │
                  │                  └────────┬─────────┘
                  │                           │ Cache Miss
                  ▼                           ▼
         ┌──────────────────────────────────────────────┐
         │              ScyllaDB / MySQL                 │
         │         (short_code → long_url mapping)       │
         └─────────────────────┬────────────────────────┘
                               │
                               ▼
                    ┌──────────────────┐
                    │   Kafka Topic     │
                    │  url.click.events │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │  Analytics Worker │
                    │  → Druid (OLAP)   │
                    └──────────────────┘
```

---

## Short Code Generation Strategies

### Strategy 1: Base62 of MD5 Hash (Simple, Risk of Collision)

```java
@Service
public class HashBasedCodeGenerator {

    public String generate(String longUrl) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(longUrl.getBytes(StandardCharsets.UTF_8));
            String base62 = Base62Encoder.encode(hash);
            return base62.substring(0, 6); // first 6 chars
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

> ⚠️ Truncation increases collision probability. Must check DB on collision and append a suffix.

---

### Strategy 2: Global Counter + Base62 Encode ✅ (Recommended)

```java
@Service
public class CounterBasedCodeGenerator {

    @Autowired
    private RedisAtomicLong globalCounter; // Redis INCR

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public String generate() {
        long id = globalCounter.incrementAndGet(); // atomic, distributed
        return encode(id);
    }

    private String encode(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(BASE62.charAt((int)(num % 62)));
            num /= 62;
        }
        while (sb.length() < 6) sb.append('0'); // pad to 6 chars
        return sb.reverse().toString();
    }
}
```

> For higher throughput, pre-allocate **ranges** from Redis (e.g., each node gets 1000-id blocks), avoiding a Redis call per URL creation.

---

### Strategy 3: Range-Partitioned ID (Best for Scale)

```java
@Service
public class RangeBasedIdGenerator {
    private static final int BATCH = 1000;
    private long currentId;
    private long maxId;

    @Autowired
    private RedisAtomicLong counterStore;

    public synchronized long nextId() {
        if (currentId >= maxId) {
            // Claim next batch from Redis atomically
            maxId = counterStore.addAndGet(BATCH);
            currentId = maxId - BATCH;
        }
        return currentId++;
    }
}
```

---

## Core Services

### URL Shortener Service

```java
@RestController
@RequestMapping("/api/v1")
public class UrlShortenerController {

    @Autowired private UrlShortenerService service;

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest req) {
        String shortCode = service.shorten(req.getLongUrl(), req.getCustomAlias(), req.getTtlDays());
        return ResponseEntity.ok(new ShortenResponse("https://tny.io/" + shortCode));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode,
                                          HttpServletRequest request) {
        String longUrl = service.resolve(shortCode, request);
        if (longUrl == null) return ResponseEntity.notFound().build();
        return ResponseEntity.status(HttpStatus.FOUND)
                             .header("Location", longUrl)
                             .build();
    }
}
```

```java
@Service
public class UrlShortenerService {

    @Autowired private UrlRepository    urlRepository;
    @Autowired private RedisTemplate<String, String> redis;
    @Autowired private CounterBasedCodeGenerator     codeGenerator;
    @Autowired private KafkaTemplate<String, ClickEvent> kafka;

    private static final String CACHE_PREFIX = "url:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(30);

    public String shorten(String longUrl, String customAlias, Integer ttlDays) {
        String code = (customAlias != null && !customAlias.isBlank())
                      ? validateAndUseAlias(customAlias)
                      : codeGenerator.generate();

        Duration ttl = ttlDays != null ? Duration.ofDays(ttlDays) : DEFAULT_TTL;

        UrlMapping mapping = new UrlMapping(code, longUrl, Instant.now().plus(ttl));
        urlRepository.save(mapping);
        redis.opsForValue().set(CACHE_PREFIX + code, longUrl, ttl);

        return code;
    }

    public String resolve(String code, HttpServletRequest req) {
        // 1. Redis cache lookup
        String longUrl = redis.opsForValue().get(CACHE_PREFIX + code);

        // 2. DB fallback on cache miss
        if (longUrl == null) {
            UrlMapping mapping = urlRepository.findByCode(code).orElse(null);
            if (mapping == null || mapping.isExpired()) return null;
            longUrl = mapping.getLongUrl();
            redis.opsForValue().set(CACHE_PREFIX + code, longUrl, Duration.ofHours(24));
        }

        // 3. Async analytics event
        kafka.send("url.click.events", new ClickEvent(code, req.getRemoteAddr(),
                   req.getHeader("User-Agent"), Instant.now()));

        return longUrl;
    }
}
```

---

## Data Model

### MySQL (Primary Store)

```sql
CREATE TABLE url_mapping (
    short_code   VARCHAR(10)  PRIMARY KEY,
    long_url     TEXT         NOT NULL,
    user_id      VARCHAR(36),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   DATETIME,
    click_count  BIGINT       DEFAULT 0,
    is_active    BOOLEAN      DEFAULT TRUE,
    INDEX idx_user_id   (user_id),
    INDEX idx_expires   (expires_at),
    INDEX idx_long_url_hash (MD5(long_url(512)))  -- deduplication
);
```

### ScyllaDB (Alternative for High Write Throughput)

```cql
CREATE TABLE url_shortener.url_mapping (
    short_code  text PRIMARY KEY,
    long_url    text,
    user_id     text,
    created_at  timestamp,
    expires_at  timestamp,
    is_active   boolean
) WITH default_time_to_live = 2592000;  -- 30 days TTL
```

> ScyllaDB is preferred over MySQL for the redirect hot path due to sub-millisecond p99 reads and linear horizontal scaling.

---

## Redis Caching Strategy

```
Cache Key:  url:{shortCode}
Value:      long URL string
TTL:        matches URL expiry

On CREATE:  SET url:{code} {longUrl} EX {ttl}
On READ:    GET url:{code}
On DELETE:  DEL url:{code}
On MISS:    query DB → SET in Redis with remaining TTL
```

---

## Analytics Pipeline (Kafka → Druid)

```java
public record ClickEvent(
    String shortCode,
    String ipAddress,
    String userAgent,
    Instant clickedAt
) {}

// Kafka topic: url.click.events
// Consumer: Druid Kafka Ingestion spec
// Druid dimensions: shortCode, country, device, browser
// Druid metrics: clickCount, uniqueVisitors
```

---

## Expiry Cleanup Job

```java
@Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
public void purgeExpiredUrls() {
    int deleted = urlRepository.deleteExpiredBefore(Instant.now());
    log.info("Purged {} expired URL mappings", deleted);
}
```

---

## API Design

```
POST   /api/v1/shorten
       Body: { "longUrl": "...", "customAlias": "mylink", "ttlDays": 30 }
       Response: { "shortUrl": "https://tny.io/abc123" }

GET    /{shortCode}
       Response: 302 Found → Location: {longUrl}
                 404 Not Found (expired or unknown)

GET    /api/v1/stats/{shortCode}
       Response: { "clicks": 15423, "topCountries": [...] }

DELETE /api/v1/url/{shortCode}   (authenticated)
```

---

## Non-Functional Requirements

| Concern | Solution |
|---------|----------|
| HA for Redis | Redis Sentinel / Cluster mode |
| DB failover | MySQL read replicas + ScyllaDB multi-AZ |
| Redirect latency < 10ms | Redis-first, DB never on hot path |
| 100K RPS redirects | Horizontal Redirect Service + CDN |
| Abuse / spam URLs | URL reputation check (Google Safe Browsing API) |
| Rate limiting | Per-IP: 10 shortenings/min via Redis rate limiter |

---

## Extension Points

- **Custom domain**: `brand.tny.io/code` — route by `Host` header in Nginx
- **QR code generation**: On-demand from short URL via image service
- **A/B testing**: Same short code routes to different long URLs by user segment
- **Elasticsearch**: Full-text search on long URLs for admin/audit