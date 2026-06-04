# HLD: Distributed Key-Value Store

> **Experience Level:** 10+ Years Java | Spring Boot · Kafka · Redis · MySQL · Elasticsearch · ScyllaDB · Druid

---

## 🔁 Clarifying Questions (You → Interviewer)

| # | Question | Why It Matters |
|---|----------|----------------|
| 1 | Should this be strongly consistent or eventually consistent? | CAP theorem decision |
| 2 | What are the latency SLAs for `get` and `put`? | Replication synchrony |
| 3 | Should it support TTL, range queries, or only point lookups? | Storage engine choice |
| 4 | What's the target data size — GB or TB per node? | Storage and compaction strategy |
| 5 | Do we need transactions or atomic multi-key operations? | MVCC / 2PC complexity |
| 6 | How many nodes? Should it auto-scale with consistent hashing? | Partitioning scheme |
| 7 | Do we need snapshotting/WAL for durability across crashes? | Persistence strategy |
| 8 | Should it be an in-memory store (like Redis) or disk-backed (like RocksDB)? | Memory vs durability tradeoff |

---

## 🔁 Expected Follow-up Questions (Interviewer → You)

- "Explain consistent hashing and virtual nodes."
- "How does your system handle node failure during a write?"
- "What is quorum read/write and why does it matter?"
- "How do you resolve write conflicts — Last Write Wins vs vector clocks?"
- "Walk me through a `get()` call end to end."
- "How does your gossip protocol work?"
- "How would you implement compaction to reclaim storage?"
- "What's the difference between your design and DynamoDB / Cassandra?"

---

## Scale Estimation

```
Nodes:         10–100 nodes
Data per node: ~500 GB (disk-backed)
Replication:   N=3 (3 replicas per key)
Quorum:        R=2, W=2 (strong consistency for most operations)
Latency target: p99 < 10ms for get, < 20ms for put
```

---

## System Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                         Clients                                 │
└──────────────────────┬─────────────────────────────────────────┘
                       │ GET /key or PUT /key
                       ▼
            ┌──────────────────────┐
            │   Coordinator Node   │◄── Any node can be coordinator
            │  (routes to replicas)│
            └──────┬───────────────┘
                   │  Consistent Hashing
        ┌──────────┼─────────────┐
        ▼          ▼             ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Node A  │  │  Node B  │  │  Node C  │
│ (primary │  │(replica 1│  │(replica 2│
│  token   │  │  range)  │  │  range)  │
│  range)  │  └──────────┘  └──────────┘
└──────────┘
     │
     ├── In-memory: ConcurrentHashMap (hot data)
     ├── Disk:      LSM-tree / SSTable (cold data)
     └── WAL:       Write-ahead log (durability)

Gossip Protocol: Nodes exchange state every 1 sec
Failure Detector: Phi Accrual / heartbeat
```

---

## Consistent Hashing with Virtual Nodes

```java
public class ConsistentHashRing {
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final int virtualNodes;
    private final MessageDigest md;

    public ConsistentHashRing(int virtualNodes) throws NoSuchAlgorithmException {
        this.virtualNodes = virtualNodes;
        this.md = MessageDigest.getInstance("MD5");
    }

    public synchronized void addNode(String nodeId) {
        for (int i = 0; i < virtualNodes; i++) {
            long hash = hash(nodeId + "-vnode-" + i);
            ring.put(hash, nodeId);
        }
    }

    public synchronized void removeNode(String nodeId) {
        for (int i = 0; i < virtualNodes; i++) {
            long hash = hash(nodeId + "-vnode-" + i);
            ring.remove(hash);
        }
    }

    /**
     * Returns N unique nodes responsible for a given key.
     * Walks clockwise around the ring.
     */
    public List<String> getPreferenceList(String key, int replicationFactor) {
        List<String> nodes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long hash = hash(key);

        NavigableMap<Long, String> tailMap = ring.tailMap(hash, true);
        Iterator<String> iter = Iterables.concat(tailMap.values(), ring.values()).iterator();

        while (iter.hasNext() && nodes.size() < replicationFactor) {
            String node = iter.next();
            if (seen.add(node)) nodes.add(node);
        }
        return nodes;
    }

    private long hash(String input) {
        byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
        long h = 0;
        for (int i = 0; i < 8; i++) h = (h << 8) | (bytes[i] & 0xFF);
        return h;
    }
}
```

---

## Storage Engine (Per Node — LSM-Tree Style)

```java
public class StorageEngine {
    // Hot data: in-memory ConcurrentSkipListMap (sorted for range queries)
    private final ConcurrentSkipListMap<String, ValueEntry> memTable = new ConcurrentSkipListMap<>();
    private final long maxMemTableSize = 64 * 1024 * 1024; // 64 MB
    private final AtomicLong currentSize = new AtomicLong(0);

    // Write-Ahead Log
    private final WriteAheadLog wal;

    // Immutable SSTables flushed from memTable
    private final List<SSTable> ssTables = new CopyOnWriteArrayList<>();

    public StorageEngine(String dataDir) throws IOException {
        this.wal = new WriteAheadLog(dataDir + "/wal.log");
        recoverFromWal();
    }

    public void put(String key, byte[] value, long ttlMillis) throws IOException {
        ValueEntry entry = new ValueEntry(value, System.currentTimeMillis(),
                                          System.currentTimeMillis() + ttlMillis);
        // 1. Append to WAL first (durability)
        wal.append(Operation.PUT, key, entry);

        // 2. Write to memTable
        memTable.put(key, entry);
        currentSize.addAndGet(key.length() + value.length);

        // 3. Flush to SSTable if memTable is full
        if (currentSize.get() >= maxMemTableSize) flushToDisk();
    }

    public byte[] get(String key) {
        // 1. Check memTable (newest data)
        ValueEntry entry = memTable.get(key);

        // 2. Check SSTables in reverse order (newest first)
        if (entry == null) {
            for (int i = ssTables.size() - 1; i >= 0; i--) {
                entry = ssTables.get(i).get(key);
                if (entry != null) break;
            }
        }

        if (entry == null || entry.isTombstone()) return null;
        if (entry.isExpired()) { delete(key); return null; }
        return entry.getValue();
    }

    public void delete(String key) throws IOException {
        // Write tombstone
        ValueEntry tombstone = ValueEntry.tombstone(System.currentTimeMillis());
        wal.append(Operation.DELETE, key, tombstone);
        memTable.put(key, tombstone);
    }

    private synchronized void flushToDisk() {
        SSTable ssTable = new SSTable(memTable);
        ssTable.writeToDisk();
        ssTables.add(ssTable);
        memTable.clear();
        currentSize.set(0);
        wal.truncate();
    }
}
```

---

## Quorum-Based Coordinator

```java
@Service
public class KVStoreCoordinator {

    private final ConsistentHashRing ring;
    private final Map<String, NodeClient> nodeClients; // nodeId → HTTP/gRPC client
    private final int N = 3; // replication factor
    private final int W = 2; // write quorum
    private final int R = 2; // read quorum

    public PutResponse put(String key, byte[] value, long ttlMillis) {
        List<String> replicas = ring.getPreferenceList(key, N);
        AtomicInteger successCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String nodeId : replicas) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    nodeClients.get(nodeId).put(key, value, ttlMillis);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Write failed on node {}: {}", nodeId, e.getMessage());
                }
            }));
        }

        // Wait for W acknowledgments
        waitForQuorum(futures, successCount, W, "Write");
        return PutResponse.success();
    }

    public GetResponse get(String key) {
        List<String> replicas = ring.getPreferenceList(key, N);
        List<CompletableFuture<VersionedValue>> futures = new ArrayList<>();

        for (String nodeId : replicas) {
            futures.add(CompletableFuture.supplyAsync(() ->
                nodeClients.get(nodeId).get(key)));
        }

        // Collect R responses, pick highest version (Last Write Wins)
        List<VersionedValue> responses = collectQuorumResponses(futures, R);
        VersionedValue latest = responses.stream()
                .max(Comparator.comparingLong(VersionedValue::getTimestamp))
                .orElse(null);

        // Read repair: async update stale replicas
        asyncReadRepair(key, latest, replicas);
        return latest != null ? GetResponse.of(latest.getValue()) : GetResponse.notFound();
    }

    private void asyncReadRepair(String key, VersionedValue latest, List<String> replicas) {
        if (latest == null) return;
        CompletableFuture.runAsync(() -> {
            for (String nodeId : replicas) {
                try {
                    VersionedValue v = nodeClients.get(nodeId).get(key);
                    if (v == null || v.getTimestamp() < latest.getTimestamp()) {
                        nodeClients.get(nodeId).put(key, latest.getValue(), latest.getTtl());
                    }
                } catch (Exception ignored) {}
            }
        });
    }
}
```

---

## Gossip Protocol (Membership & Failure Detection)

```java
@Service
public class GossipService {

    private final Map<String, NodeState> membershipTable = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::gossipRound, 0, 1, TimeUnit.SECONDS);
    }

    private void gossipRound() {
        // Pick a random live node to exchange state with
        List<String> liveNodes = getLiveNodes();
        if (liveNodes.isEmpty()) return;
        String peer = liveNodes.get(ThreadLocalRandom.current().nextInt(liveNodes.size()));
        exchangeState(peer);
        detectFailures();
    }

    private void detectFailures() {
        long now = System.currentTimeMillis();
        membershipTable.forEach((nodeId, state) -> {
            if (now - state.getLastHeartbeat() > 5000) { // 5s timeout
                state.setStatus(NodeStatus.SUSPECTED);
            }
            if (now - state.getLastHeartbeat() > 15000) { // 15s dead
                state.setStatus(NodeStatus.DOWN);
                notifyRingOfFailure(nodeId);
            }
        });
    }

    private void notifyRingOfFailure(String nodeId) {
        ring.removeNode(nodeId);
        // Trigger hinted handoff for keys that were on the failed node
        hintedHandoffService.startHandoff(nodeId);
    }
}
```

---

## Hinted Handoff (Handling Node Failures)

```java
@Service
public class HintedHandoffService {
    // When a target node is down, writes are temporarily stored on a healthy node
    // with a hint that data belongs to the failed node.
    // When the failed node recovers, hints are replayed.

    @Autowired private HintRepository hintRepository; // persisted in local DB

    public void storeHint(String targetNodeId, String key, byte[] value) {
        hintRepository.save(new Hint(targetNodeId, key, value, Instant.now()));
    }

    public void replayHints(String recoveredNodeId) {
        List<Hint> hints = hintRepository.findByTargetNode(recoveredNodeId);
        hints.forEach(hint -> {
            try {
                nodeClients.get(recoveredNodeId).put(hint.getKey(), hint.getValue(), -1);
                hintRepository.delete(hint);
            } catch (Exception e) {
                log.warn("Hint replay failed for node {}", recoveredNodeId);
            }
        });
    }
}
```

---

## Anti-Entropy (Merkle Tree Sync)

```java
public class MerkleTreeSync {
    /**
     * Compare Merkle trees between two replicas.
     * Identify key ranges that differ and sync only those ranges.
     * Avoids full data transfer for consistency repair.
     */
    public List<String> findDivergentKeys(String nodeA, String nodeB) {
        MerkleTree treeA = nodeClients.get(nodeA).getMerkleTree();
        MerkleTree treeB = nodeClients.get(nodeB).getMerkleTree();
        return treeA.diff(treeB); // returns key ranges that need repair
    }
}
```

---

## REST API

```
PUT    /kv/{key}
       Body: { "value": "<base64>", "ttlSeconds": 3600 }
       Response: 200 OK

GET    /kv/{key}
       Response: 200 { "value": "<base64>", "version": 1718000000000 }
                 404 Not Found

DELETE /kv/{key}
       Response: 204 No Content

GET    /kv?prefix={prefix}&limit=100    (range scan)
```

---

## CAP Tradeoffs & Tuning

```
Strong consistency (CP):   W + R > N  →  W=2, R=2, N=3
Eventual consistency (AP): W=1, R=1, N=3  →  fastest, may read stale
Read-heavy optimization:   W=3, R=1   →  all replicas written, any can serve reads
Write-heavy optimization:  W=1, R=3   →  fast writes, reads gather all
```

---

## Comparison: This Design vs Production Systems

| Feature | This Design | DynamoDB | Cassandra |
|---------|------------|----------|-----------|
| Partitioning | Consistent Hashing | Consistent Hashing | Consistent Hashing |
| Replication | Quorum (N/W/R) | Multi-AZ | Configurable |
| Conflict Resolution | LWW (timestamp) | LWW | LWW / Configurable |
| Storage Engine | LSM-Tree | Proprietary | LSM (SSTables) |
| Failure Detection | Gossip | AWS infra | Gossip |
| Anti-entropy | Merkle Tree | Proprietary | Merkle Tree |

---

## Extension Points

- **Range queries**: Use `ConcurrentSkipListMap` in memTable for prefix scans
- **Transactions**: Paxos-based multi-key compare-and-swap
- **Compression**: Snappy/LZ4 on SSTable blocks
- **Metrics**: Expose node health via Spring Actuator + Prometheus
- **Druid**: Stream put/get stats via Kafka for OLAP query analysis