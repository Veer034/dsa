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
# Incident 1 — False Sharing: Why CPU Cache Made Our Counters Slow Each Other Down

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

## What Was Happening

During peak hours (08:00–11:00 PM), our AdServer bid response latency crept from **4ms up to 18ms** — more than 4× 
worse. But nothing external was broken. Redis was healthy. MySQL was healthy. Error rates were flat. The system was just... slower. CPU was running at 85% but we were only hitting 60% of expected throughput. The CPU was busy but not doing useful work.

---

## Background — How CPU Caches Work

To understand this incident, you need to know one thing about how CPUs work: **a CPU never reads or writes a single variable directly from RAM.** It always pulls data in fixed-size chunks called **cache lines** — typically 64 bytes at a time. It loads that chunk into its own small, fast memory (L1/L2/L3 cache) and works from there.

There are three levels of CPU cache, each progressively larger but slower:

- **L1 Cache** — Fastest, closest to the CPU core. Extremely small (16KB–128KB per core). Stores the most immediate data the CPU is currently executing on.
- **L2 Cache** — Larger but slightly slower than L1. Typically 256KB to a few MB per core. Stores data the CPU is likely to need next.
- **L3 Cache** — Largest and slowest cache level, but still much faster than RAM. Shared across all CPU cores (a few MB to 100+ MB). Holds data being passed between different cores.

A `long` in Java is 8 bytes. So one 64-byte cache line can hold **8 `long` fields** sitting next to each other in memory.

---

## Root Cause — False Sharing

### What is False Sharing?

When two threads on different CPU cores update **logically independent fields** that happen to live on the **same 64-byte cache line**, every write by one thread forces every other core to invalidate and re-fetch that entire cache line — even though the other thread's field didn't change at all.

**Two threads appear to share data (they share a cache line) even though they logically share nothing.**

### Why We Had This Problem — The BudgetRegistry

**`campaignId` is our Kafka partition key.** This means one pod owns all events for a given campaign completely:

```
Campaign X → always Partition 3 → always Pod 2
Campaign Y → always Partition 7 → always Pod 5
```

Each campaign on each pod has its own `BudgetRegistry` instance — a pod-local, per-campaign write buffer. Instead of calling Redis on every single Kafka event at 20,000 events/sec per campaign, the registry accumulates counts locally and flushes aggregated values to Redis every 5 seconds in a single batch write:

```
Without registry:  20,000 Kafka events/sec → 20,000 Redis calls/sec  ✗
With registry:     20,000 Kafka events/sec → 1 Redis call per 5 sec  ✓
```

This is a standard **write-buffering pattern** — accumulate locally, sync periodically.

### The Problematic Code

```java
// BudgetRegistry — pod-local, per-campaign write buffer
// Accumulates counts locally across multiple Kafka consumer threads
// Flushes aggregated values to Redis every 5 seconds
//
// These 4 fields are declared together → JVM packs them adjacently in memory
// All 4 fit inside one 64-byte CPU cache line
public class BudgetRegistry {
    private volatile long totalBidRequests;     // 8 bytes ─┐
    private volatile long totalImpressions;     // 8 bytes  │ all packed into
    private volatile long totalClicks;          // 8 bytes  │ same 64-byte cache line
    private volatile long budgetConsumedMicros; // 8 bytes ─┘
}
```

### Why Multiple Threads Hit the Same Registry

Each campaign's pod runs **multiple concurrent Kafka consumer threads** across separate topics — all updating the same `BudgetRegistry` instance simultaneously:

```
ad.impressions consumer thread  → updates totalImpressions + budgetConsumedMicros
ad.clicks consumer thread       → updates totalClicks
ad.bids consumer thread         → updates totalBidRequests

All three threads → same BudgetRegistry instance → same 64-byte cache line
```

Spring Kafka's `concurrency` setting makes this worse — multiple threads can consume the same partition simultaneously for higher throughput:

```java
@KafkaListener(
    topics = "ad.impressions",
    concurrency = "4"  // 4 threads, all updating same BudgetRegistry
)
public void handleImpression(ImpressionEvent event) {
    budgetRegistry.addBudgetConsumed(event.getBidPriceMicros());
    budgetRegistry.incrementImpressions();
}
```

### What Happens at Runtime

```
Thread 1 writes totalImpressions
  → CPU marks entire 64-byte cache line as "modified on Core 1"
  → Cores 2, 3, 4 see their copy as stale → must re-fetch from RAM

Thread 2 (was working on totalClicks on Core 2)
  → cache line invalidated → re-fetches from RAM
  → writes totalClicks → now Core 1, 3, 4 must re-fetch

Thread 3 (was working on budgetConsumedMicros on Core 3)
  → cache line invalidated again → re-fetches from RAM
  → writes budgetConsumedMicros → all other cores must re-fetch

This ping-pong happens millions of times per second.
Cores spend more time on cache coherence than actual work.
```

### Important — Why `volatile` Does NOT Fix This

A common misconception is that `volatile` prevents false sharing. It does not:

```
volatile guarantees:
  → visibility  (other threads see latest value)     ✓
  → ordering    (no instruction reordering)          ✓

volatile does NOT control:
  → which cache line a field lives on                ✗
  → whether fields are packed adjacently in memory   ✗
```

False sharing is a **CPU-level problem** about cache line placement. `volatile`, `AtomicLong`, and `synchronized` are all Java-level concepts — they have no control over which cache line a field occupies. Only `@Contended` fixes it.

---

## How We Found It

The first hint came from Grafana — latency spiking, but no external dependency degraded. That asymmetry pointed inward: the slowdown was happening inside the JVM, not in Redis or MySQL. We then profiled directly on the GKE node:

```bash
perf stat -e cache-misses,L1-dcache-load-misses,LLC-load-misses \
  -p $(pgrep -f adserver) -- sleep 10
```

**L1 cache miss rate: 38%.** For a workload operating mostly on in-memory counters, normal is under 5%. That number told us immediately — cores were constantly invalidating each other's cache. Async Profiler confirmed the hot frames were inside `BudgetRegistry` field writes with abnormally high cycles per operation.

---

## The Fix

Two changes. Nothing else.

### Fix 1 — Pad Each Field onto Its Own Cache Line Using `@Contended`

```java
import jdk.internal.vm.annotation.Contended;

// @Contended tells the JVM: add 56 bytes of padding around this field
// so it occupies its own isolated 64-byte cache line.
//
// Thread 1 writing totalImpressions no longer invalidates
// Thread 2's cache line for totalClicks — they are on separate cache lines.
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

**What `@Contended` does physically:**

```
Before @Contended — all fields on same cache line:
[totalBidRequests][totalImpressions][totalClicks][budgetConsumedMicros] ← 64 bytes, one cache line

After @Contended — each field on its own cache line:
[totalBidRequests    + 56 bytes padding] ← own cache line
[totalImpressions   + 56 bytes padding] ← own cache line
[totalClicks        + 56 bytes padding] ← own cache line
[budgetConsumedMicros + 56 bytes padding] ← own cache line

Thread 1 writes totalImpressions → only that cache line invalidated
Thread 2's cache line for totalClicks → untouched, no re-fetch needed
```

### Fix 2 — Replace `AtomicLong[]` with `LongAdder[]` for Kafka Consumer Counters

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

---

## Safe Flush to Redis — Atomic Reset Pattern

When the scheduler flushes local counts to Redis every 5 seconds, a naive read-then-reset creates a race condition:

```
// WRONG — race condition
long impressions = totalImpressions; // Thread 2 adds 50 more here
totalImpressions = 0;                // those 50 events are lost forever
```

The fix is `getAndSet(0)` — atomically swaps the current value with 0, so no events are lost between read and reset:

```java
// Scheduler runs every 5 seconds
public void flushToRedis(RedisClient redis) {
        long impressions = totalImpressions.getAndSet(0);
        long clicks = totalClicks.getAndSet(0);
        long budget = budgetConsumedMicros.getAndSet(0);
        long bids = totalBidRequests.getAndSet(0);

        try {
            redis.incrBy("campaign:X:impressions", impressions);
            redis.incrBy("campaign:X:clicks", clicks);
            redis.incrBy("campaign:X:budget", budget);
            redis.incrBy("campaign:X:bids", bids);
        } catch (Exception e) {
            // Redis write failed - restore counters
            totalImpressions.addAndGet(impressions);
            totalClicks.addAndGet(clicks);
            budgetConsumedMicros.addAndGet(budget);
            totalBidRequests.addAndGet(bids);
            throw e;
           }
        }
```

---

## Three-Layer Architecture — Each Layer Has a Different Job

```
BudgetRegistry (pod-local, per-campaign)
  → write buffer, nanosecond speed
  → accumulates counts across Kafka consumer threads
  → flushes to Redis every 5 seconds
  → purpose: avoid 20,000 Redis calls/sec per campaign

Redis (global, approximate)
  → near-realtime aggregated counts across all pods
  → used for frequency cap and budget enforcement decisions
  → updated by periodic registry flushes

Flink / Analytics Pipeline
  → exact, billing-accurate counts
  → used for dashboards, invoicing, campaign reporting
  → purpose: authoritative historical record
```

Flink and Redis are not replacements for the registry — they operate at completely different time granularities and serve different consumers.

---

## Pod Restart — Rehydration

Since `campaignId` is the partition key, a pod restart means the registry loses its local state. On startup, the pod rehydrates from Redis — getting last known counts — and resumes. Worst case: 5 seconds of local state lost between the last flush and the restart. Acceptable for our SLA.

---

## Results

| Metric | Before Fix | After Fix |
|---|---|---|
| Bid response latency (peak) | 18ms | 4ms |
| L1 cache miss rate | 38% | <5% |
| CPU throughput | 60% of expected | 100% of expected |
| Redis calls per campaign/sec | N/A (direct) | ~0.2 (one per 5s) |

---

## Interview Answer

> During peak viewing hours — 8-11 PM when users are actively streaming — our AdServer bid response latency climbed from 4ms to 18ms with no external cause. Redis and MySQL were both healthy. CPU was at 85% but throughput was only 60% of expected.
We profiled the GKE node and saw L1 cache miss rate at 38%, way off from the normal under 5% for an in-memory counter workload. That immediately told us cores were constantly invalidating each other's caches.
The problem was false sharing in our BudgetRegistry — a pod-local, per-campaign write buffer that accumulates impression/click counts locally across multiple Kafka consumer threads, then flushes to Redis every 5 seconds. This avoids hammering Redis with 20,000 individual calls per campaign per second.
The issue was that all four counter fields — totalImpressions, totalClicks, totalBudgetConsumed, totalBidRequests — were packed into the same 64-byte CPU cache line. When the impressions consumer thread wrote to one field, it invalidated that entire cache line on every other core, forcing them to re-fetch from RAM even though their own fields hadn't changed. At peak traffic (8-11 PM), this ping-pong was happening millions of times per second.
The fix was @Contended on each field — it tells the JVM to add 56 bytes of padding so each field gets its own isolated cache line. We also replaced AtomicLong[] with LongAdder[] for the Kafka consumer counters since LongAdder internally pads its cells. L1 miss rate dropped back below 5%, latency back to 4ms, and we hit 100% of expected throughput.

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



If we fail fast, threads get an error quickly, back off, and the stampede self-heals in seconds instead of 30-second waves of thread accumulation.

### How to Explain This in an Interview

> *"At 9 AM, targeting service latency spiked from 8ms to over 4 seconds. MySQL connection pool maxed out instantly. The cause was a cache stampede — all 200 campaign targeting rules had been loaded at startup with a fixed 5-minute TTL, so they all expired simultaneously. At 83K req/sec across 5 pods, hundreds of threads in each pod simultaneously missed the cache and raced to MySQL.
We fixed it in three complementary ways:
First, request coalescing — using a ConcurrentHashMap<String, CompletableFuture> within each pod, so when 500 threads all miss for the same campaign, only 1 MySQL query fires and the other 499 wait on that future. This helps within a single pod.
Second, TTL jitter — instead of all keys expiring at exactly T=5:00, we add random jitter (±30 seconds) at write time. So 200 campaigns expire at staggered times across all pods, preventing synchronized expiry. This is now mandatory for any TTL under 15 minutes.
Third and most importantly, Caffeine refreshAfterWrite — the hot path uses a local Caffeine cache with refreshAfterWrite(4 minutes) and expireAfterWrite(6 minutes). At 4 minutes, Caffeine triggers an async background refresh from the targeting service, so the value is fresh before the hard expiry at 6 minutes. No thread ever blocks on a cache miss for a warm key.
The combination prevents both within-pod stampedes and cross-pod synchronized expiry. We also tightened HikariCP timeout from 30 seconds to 3 seconds so any stampede that does happen self-heals fast."*

---

# Incident 4 — GC Pressure: Kafka Consumer Allocating Its Way Into an OOMKill

## What Was Happening

The **Analytics Ingestion Service** (Spring Boot + Kafka consumer → ScyllaDB) was running fine for hours, then gradually getting slower, eventually getting **OOMKilled by Kubernetes**.

### Symptoms Observed

- GC pauses growing over time: **800ms → 1.1s → 1.4s → 1.8s** (full stop-the-world)
- Heap chart in Grafana showed **sawtooth pattern with increasing amplitude** — classic Old Gen leak
- Pod restart fixed it temporarily (a few hours), then it happened again
- At peak: **83K events/sec** across 4 concurrent Kafka consumer threads

---

## Root Cause Analysis

**Three independent problems, all pushing the same direction:**

### Problem 1: Massive Object Allocation Per Kafka Message

```java
// KAFKA CONSUMER — Spring Boot
@KafkaListener(topics = "ad.impressions", concurrency = "4")
public void consume(ConsumerRecord<String, byte[]> record) {
    // ISSUE: Every message triggers deserialization
    // At 83K events/sec, this creates massive Eden pressure
    ImpressionEvent event = MAPPER.readValue(record.value(), ImpressionEvent.class);
    //                       ^^^^^^
    //                       New object allocated here
    //                       + new String objects from Jackson parsing
    //                       + temporary byte[] arrays
    
    processEvent(event);
}
```

**The Numbers:**
- 83,000 events/sec = 83K objects per second
- Each `ImpressionEvent` object: ~200 bytes
- Jackson temporary allocations: +100-150 bytes per message
- **Eden space filled in ~80ms** during normal load
- **Eden space filled in ~20ms** during Kafka backlog catch-up

Objects promoted to Old Gen faster than GC could collect them.

---

### Problem 2: Unbounded ScyllaDB Async Queue (Backpressure Missing)

```java
// SCYLLADB WRITER — No backpressure
private final CqlSession scyllaSession;

public void writeToScylla(ImpressionEvent event) {
    // ISSUE: executeAsync with no limit
    // If ScyllaDB throttles (compaction, node coordination),
    // futures accumulate in heap with full event payloads
    scyllaSession.executeAsync(buildStatement(event))
            .whenComplete((rs, ex) -> {
                // callback here
            });
    // Returns immediately — doesn't wait for write to complete
    // Thousands of pending futures = Old Gen bloat
}
```

**What Happens During ScyllaDB Compaction:**

```
T=0s    ScyllaDB doing compaction (paused writes)
T=0s    Consumer keeps calling executeAsync()
        ├─ Future 1 created (event payload in memory)
        ├─ Future 2 created (event payload in memory)
        ├─ Future 3 created (event payload in memory)
        ├─ ...
        └─ Future 5000 created in 1 second

T=1s    ScyllaDB resumes
        Futures finally complete, but 5000 × full event objects 
        were held in Old Gen for that 1 second
        
GC tries to clean, but new events keep arriving faster
→ Old Gen never gets fully cleaned
→ Full GC pauses get longer each cycle
```

---

### Problem 3: Prometheus Label Cardinality Explosion

```java
// PROMETHEUS METRICS — High cardinality tag
// ISSUE: campaign_id has 50,000+ unique values

Counter.builder("ad.impressions.processed")
        .tag("campaign_id", event.getCampaignId())  // ✗ NEVER DO THIS
        .register(registry)
        .increment();

// Result:
// - 50,000+ unique campaign IDs
// - = 50,000+ separate Prometheus time series
// - = 50,000+ entries in Micrometer registry (IN MEMORY)
// - = ~1.8 GB of heap just for metrics metadata
```

**Checking Cardinality:**

```bash
curl http://localhost:8080/actuator/prometheus | grep "ad_impressions_processed" | wc -l

# Output: 51,847 lines
# Each line = one metric in memory
# Result: 1.8GB of heap used by Prometheus metadata
```

This heap was **directly competing with the application** for GC pressure.

---

## How We Found It

### Signal 1: Grafana Heap Sawtooth
```
Heap usage over 4 hours:
3.5GB ╱╲    ╱╲    ╱╲    ╱╲     ← Getting taller each time
3.2GB ╱  ╲╱  ╲╱  ╲╱  ╲╱
3.0GB
2.8GB
```

The amplitude increases = Full GC not cleaning enough = Old Gen leak.

### Signal 2: Async Profiler Allocation Flamegraph

```bash
java -jar async-profiler.jar -e alloc -d 30 -f alloc.html <pid>
```

**Top Allocators:**
- ImpressionEvent: **34%** (deserialization)
- byte[] (Jackson): **28%** (temporary buffers)
- Long autoboxing: **18%** (HashMap.compute())
- Other: 20%

### Signal 3: GC Logs

```
[0.500s][info][gc,heap] GC(1): Full GC pause: 800ms, Reclaimable: 85%
[1.200s][info][gc,heap] GC(2): Full GC pause: 1100ms, Reclaimable: 70%
[1.950s][info][gc,heap] GC(3): Full GC pause: 1400ms, Reclaimable: 55%
[2.800s][info][gc,heap] GC(4): Full GC pause: 1800ms, Reclaimable: 30%
```

Pause time increasing + reclaimable memory decreasing = Old Gen fragmentation.

---

## The Fix

### Fix 1: Eliminate Autoboxing with Primitive Maps

**Before:**
```java
// HashMap<String, Long> — allocates a Long wrapper object per update
private final HashMap<String, Long> counters = new HashMap<>();

@KafkaListener(topics = "ad.impressions", concurrency = "4")
public void consume(ImpressionEvent event) {
    counters.compute(event.getCampaignId(), (k, v) -> {
        // v + 1 boxes long → Long object
        // Every compute call allocates a new Long object
        return v == null ? 1L : v + 1;
    });
}
```

**Result:** 83,000 Long objects allocated per second.

**After:**
```java
// Eclipse Collections primitive map — stores raw long, NO boxing
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;

private final ObjectLongHashMap<String> campaignCounters = new ObjectLongHashMap<>();

@KafkaListener(topics = "ad.impressions", concurrency = "4")
public void consume(ImpressionEvent event) {
    // Zero object allocation — updates raw long value in place
    campaignCounters.addToValue(event.getCampaignId(), 1L);
}
```

**Impact:** Eliminated 83K Long allocations/sec.

---

### Fix 2: Reuse Deserialization Object Instead of Allocating New One Per Message

**Before:**
```java
@KafkaListener(topics = "ad.impressions", concurrency = "4")
public void consume(ConsumerRecord<String, byte[]> record) {
    // New ImpressionEvent allocated for EVERY message
    // At 83K/sec, this fills Eden in 80ms
    ImpressionEvent event = MAPPER.readValue(
        record.value(), 
        ImpressionEvent.class
    );
    
    processEvent(event);
}
```

**After:**
```java
// One ImpressionEvent object per consumer thread (4 threads total)
// Reused for every message
private static final ThreadLocal<ImpressionEvent> REUSABLE_EVENT =
        ThreadLocal.withInitial(ImpressionEvent::new);

private static final ObjectReader EVENT_READER = 
        MAPPER.readerForUpdating(null);

@KafkaListener(topics = "ad.impressions", concurrency = "4")
public void consume(ConsumerRecord<String, byte[]> record) {
    // Get thread-local instance (already created)
    ImpressionEvent event = REUSABLE_EVENT.get();
    
    // Jackson updates fields in place instead of creating new object
    EVENT_READER.withValueToUpdate(event).readValue(record.value());
    
    processEvent(event);
}
```

**How It Works:**
- Thread 1 gets ImpressionEvent instance A
- Thread 1 deserializes message 1 → fields overwritten in place
- Thread 1 deserializes message 2 → same instance A, fields overwritten again
- Zero new object allocation

**Impact:** Eliminated 83K ImpressionEvent allocations/sec.

---

### Fix 3: Add Semaphore for Backpressure on ScyllaDB Writes

**Before:**
```java
public void writeToScylla(ImpressionEvent event) {
    // Unbounded async write
    // If ScyllaDB is slow, futures accumulate
    scyllaSession.executeAsync(buildStatement(event))
            .whenComplete((rs, ex) -> {
                // callback
            });
    // Returns immediately, no wait
}

// During ScyllaDB compaction:
// Thousands of futures pile up in heap
// Each holding reference to event payload
```

**After:**
```java
// Max 500 in-flight async writes
// If ScyllaDB is slow, consumer thread blocks (deliberate backpressure)
private final Semaphore scyllaPermits = new Semaphore(500);

public void writeToScylla(ImpressionEvent event) {
    try {
        // Acquire permit — blocks if 500 already in flight
        scyllaPermits.acquire();
        
        scyllaSession.executeAsync(buildStatement(event))
                .whenComplete((rs, ex) -> {
                    // Release permit when write completes
                    scyllaPermits.release();
                });
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
    }
}
```

**What Happens:**
```
Normal load: 100 futures in flight → consumer runs freely
ScyllaDB compaction: future count grows → at 500, consumer blocks
ScyllaDB resumes: futures complete → permits released → consumer unblocks

Result: No unbounded queue growth in heap
```

**Impact:** Bounded in-flight writes, prevents Old Gen bloat during throttling.

---

### Fix 4: Fix Prometheus Label Cardinality

**Before:**
```java
// High cardinality labels = memory explosion
Counter.builder("ad.impressions.processed")
        .tag("campaign_id", event.getCampaignId())  // 50,000+ unique values
        .register(registry)
        .increment();

// Result: 50,000+ time series in Micrometer registry
//         ~1.8GB of heap
```

**After:**
```java
// Only bounded categorical labels
Counter.builder("ad.impressions.processed")
        .tag("advertiser_tier", event.getAdvertiserTier())  // "premium", "standard", "house"
        .tag("ad_format", event.getAdFormat())              // "display", "video", "native"
        .register(registry)
        .increment();

// Result: 3 × 3 = 9 time series in memory
//         ~5 MB of heap

// Per-campaign granular counts go to ScyllaDB for reporting, not Prometheus
// (Prometheus is for monitoring, not detailed analytics)
```

**Impact:** Freed ~1.8GB of heap from Prometheus metadata.

---

### Fix 5: Right GC Algorithm for Each Service Type

**For Vert.x AdServer** (latency-sensitive):
```bash
# ZGC: sub-millisecond pause times (target: <1ms)
java -XX:+UseZGC \
     -Xms4g -Xmx4g \
     -XX:SoftMaxHeapSize=3500m \
     -Xlog:gc*:file=/var/log/gc.log:time,uptime,level
```

**For Spring Boot Analytics** (throughput-focused):
```bash
# G1GC: balanced latency and throughput
java -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=100 \
     -XX:G1HeapRegionSize=16m \
     -XX:InitiatingHeapOccupancyPercent=40 \
     -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=5,filesize=20m
```

---

### Fix 6: Kubernetes Memory Limits (Critical!)

**Common Mistake:**
```yaml
resources:
  limits:
    memory: "4Gi"  # Set equal to -Xmx
```

**Why This Fails:**
```
JVM uses memory outside the heap:
├─ Heap (-Xmx)              → 4096 MB
├─ Metaspace               → 256-512 MB
├─ Thread stacks           → (thread count × 512 KB) = ~512 MB
├─ Direct/Off-heap buffers → 512 MB (Netty/Vert.x)
├─ JIT code cache          → 256 MB
└─ Kubernetes overhead     → ~100 MB

Total: 4096 + 512 + 512 + 512 + 256 + 100 = ~5,988 MB

If limit = 4Gi (4096 MB):
  Off-heap usage = 5988 - 4096 = 1892 MB exceeds limit
  → Kubernetes OOMKills pod even though heap is fine
```

**Correct Config:**
```yaml
resources:
  requests:
    memory: "5Gi"   # Actual Pod needs this
  limits:
    memory: "6Gi"   # Safety margin above actual usage
    
jvm:
  -Xmx: "4g"       # Heap size
  # Total: 4 (heap) + 2 (off-heap/metaspace) + 0.5 (safety) = 6.5 but set to 6
```

**Formula:**
```
Memory Limit = Xmx + (Metaspace: 512MB) 
             + (Off-heap: 512MB)
             + (Thread stacks: thread_count × 0.5MB)
             + (JIT: 256MB)
             + (Safety margin: 10%)
             
For Xmx=4g with 20 threads:
= 4096 + 512 + 512 + (20 × 0.5) + 256 + 10%
= 5428 MB → set limit to 6Gi
```

---

## Results

| Metric | Before Fix | After Fix |
|--------|-----------|-----------|
| GC pause time (max) | 1.8s | 120ms |
| Full GC frequency | Every 3-4 min | Every 45-60 min |
| Heap utilization (steady state) | Growing → OOM | Stable at 60% |
| Object allocations/sec | 400K+ | 50K (primitive ops) |
| ScyllaDB futures in queue | Unbounded (5000+) | Bounded (≤500) |
| Prometheus memory | 1.8GB | 5MB |
| OOMKill frequency | Every 4-6 hours | Never |

---

## Interview Answer

> *"Our Analytics Ingestion Service was getting OOMKilled every 4-6 hours. Grafana showed a classic sawtooth heap pattern with increasing amplitude — Full GC pauses growing from 800ms to 1.8 seconds. We identified three root causes using Async Profiler and Prometheus cardinality checks.*
>
> *First, we were allocating a new `ImpressionEvent` object per Kafka message at 83K/sec. Jackson deserialization, plus boxed Long objects from HashMap.compute(). Eden filled in 80ms. We fixed it with ThreadLocal object reuse — one instance per consumer thread, with Jackson's `readerForUpdating` to mutate fields in place instead of allocating new objects.*
>
> *Second, unbounded ScyllaDB `executeAsync` calls with no backpressure. When ScyllaDB throttled (compaction, node coordination), thousands of futures accumulated in heap, each holding a full event payload. We added a Semaphore(500) for deliberate backpressure — consumer thread blocks if 500 writes are in flight, preventing Old Gen bloat.*
>
> *Third, high-cardinality Prometheus labels. We were tagging by campaign_id with 50,000+ unique values — creating 50,000+ time series in Micrometer registry, 1.8GB of heap competing with the application. We replaced it with bounded categorical labels (advertiser_tier, ad_format — 9 combinations total) and moved per-campaign granular counts to ScyllaDB reporting instead.*
>
> *Also, we were setting Kubernetes memory limits equal to Xmx and getting OOMKilled by off-heap growth — fixed by adding 2.5GB headroom for Metaspace, thread stacks, and Netty buffers. GC pauses dropped to 120ms, heap stabilized, and OOMKills stopped completely."*
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