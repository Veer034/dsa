# Production Incidents — AdTech AdServer Platform
### Tech Lead Interview Prep: Real Problems, Real Fixes, Real Conversations

> **Stack at a glance:** Vert.x 4.x (bid/RTB critical path) · Spring Boot 3.x (campaign management, analytics, targeting) · Java 17 · Kafka · Redis · MySQL · ScyllaDB · Kubernetes (GKE)
>
> **Scale:** ~83K requests/sec sustained, burst up to 120K/sec during prime-time ad serving.

---

## Why This Document Exists

These are real incidents. Not textbook examples. When you're in a Tech Lead interview and someone says *"tell me about a hard production problem you've solved"* — this is the kind of story you want to tell. The goal here is to help you explain these clearly to a technical interviewer, not just recite what the fix was.

For each incident the structure is:
1. **What was actually happening** (the symptom, what users / ops saw)
2. **Why it happened** (the real root cause, explained simply)
3. **How we found it** (the investigation — dashboards, tools, commands)
4. **What we fixed** (the code/config change)
5. **How to explain it in an interview** (the one-paragraph version)

---

## System Architecture (Quick Reference)

```
Advertiser DSP ──► [Vert.x AdServer] ──► Redis (freq-cap / budget check — fast path)
                          │                      │ miss
                          │               MySQL (campaign rules, budget caps)
                          │
                          ├──► Kafka: ad.impressions  (~5M events/min)
                          ├──► Kafka: ad.clicks
                          └──► Kafka: ad.conversions
                                    │
              ┌─────────────────────┴────────────────────────┐
              │                                              │
    [Analytics Ingestion]                        [Frequency Cap Updater]
    Spring Boot → ScyllaDB                       Spring Boot → Redis + MySQL
    (raw event writes)                           (counter sync)
              │
    [Reporting Service]
    ScyllaDB reads + MySQL aggregations
```

---

## Incident 1 — False Sharing: Why CPU Cache Made Our Counters Slow Each Other Down

### What Was Happening

During peak hours (09:00–11:00 AM), our AdServer bid response latency crept from **4ms up to 18ms** — more than 4× worse. But nothing external was broken. Redis was healthy. MySQL was healthy. Error rates were flat. The system was just... slower. CPU was running at 85% but we were only hitting 60% of expected throughput. The CPU was busy but not doing useful work.

### The Root Cause (Explained Simply)

To understand this, you first need to know one thing about how CPUs work: **a CPU never reads or writes a single variable directly from RAM.** It always pulls data in fixed-size chunks called **cache lines** — typically 64 bytes at a time. It loads that chunk into its own small, fast memory (L1/L2 cache) and works from there.

Now here's where the problem starts. A `long` in Java is 8 bytes. So one 64-byte cache line can hold **8 `long` fields** sitting next to each other in memory. That's exactly what our `BudgetRegistry` looked like — 5 counter fields, all declared one after the other, all packed into the same cache line.

Now imagine Thread 1 (running on Core 1) updates `totalImpressions`, and Thread 2 (running on Core 2) simultaneously updates `totalClicks`. These are logically independent. Thread 1 doesn't care about clicks. Thread 2 doesn't care about impressions. But the CPU has no idea — it only sees one cache line. The moment Thread 1 modifies its field, the CPU marks **the entire 64-byte block** as "modified on Core 1". Core 2 now sees its copy of that same cache line as stale. It has to throw it away and re-fetch from memory — even though `totalClicks` didn't change at all.

**This is false sharing.** Two threads appear to share data (they share a cache line) even though they logically share nothing. Every write by one thread forces every other thread to do a cache reload. At 83K req/sec with multiple event-loop threads all writing to this singleton, the cores were spending more time invalidating and reloading cache than doing actual work. And this was completely invisible locally — on a laptop with one CPU core running tests, there's no cross-core contention. It only showed up under production load on a real multi-core machine.

### The Problematic Code

```java
// These 5 fields are declared together → JVM packs them adjacently in memory
// All 5 fit inside one 64-byte CPU cache line
public class BudgetRegistry {
  private volatile long totalBidRequests;     // 8 bytes
  private volatile long totalImpressions;     // 8 bytes  ← same cache line
  private volatile long totalClicks;          // 8 bytes  ← same cache line
  private volatile long budgetConsumedMicros; // 8 bytes  ← same cache line
  private volatile long frequencyCapHits;     // 8 bytes  ← same cache line
}

// What happens at runtime:
// Thread 1 writes totalImpressions → CPU invalidates this entire 64-byte line on all other cores
// Thread 2 (was working on totalClicks) now has a stale cache line → must re-fetch from RAM
// Thread 2 writes totalClicks → CPU invalidates the line again → Thread 1 must re-fetch
// This ping-pong happens millions of times per second. All threads slow each other down.
```

Similarly, the Analytics Ingestion Service had a `AtomicLong[]` counter array shared across Kafka consumer threads — and array elements at neighbouring indices were also falling into the same cache lines.

### How We Found It

The first hint came from Grafana — latency spiking, but no external dependency degraded. That asymmetry pointed inward: the slowdown was happening inside the JVM, not in Redis or MySQL. We then profiled directly on the GKE node:

```bash
perf stat -e cache-misses,L1-dcache-load-misses,LLC-load-misses \
  -p $(pgrep -f adserver) -- sleep 10
```

**L1 cache miss rate: 38%.** For a workload operating mostly on in-memory counters, normal is under 5%. That number told us immediately — cores were constantly invalidating each other's cache. Async Profiler confirmed the hot frames were inside `BudgetRegistry` field writes with abnormally high cycles per operation.

### The Fix

There are only two things that matter here: fix the shared singleton fields, and fix the array counters. Everything else is noise.

**Fix 1 — Pad each field onto its own cache line using `@Contended`:**

```java
import jdk.internal.vm.annotation.Contended;

// @Contended tells the JVM: add padding around this field
// so it occupies its own 64-byte cache line, isolated from its neighbours.
// Thread 1 writing totalImpressions no longer affects Thread 2's cache for totalClicks.
public class BudgetRegistry {
  @Contended
  private volatile long totalBidRequests;

  @Contended
  private volatile long totalImpressions;

  @Contended
  private volatile long totalClicks;

  @Contended
  private volatile long budgetConsumedMicros;
}
// JVM startup flag required to allow this in production:
// -XX:-RestrictContended
```

**Fix 2 — Replace `AtomicLong[]` with `LongAdder[]` for Kafka consumer counters:**

```java
// WRONG — array[i] and array[i+1] share a 64-byte cache line
// Consumer thread 1 updating index 0 invalidates thread 2's cache for index 1
private final AtomicLong[] segmentCounters = new AtomicLong[SEGMENT_COUNT];

// CORRECT — LongAdder is designed for exactly this problem
// Internally it maintains per-thread cells that are already padded and isolated
// No cross-thread cache line sharing, even in an array
private final LongAdder[] segmentCounters = new LongAdder[SEGMENT_COUNT];
// .increment() for writes, .sum() for reads — that's it
```

That's the entire fix. Two changes, surgically applied to the two places where false sharing was happening.

### How to Explain This in an Interview

> *"We had bid latency climbing from 4ms to 18ms under peak load with no external cause — Redis and MySQL were both healthy. CPU was pegged at 85% but throughput was only at 60% of what we'd expect. We profiled the GKE node and saw L1 cache miss rate at 38%, which for an in-memory counter workload is way off. The problem was false sharing. We had a shared singleton `BudgetRegistry` with multiple `volatile long` fields declared adjacently. The JVM packs those fields next to each other in memory, so they all end up in the same 64-byte CPU cache line. When one event-loop thread wrote to `totalImpressions`, it forced every other core to invalidate its copy of that entire cache line — including `totalClicks`, which they hadn't touched. This ping-pong between cores was happening millions of times per second, killing throughput. We fixed it with `@Contended` on each field — this tells the JVM to add padding so each field lives on its own isolated cache line. We also replaced `AtomicLong[]` with `LongAdder[]` for the Kafka consumer segment counters, since LongAdder uses internally padded per-thread cells by design."*

---

## Incident 2 — Context Switching: Too Many Threads Doing Too Little Work

### What Was Happening

The **Frequency Cap Updater** service (Spring Boot, Kafka consumer) was supposed to keep up with 5 million impression events per minute. Instead, consumer lag kept growing during the day and only recovered at night when traffic dropped. At peak, we saw 128,000 context switches per second on the pod's node — about 10× normal. CPU was thrashing. And separately, the Vert.x AdServer was making HTTP calls to this service for frequency cap checks, and those calls were occasionally stalling the entire bid pipeline for hundreds of milliseconds.

### Understanding the Vert.x Event Loop — Why It Matters Here

Before the root cause, this is important context because it explains why the Vert.x impact was so severe.

Vert.x processes incoming requests using a small fixed number of **event-loop threads** — typically one per CPU core. Each event-loop thread handles thousands of requests concurrently using non-blocking I/O. The mental model is: one thread, one infinite loop, pick up the next completed I/O event, run its handler, move on. It's extremely efficient as long as every handler returns quickly.

The hard rule is: **you must never block an event-loop thread.** If a handler blocks — even for a few hundred milliseconds — that one thread stops processing all requests behind it. Not just the slow one. All of them. Because it's a single thread running a loop, and the loop is stuck.

In our case, the Vert.x AdServer was making a **synchronous HTTP call** to the Frequency Cap Updater directly on the event-loop thread:

```java
// WRONG — this blocks the event-loop thread while waiting for HTTP response
router.get("/bid").handler(ctx -> {
        boolean capped = httpClient.get("/freq-cap/" + userId).body(); // BLOCKS HERE
        ctx.response().end(buildBidResponse(capped));
        });
// While this one request waits for the HTTP response,
// ALL other incoming bid requests on this event loop are queued behind it.
// If the Frequency Cap service is slow (it was), bids pile up fast.
```

This is why the Frequency Cap Updater's slowness had an outsized impact — it didn't just slow down frequency cap checks, it stalled the entire bid path on each event loop that made the call.

### The Root Causes in the Frequency Cap Updater

**Problem 1 — Way too many threads for the CPU.** The service had 32 Kafka consumer threads on a 4-vCPU pod. That's 8 threads per core. Most were blocking on Redis or MySQL I/O, so the OS kernel spent most of its time context-switching between them looking for one that was actually runnable. Each context switch costs ~1–10 microseconds and flushes CPU caches. At 32 threads, we were burning CPU on the switching itself, not the work.

**Problem 2 — One big lock serializing everything.** All 32 threads shared a single `synchronized` block to prevent duplicate writes. Even when a thread got CPU time, it immediately blocked on the lock waiting for the other 31.

```java
// PROBLEMATIC — 32 threads all trying to enter this one lock
private final Object processLock = new Object();

public void processImpression(ImpressionEvent event) {
synchronized (processLock) {  // 31 threads wait here while 1 runs
        redisTemplate.opsForValue().increment(buildCapKey(event)); // sync, blocking
        jdbcTemplate.update(INSERT_SQL, ...);                      // sync, blocking
        }
        }
```

### How We Found It

Grafana showed `node_context_switches_total` at 128K/sec. The `jvm_threads_states_threads{state="blocked"}` panel showed 20–26 of the 32 consumer threads in `BLOCKED` state at any given moment — not waiting on I/O, blocked on the lock. We confirmed with a thread dump:

```bash
jstack <pid> | grep -A3 "BLOCKED" | head -60
# Output: 22 threads blocked on synchronized(processLock)

pidstat -w -p <pid> 1 10
# voluntary_ctxt_switches:    85,000/sec
# nonvoluntary_ctxt_switches: 43,000/sec
```

The Kafka consumer lag panel showed lag growing linearly all day — the service was processing slower than events were arriving.

### The Fix

Two changes — one on each side of the problem.

**Fix 1 — Match thread count to vCPUs. Use async Redis. Batch MySQL.**

The core insight: a thread blocked on I/O is not doing work, it's just waiting. So having 32 threads hoping one of them can sneak past the I/O wait is the wrong model. The right model is: fewer threads, non-blocking I/O so threads never wait at all, and batch MySQL so you're not writing to the DB on every single event.

```java
@KafkaListener(
        topics = "ad.impressions",
        concurrency = "4",  // one per vCPU — no over-subscription
        containerFactory = "kafkaListenerContainerFactory"
)
public void processImpression(ImpressionEvent event) {
        // Async Redis — fire and forget, thread does not wait for response
        redisAsyncCommands.incr(buildCapKey(event));

        // Don't touch MySQL here at all — accumulate locally and flush in batch
        batchFlusher.record(event.getCampaignId(), event.getUserId());
        }

// Flush to MySQL every 5 seconds — one batch write instead of 83K individual writes
@Scheduled(fixedDelay = 5000)
public void flushToMySQL() {
        Map<String, LongAdder> snapshot = new ConcurrentHashMap<>(localCounters);
        localCounters.clear();

        List<Object[]> batchArgs = snapshot.entrySet().stream()
        .map(e -> new Object[]{
        extractCampaign(e.getKey()),
        extractUser(e.getKey()),
        e.getValue().sum()
        })
        .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(
        "INSERT INTO impression_counts (campaign_id, user_id, count) VALUES (?,?,?) " +
        "ON DUPLICATE KEY UPDATE count = count + VALUES(count)",
        batchArgs
        );
        }
```

Note on the local accumulation: this runs inside each pod separately — 25 pods each accumulating independently and flushing with `ON DUPLICATE KEY UPDATE count = count + VALUES(count)`. MySQL sums them correctly across all pods. No cross-pod coordination needed.

**Fix 2 — Fix the Vert.x event loop: never block, always async with a timeout.**

```java
// CORRECT — the event-loop thread never waits here
// It registers a callback and immediately moves on to the next request
router.get("/bid").handler(ctx -> {
        webClient.get(8080, "freq-cap-service", "/check/" + userId)
        .timeout(2)  // 2ms timeout — in RTB, slow = useless anyway
        .send()
        .onSuccess(resp -> ctx.response().end(buildBidResponse(resp.bodyAsJson())))
        .onFailure(err  -> ctx.response().end(buildBidResponse(false))); // default: serve the ad
        });
// The event loop thread fires the HTTP request and immediately picks up the next bid request.
// When the HTTP response comes back (or times out), the callback runs.
// No blocking. No queuing behind one slow request.
```

The 2ms timeout is intentional — in real-time bidding, if the frequency cap check takes longer than 2ms, we default to serving the ad (uncapped). A lost frequency cap check is a minor business risk. A stalled event loop is a catastrophic latency failure.

### How to Explain This in an Interview

> *"We had two problems feeding into each other. The Frequency Cap Updater had 32 Kafka consumer threads on a 4-vCPU pod. Most threads were blocking on Redis and MySQL I/O, so the OS was burning CPU on context switches — we measured 128K switches per second. On top of that, all 32 threads shared a single synchronized lock, so even the ones that got CPU time were just waiting for the lock. We fixed it by dropping concurrency to 4 threads matching the vCPU count, switching to async Redis so threads never block on I/O, and batching MySQL writes on a 5-second flush — each pod accumulates locally and flushes with an upsert, so all 25 pods write independently without any cross-pod coordination. The second problem was on the Vert.x side. Vert.x uses a small number of event-loop threads, each handling thousands of requests concurrently. The rule is you must never block an event loop — if one handler blocks, every request queued behind it on that thread stalls. We were making a synchronous HTTP call to the Frequency Cap service on the event loop. We changed it to async with a 2ms timeout and a default-open fallback — the event loop fires the request and immediately picks up the next bid."*

---

## Incident 3 — Cache Stampede: 200 Campaigns Expiring at Once Brought Down MySQL

### What Was Happening

At 9:00 AM peak, the Targeting Service latency went from 8ms to **4.2 seconds** in less than a minute. MySQL connections maxed out instantly. Everything downstream of targeting — bid decisions, pacing, reporting — started queuing up. The system looked like it was about to fall over.

### The Root Cause (Explained Simply)

A **cache stampede** (also called a thundering herd) is what happens when many concurrent requests all miss the cache at the same moment and all go to the database simultaneously.

Here's exactly what happened: when the Targeting Service started up, it loaded all ~200 active campaign targeting rules from MySQL and cached them in Redis with a **fixed 5-minute TTL**. Because they were all loaded at the same time, they all expired at the same time, 5 minutes later. Every incoming bid request (83,000/sec) simultaneously found a cache miss for targeting rules and raced to MySQL to reload them. MySQL's connection pool had 20 connections. It was immediately saturated. Connections queued. Threads stacked up.

This was made worse by a coincidence: the Kafka Analytics consumer was processing an overnight backlog at the same time, which was pushing rapid budget counter updates to Redis, spiking Redis CPU and adding 10–20ms latency to Redis GETs — slowing down cache miss recovery even further.

### How We Found It

Grafana's `hikaricp_connections_pending` panel spiked from 0 to 18 (out of 20 max) in a 200ms window — one of the clearest stampede signals you can see. At the same moment, `redis_keyspace_misses_total` spiked 400×. The P99 latency heatmap showed the exact timestamp of the cliff. We confirmed in staging by manually expiring all campaign keys simultaneously — same crash, reproducible.

### The Fix

**Fix 1 — Only one thread per campaign key loads from MySQL at a time (request coalescing):**

```java
@Service
public class TargetingRuleCache {

  private final ConcurrentHashMap<String, CompletableFuture<TargetingRule>> inflight =
          new ConcurrentHashMap<>();

  public CompletableFuture<TargetingRule> get(String campaignId) {
    // Fast path: check Redis first
    TargetingRule cached = redis.opsForValue().get("rule:" + campaignId);
    if (cached != null) return CompletableFuture.completedFuture(cached);

    // If someone else is already loading this key, join that future — don't start a new DB call
    return inflight.computeIfAbsent(campaignId, id ->
            CompletableFuture
                    .supplyAsync(() -> mysql.findByCampaignId(id), mysqlExecutor)
                    .whenComplete((rule, ex) -> {
                      inflight.remove(id);
                      if (rule != null) {
                        // TTL with jitter: 5 min ± 30 seconds
                        // Prevents all 200 campaigns from expiring at the same instant
                        long jitterSecs = ThreadLocalRandom.current().nextLong(-30, 30);
                        redis.opsForValue().set(
                                "rule:" + id, rule,
                                Duration.ofSeconds(300 + jitterSecs)
                        );
                      }
                    })
    );
    // If 500 threads all miss for the same campaignId at once,
    // only 1 MySQL query fires. The other 499 wait on that same future.
  }
}
```

**Fix 2 — Add TTL jitter to every Redis cache entry:**

The root cause was all keys expiring together. Adding random jitter at write time prevents this permanently. This is now an ADR (Architecture Decision Record) — no fixed TTL under 15 minutes without jitter.

**Fix 3 — Use Caffeine `refreshAfterWrite` in the Vert.x hot path:**

```java
// Caffeine refreshes entries in the background BEFORE they expire
// No thread ever blocks on a cache miss for a hot key
LoadingCache<String, TargetingRule> ruleCache = Caffeine.newBuilder()
        .maximumSize(50_000)
        .refreshAfterWrite(Duration.ofMinutes(4))  // proactive async refresh
        .expireAfterWrite(Duration.ofMinutes(6))   // hard fallback expiry
        .build(campaignId -> targetingServiceClient.getRule(campaignId));
```

**Fix 4 — Fail fast on HikariCP instead of queuing for 30 seconds:**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 3000   # 3 seconds, not 30 — fail fast
      leak-detection-threshold: 5000
```

If we fail fast, threads get an error quickly, back off, and the stampede self-heals in seconds instead of 30-second waves of thread accumulation.

### How to Explain This in an Interview

> *"At 9 AM one day, targeting service latency went from 8ms to over 4 seconds. MySQL connection pool was immediately at max. The cause was a cache stampede — all 200 campaign targeting rules had been loaded into Redis at startup with a fixed 5-minute TTL. They all expired at exactly the same time, and at 83K req/sec, hundreds of threads simultaneously missed the cache and raced to MySQL. Twenty connections, thousands of requests. We fixed it in three ways: request coalescing with a `ConcurrentHashMap<String, CompletableFuture>` so only one MySQL query fires per cache key regardless of how many threads miss; mandatory TTL jitter so keys can never all expire together; and `refreshAfterWrite` in Caffeine so the hot path proactively refreshes before expiry and no thread ever sees a miss on a warm key. We also tightened HikariCP connection timeout from 30 seconds to 3 so the blast radius self-heals fast."*

---

## Incident 4 — GC Pressure: Kafka Consumer Allocating Its Way Into an OOMKill

### What Was Happening

The Analytics Ingestion Service (Spring Boot + Kafka consumer → ScyllaDB) was running fine for hours, then gradually getting slower, then occasionally getting **OOMKilled by Kubernetes**. GC pauses were growing over time — 800ms, then 1.1 seconds, then 1.4 seconds, then a 1.8-second full stop-the-world pause. Each pod restart fixed it for a few hours, then it happened again. The Grafana heap chart looked like a sawtooth where each tooth was getting taller — a classic sign that something was leaking into Old Gen.

### The Root Cause (Explained Simply)

Three independent problems, all pushing the same direction:

**Problem 1 — Massive object allocation per Kafka message.** At 83K events/sec across 4 consumer threads, every message created: a new `ImpressionEvent` object (Jackson deserialization), multiple `String` objects, a boxed `Long` from `HashMap.compute()`. Eden space filled in ~80ms. During Kafka burst (catching up on a backlog), it filled in under 20ms. Objects promoted to Old Gen faster than GC could collect them.

**Problem 2 — Unbounded ScyllaDB async queue.** We were calling `executeAsync()` on ScyllaDB with no backpressure. If ScyllaDB was briefly throttled (compaction, node coordination), pending futures accumulated in heap — each holding a full event payload. Thousands of futures × full event objects = Old Gen bloat.

**Problem 3 — Prometheus label cardinality explosion.** We were registering a Prometheus counter with `campaign_id` as a label. We had 50,000+ unique campaign IDs. That created 50,000+ Prometheus time series in memory, growing the Micrometer registry by ~1.8GB of heap — directly competing with the application.

### How We Found It

Grafana was the primary surface. The heap sawtooth with increasing amplitude was visible for hours before the OOMKill. After the kill, Kubernetes events showed `OOMKilling`. We ran:

```bash
# Async Profiler allocation flamegraph:
java -jar async-profiler.jar -e alloc -d 30 -f alloc.html <pid>
# Top allocators: ImpressionEvent 34%, byte[] Jackson deserialization 28%,
#                 Long autoboxing in HashMap.compute() 18%

# Prometheus cardinality check:
curl http://localhost:8080/actuator/prometheus | grep "ad_impressions_processed" | wc -l
# Output: 51,847 lines — one metric line per campaign ID
```

GC logs (pre-configured in the JVM) showed Full GC duration increasing each cycle — confirming Old Gen was never fully cleaning.

### The Fix

**Fix 1 — Eliminate autoboxing with a primitive map:**

```java
// BEFORE: HashMap<String, Long> — allocates a Long object wrapper per update
counters.compute(event.getCampaignId(), (k, v) -> v == null ? 1L : v + 1);
//                                                 ^^^^ boxes long → Long every time

// AFTER: Eclipse Collections primitive map — stores raw long, no boxing
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;

private final ObjectLongHashMap<String> campaignCounters = new ObjectLongHashMap<>();
        campaignCounters.addToValue(event.getCampaignId(), 1L); // zero object allocation
```

**Fix 2 — Reuse the deserialization object instead of allocating a new one per message:**

```java
// One ImpressionEvent object per consumer thread, reused for every message
private static final ThreadLocal<ImpressionEvent> REUSABLE_EVENT =
        ThreadLocal.withInitial(ImpressionEvent::new);

private static final ObjectReader EVENT_READER = MAPPER.readerForUpdating(null);

@KafkaListener(topics = "ad.impressions", concurrency = "4")
public void consume(ConsumerRecord<String, byte[]> record) {
        ImpressionEvent event = REUSABLE_EVENT.get();
        EVENT_READER.withValueToUpdate(event).readValue(record.value());
        // Fields are overwritten in place — no new object allocated
        processEvent(event);
        }
```

**Fix 3 — Add a semaphore to limit in-flight ScyllaDB writes:**

```java
// Max 500 async writes in flight at a time
// If ScyllaDB throttles, this blocks the consumer thread instead of accumulating futures
private final Semaphore scyllaPermits = new Semaphore(500);

public void writeToScylla(ImpressionEvent event) {
        scyllaPermits.acquire(); // slows the consumer down — deliberate backpressure
        scyllaSession.executeAsync(buildStatement(event))
        .whenComplete((rs, ex) -> scyllaPermits.release());
        }
```

**Fix 4 — Fix Prometheus label cardinality:**

```java
// BEFORE: 50,000 unique values for campaign_id = 50,000 time series in memory
Counter.builder("ad.impressions.processed")
        .tag("campaign_id", event.getCampaignId()) // NEVER DO THIS

// AFTER: only categorical labels with bounded cardinality
        Counter.builder("ad.impressions.processed")
        .tag("advertiser_tier", event.getAdvertiserTier()) // "premium", "standard", "house"
        .tag("ad_format", event.getAdFormat())             // "display", "video", "native"
        .register(registry)
        .increment();

// Per-campaign counts go to ScyllaDB for reporting — not Prometheus
```

**Fix 5 — Right GC algorithm for each service type:**

```bash
# Vert.x AdServer — sub-millisecond pauses are critical. Use ZGC.
-XX:+UseZGC -Xms4g -Xmx4g -XX:SoftMaxHeapSize=3500m

# Spring Boot Analytics — throughput matters more than pause time. G1GC.
-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:G1HeapRegionSize=16m
-XX:InitiatingHeapOccupancyPercent=40
-Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=5,filesize=20m
```

**On Kubernetes memory limits — the thing most people get wrong:**

A common mistake: setting `limits.memory` equal to `-Xmx`. But the JVM uses memory outside the heap too:

```
Pod Memory Limit = Xmx (heap)
                 + Metaspace         (~256–512 MB)
                 + Direct/Off-heap   (Netty/Vert.x: ~512 MB; Kafka client: ~200 MB)
                 + Thread stacks     (thread count × 512 KB)
                 + JIT code cache    (~256 MB)
                 + Safety margin     (10%)

# For Xmx=4g: 4096 + 512 + 200 + ~2 + 256 + ~520 = ~5,800 MB → set limit to 6Gi
```

If `limits.memory = Xmx`, Kubernetes will OOMKill the pod for off-heap usage even when the GC log shows heap is fine.

### How to Explain This in an Interview

> *"Our Analytics Ingestion Service was getting OOMKilled every few hours. Heap sawtooth in Grafana was the first signal — each GC cycle cleaning less and less, Full GC pauses growing from 800ms to 1.8 seconds. We found three root causes with Async Profiler and Prometheus cardinality checks. First, we were allocating a new `ImpressionEvent` object per Kafka message at 83K/sec — Eden space filling every 80ms. We fixed that with `ThreadLocal` object reuse and Jackson's `readerForUpdating`. Second, we had unbounded ScyllaDB `executeAsync` calls — during compaction, futures accumulated in heap holding full event payloads. We added a semaphore for deliberate backpressure. Third, we had 50,000+ Prometheus time series from tagging by campaign ID — 1.8GB of Micrometer registry in heap competing with the application. We replaced high-cardinality labels with categorical ones. Also, we were setting Kubernetes memory limits equal to Xmx and getting OOMKilled by off-heap growth — fixed by adding 2.5G headroom above Xmx for Metaspace, Netty buffers, and thread stacks."*

---

## Cross-Incident Summary — The Patterns

| Problem | Simple Description | Key Fix |
|---|---|---|
| **False Sharing** | Two threads stepping on each other's CPU cache even though they're updating different variables | `@Contended` on shared fields, `LongAdder` over `AtomicLong[]` |
| **Context Switching** | Too many threads for the CPU; OS wastes time switching instead of working | Thread count = vCPU count; async Redis; batch MySQL writes per pod |
| **Cache Stampede** | Many keys expire together → everyone races to DB at same moment | TTL jitter; request coalescing; `refreshAfterWrite` |
| **GC Pressure / OOMKill** | Allocating too many short-lived objects; unbounded queues; Prometheus cardinality | Object reuse; backpressure semaphores; fix label cardinality; Xmx + 2.5G headroom |

---

## Grafana / Prometheus Alert Reference

### Key Alerts (Production Tuned)

```yaml
# False Sharing / CPU
- alert: HighCPUWithLowThroughput
  expr: rate(process_cpu_seconds_total[2m]) > 0.80
    and rate(http_server_requests_seconds_count[2m]) < 50000
  # Signal: CPU busy but not serving requests → internal contention

# Context Switching
- alert: ExcessiveContextSwitches
  expr: rate(node_context_switches_total[1m]) > 50000
  # Signal: thread count too high for vCPUs, or heavy I/O blocking

- alert: HighVCPUSteal
  expr: rate(node_cpu_seconds_total{mode="steal"}[5m]) > 0.05
  # Signal: hypervisor-level contention on the GKE node

# Cache Stampede
- alert: RedisCacheMissSpike
  expr: rate(redis_keyspace_misses_total[10s]) > 500
  # Signal: cache stampede potentially in progress

- alert: HikariPoolSaturation
  expr: hikaricp_connections_pending / hikaricp_connections_max > 0.7
  # Signal: DB fan-out from a stampede or missing cache

# GC Pressure
- alert: GCPauseP99High
  expr: histogram_quantile(0.99, rate(jvm_gc_pause_seconds_bucket[5m])) > 0.2
  # Signal: bid SLA at risk — P99 GC pause > 200ms

- alert: HeapNearExhaustion
  expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
  # Signal: OOMKill imminent

- alert: OldGenGrowing
  expr: increase(jvm_gc_live_data_size_bytes[1h]) > 500000000
  # Signal: something not being GC'd — potential heap leak
```

---

## Architecture Decisions Made After These Incidents

These are the guardrails we put in place so the same class of problem can't quietly reach production again:

- **ADR-001:** No blocking I/O on a Vert.x event loop — ever. `BlockedThreadChecker` at 200ms, alert to PagerDuty. Violation = P1 bug.
- **ADR-002:** Kafka consumer `concurrency` must not exceed the pod's vCPU count. MySQL writes from Kafka must be batched, never inline.
- **ADR-003:** All Redis TTLs under 15 minutes must include ±20% random jitter. No exceptions. Code review checklist item.
- **ADR-004:** Any field updated by more than one thread must use `@Contended` or `LongAdder`. Bare `AtomicLong` fields in shared singletons are banned.
- **ADR-005:** No Prometheus label with cardinality > 100 without Architecture Review Board approval. Campaign/user granularity belongs in ScyllaDB.
- **ADR-006:** Pod `limits.memory` = `Xmx + 2.5Gi` minimum. Setting limits equal to Xmx is a deployment review failure.
- **ADR-007:** All ScyllaDB async write paths must be gated by a semaphore. Unbounded `executeAsync` is banned.

---

*Documented from production experience. Stack: Java 17 / Vert.x 4.x / Spring Boot 3.x / Apache Kafka / Redis / MySQL / ScyllaDB / Kubernetes 1.28+ / Prometheus + Grafana.*