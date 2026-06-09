# Design a Distributed Cache (Redis-like)
> Tests: consistent hashing, eviction policies, replication, cache invalidation — one of the hardest fundamentals questions.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| Read-heavy or write-heavy? | Replication strategy, write-through vs write-behind |
| What data types? | String only vs rich types (list, set, hash, sorted set) |
| Persistence needed? | Pure cache vs durable store |
| What's the expected hit rate? | Drives capacity estimation |
| Eviction policy? | LRU / LFU / TTL-based |
| Consistency model? | Strong vs eventual for replicas |
| Scale: how many nodes? | Sharding/cluster strategy |
| Cache-aside or write-through? | Application integration pattern |

**Typical answer:** Read-heavy (10:1), string KV + TTL, no persistence needed, LRU eviction, millions of ops/sec, horizontal scaling.

---

## Scale Estimation

```
Throughput: 10M ops/sec (read + write combined)
  → Each cache node: ~100K-500K ops/sec (Redis benchmark)
  → Need 20-100 nodes for 10M ops/sec

Memory: cache 10TB of data
  → Each node: 64-128 GB RAM
  → Need ~80-160 nodes for storage

Latency target: < 1ms for cache hit
```

---

## HLD Diagram

```
         ┌─────────────────────────────────────────────┐
         │              Application Servers             │
         │           (Cache Client SDK)                │
         └──────────────────┬──────────────────────────┘
                            │
         ┌──────────────────▼──────────────────────────┐
         │           Cache Client (Smart Client)        │
         │   - Consistent hashing ring                  │
         │   - Routes key to correct node              │
         │   - Handles node failure + rebalance        │
         └───┬───────────┬───────────┬─────────────────┘
             │           │           │
    ┌────────▼──┐ ┌──────▼───┐ ┌────▼──────┐
    │  Node A   │ │  Node B  │ │  Node C   │  Primary nodes
    │ (master)  │ │ (master) │ │ (master)  │
    └────────┬──┘ └──────┬───┘ └────┬──────┘
             │           │          │
    ┌────────▼──┐ ┌──────▼───┐ ┌────▼──────┐
    │  Node A'  │ │  Node B' │ │  Node C'  │  Replica nodes
    │ (replica) │ │ (replica)│ │ (replica) │
    └───────────┘ └──────────┘ └───────────┘

                     ┌────────────────┐
                     │  Config Server │  ← tracks ring membership
                     │  (ZooKeeper /  │     node health, failover
                     │   etcd)        │
                     └────────────────┘
```

---

## Key Design Decisions

### 1. Consistent Hashing — Core Algorithm

```
Problem: Naive hashing (key % N) means adding/removing a node reshuffles everything.
  → N=10: key goes to node 5
  → N=11: key goes to node 3 (node 5's data is now a cache miss)
  → Full cache invalidation → thundering herd on DB

Consistent Hashing:
  → Hash space: 0 to 2^32 (a ring)
  → Each cache node hashed to multiple positions (virtual nodes)
  → Key hashed to position → clockwise to nearest node

  Ring:  0────────────────────────────2^32
              A1    B1    C1    A2    B2    C2
         A1, A2, A3 = virtual nodes for server A

Adding node D:
  → D hashed to positions on ring
  → Only keys between D's predecessor and D move to D
  → ~K/N keys move (K=total keys, N=nodes) — minimal disruption

Virtual nodes (vnodes):
  → Each physical node gets 150-300 positions on ring
  → Distributes load evenly even with heterogeneous node sizes
  → Node failure redistributes its vnodes to neighbors
```

```java
// Simplified consistent hash ring
public class ConsistentHashRing {
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final int virtualNodes = 150;

    public void addNode(String nodeId) {
        for (int i = 0; i < virtualNodes; i++) {
            long hash = hash(nodeId + "#" + i);
            ring.put(hash, nodeId);
        }
    }

    public String getNode(String key) {
        long hash = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        if (entry == null) entry = ring.firstEntry();  // wrap around
        return entry.getValue();
    }

    private long hash(String key) {
        // MurmurHash or xxHash — fast, good distribution
        return Hashing.murmur3_128().hashString(key, UTF_8).asLong() & Long.MAX_VALUE;
    }
}
```

### 2. Eviction Policies

```
LRU (Least Recently Used):
  → Evict the item not accessed for the longest time
  → Implementation: HashMap + Doubly Linked List
  → HashMap: key → ListNode (O(1) lookup)
  → Linked list: move accessed node to head, evict from tail
  → O(1) get and put

LFU (Least Frequently Used):
  → Evict item with lowest access count (ties broken by recency)
  → Better for temporal locality (hot items stay)
  → More complex: need min-frequency tracking
  → Redis uses approximated LFU (sampled)

FIFO (First In First Out):
  → Simple queue, evict oldest inserted item
  → Ignores access pattern — poor cache performance

TTL-based:
  → Items expire after set duration regardless of access
  → Combined with LRU (expire + evict on memory pressure)

What Redis actually does:
  → Approximated LRU: sample 5 random keys, evict least recently used among them
  → Not perfect LRU but close enough and O(1) without the linked list overhead
  → maxmemory-policy options: allkeys-lru, volatile-lru, allkeys-lfu, etc.
```

```java
// LRU Cache — classic interview implementation (O(1) get/put)
public class LRUCache {
    private final int capacity;
    private final Map<String, DLinkedNode> cache = new HashMap<>();
    private final DLinkedNode head = new DLinkedNode();  // dummy
    private final DLinkedNode tail = new DLinkedNode();  // dummy

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public String get(String key) {
        DLinkedNode node = cache.get(key);
        if (node == null) return null;
        moveToHead(node);  // mark as recently used
        return node.value;
    }

    public void put(String key, String value, long ttlMs) {
        DLinkedNode node = cache.get(key);
        if (node != null) {
            node.value = value;
            node.expireAt = System.currentTimeMillis() + ttlMs;
            moveToHead(node);
        } else {
            DLinkedNode newNode = new DLinkedNode(key, value, ttlMs);
            cache.put(key, newNode);
            addToHead(newNode);
            if (cache.size() > capacity) {
                DLinkedNode evicted = removeTail();
                cache.remove(evicted.key);
            }
        }
    }
}
```

### 3. Cache Invalidation — The Hardest Problem

```
"There are only two hard problems in CS: cache invalidation and naming things."

Strategies:

A. TTL (Time-To-Live) — simplest
   → Every cached item has an expiry
   → Stale data guaranteed to clear within TTL window
   → Trade-off: stale for up to TTL duration

B. Event-driven invalidation:
   → DB write → publish event to Kafka → cache consumer DEL key
   → Near-real-time freshness
   → Risk: event lost → stale cache forever (use TTL as safety net)

C. Write-Through:
   → Every DB write also writes to cache
   → Cache always fresh
   → Every write hits cache (not ideal for write-heavy workloads)
   → Cache must always have the item (no cold start issue)

D. Write-Behind (Write-Back):
   → Write to cache first, async flush to DB
   → Fastest writes, lowest DB load
   → Risk: cache node crash = data loss
   → Use for non-critical data (view counts, session data)

E. Cache-Aside (Lazy Loading): ← most common
   → Read: check cache → miss → read DB → populate cache
   → Write: write DB first → DEL cache key (not update)
   → Next read will re-populate from DB
   → Why DEL and not update? Update introduces race conditions

Race condition with Cache-Aside:
  T1: Read miss → reads DB (old value: 10)
  T2: Write DB: value = 20 → DEL cache
  T1: Writes stale value 10 to cache  ← stale!
  
  Fix: Use version/ETag → only SET cache if version matches
  Fix: Short TTL → stale window is bounded
```

### 4. Replication — Leader-Follower

```
Each primary node has 1-2 replicas:
  → Reads can go to replicas (scale reads horizontally)
  → Writes go to primary → async replicated to replicas
  → Replication lag: typically < 1ms on same rack, < 10ms cross-AZ

Failover:
  → Config server (etcd/ZooKeeper) monitors node health (heartbeat every 1s)
  → Primary failure → promote replica to primary within 5-30 seconds
  → Client smart-routing updates from config server

Consistency during failover:
  → Async replication: possible to lose writes not yet replicated
  → For strong consistency: synchronous replication to at least 1 replica before ACK
    (higher latency: +network RTT per write)
  → Redis Cluster uses async replication by default — accepts possibility of data loss

QUORUM reads/writes (Dynamo-style):
  → N replicas, W writes must succeed, R reads must succeed
  → W + R > N ensures overlap (at least 1 node has latest data)
  → N=3, W=2, R=2 — common setup
```

### 5. Hot Key Problem

```
Problem: 1 key (e.g., celebrity profile, trending item) gets 500K reads/sec
         → single node overwhelmed

Solutions:

A. Local cache (JVM Caffeine) in each app server:
   → Top 1000 hot keys cached in heap
   → No network call for hottest items
   → Invalidation via Kafka broadcast

B. Key replication (fan-out):
   → Store hot key on multiple nodes: user:123:shard0, user:123:shard1, ..., user:123:shard9
   → Reads round-robin across shards
   → Client randomly picks shard

C. Read replicas per hot node:
   → Detect hot node (monitoring: ops/sec per node)
   → Dynamically add read replicas for that node
   → Route reads to replicas

Detection:
   → Monitor ops/sec per node in Prometheus/Druid
   → Alert if any node > 80% capacity → trigger hot-key mitigation
```

---

## Cache Patterns Quick Reference

```
Pattern          Description                           Use Case
──────────────────────────────────────────────────────────────────────
Cache-Aside      App checks cache, hits DB on miss     Most common; DB is source of truth
Write-Through    Write cache + DB together             Strong consistency needed
Write-Behind     Write cache, async flush DB           High write throughput, tolerate loss
Read-Through     Cache fetches from DB on miss         Cache is a proxy
Refresh-Ahead    Proactively refresh before expiry     Predictable access patterns, low latency
```

---

## Interview Tips

- **Consistent hashing is the answer** to "how do you add/remove nodes without cache stampede."
- **Virtual nodes** prevent uneven distribution — mention them.
- **Cache invalidation is the hard problem** — walk through race conditions in cache-aside and how TTL + DEL-not-update helps.
- **Hot key problem** — bring this up proactively. Interviewers love it.
- **LRU with HashMap + Doubly Linked List** — if they ask to code it, this is the expected O(1) solution.
- Mention **Redis Cluster vs Redis Sentinel** — Cluster for horizontal scaling, Sentinel for HA without sharding.
- Drop **quorum reads/writes** if they ask about consistency — shows distributed systems depth.
