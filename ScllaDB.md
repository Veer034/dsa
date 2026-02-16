# ScyllaDB Interview Guide
## For Senior Engineers (10+ Years Experience, Cassandra → ScyllaDB Migration)

---

## 1. Architecture & Migration Questions

### Q1: What are the key architectural differences between Cassandra and ScyllaDB?

**Answer:**
- **Language**: ScyllaDB is written in C++ (vs. Cassandra's Java), eliminating JVM overhead and garbage collection pauses
- **Thread-per-Core Architecture**: ScyllaDB uses a shard-per-core model with thread pinning, avoiding context switching
- **I/O Scheduler**: ScyllaDB has an advanced I/O scheduler that prioritizes requests based on latency SLAs
- **Memory Management**: Direct memory management without GC, using seastar framework
- **Compaction**: More efficient compaction strategies with better resource utilization
- **Performance**: 10x better throughput with lower latencies (p99 < 1ms possible)

```
┌─────────────────────────────────────────────────────────┐
│                    ScyllaDB Node                        │
├─────────────────────────────────────────────────────────┤
│  CPU Core 0  │  CPU Core 1  │  CPU Core 2  │  Core N   │
│  ┌────────┐  │  ┌────────┐  │  ┌────────┐  │ ┌──────┐ │
│  │ Shard 0│  │  │ Shard 1│  │  │ Shard 2│  │ │Shard │ │
│  │        │  │  │        │  │  │        │  │ │  N   │ │
│  │MemTable│  │  │MemTable│  │  │MemTable│  │ │MemTbl│ │
│  │        │  │  │        │  │  │        │  │ │      │ │
│  │SSTables│  │  │SSTables│  │  │SSTables│  │ │SSTab │ │
│  └────────┘  │  └────────┘  │  └────────┘  │ └──────┘ │
└─────────────────────────────────────────────────────────┘
         ↓              ↓              ↓            ↓
    ┌────────────────────────────────────────────────────┐
    │         Shared-Nothing Architecture                │
    │    Each core owns its data & memory exclusively    │
    └────────────────────────────────────────────────────┘
```

---

### Q2: Explain ScyllaDB's data distribution and partitioning mechanism

**Answer:**
- Uses **consistent hashing** with virtual nodes (vnodes) or tablets
- Partition key → token (Murmur3 hash) → determines data placement
- Token range divided among nodes in the cluster
- **Replication Factor (RF)**: Copies of data stored on multiple nodes
- **Replication Strategy**: SimpleStrategy (single DC) or NetworkTopologyStrategy (multi-DC)

```
┌──────────────────────────────────────────────────────────┐
│              Consistent Hashing Ring                     │
│                                                           │
│         Token: 0                    Token: 2^63          │
│            ○────────────────────────────○                │
│          ╱                                ╲              │
│         ╱        ○ Node A (RF=3)           ╲             │
│        ╱       ╱   Token: 100               ╲            │
│       ○       ╱                               ○           │
│       │      ╱         Write Key "user123"    │           │
│       │     ○          Hash → Token: 150      │           │
│       │     │          Primary: Node B        │           │
│   Node D    │          Replicas: Node C, D    │   Node B  │
│   Token:    │                                 │   Token:  │
│   700       │                                 │   200     │
│       │     │                                 │           │
│       ○     │                                 ○           │
│        ╲    │                               ╱             │
│         ╲   ○ Node C                      ╱              │
│          ╲    Token: 500                ╱                │
│           ╲                           ╱                  │
│            ○─────────────────────────○                   │
│                                                           │
└──────────────────────────────────────────────────────────┘

Data Flow:
1. Client writes key "user123"
2. Driver calculates token = hash("user123") = 150
3. Coordinator identifies Node B as primary (token 200)
4. RF=3 → replicas at Node C (token 500) and Node D (token 700)
5. Write sent to all 3 nodes (consistency level dependent)
```

---

### Q3: What challenges did you face during Cassandra to ScyllaDB migration?

**Answer:**
Common challenges:
- **Driver Compatibility**: Ensuring application drivers work with ScyllaDB
- **Query Patterns**: Some anti-patterns in Cassandra perform differently
- **Consistency Levels**: Tuning for optimal performance
- **Compaction Strategy**: Adjusting from Cassandra's compaction
- **Monitoring**: Different metrics and tools (ScyllaDB Monitoring Stack)
- **Data Migration**: Zero-downtime migration using dual-write or Spark

**Migration Strategy:**
1. Set up ScyllaDB cluster in parallel
2. Enable dual writes (app writes to both)
3. Historical data migration (snapshot + stream)
4. Validation phase
5. Switch reads to ScyllaDB
6. Decommission Cassandra

---

## 2. Data Modeling & Schema Design

### Q4: How do you design an efficient data model in ScyllaDB?

**Answer:**
Key principles:
- **Query-Driven Design**: Model based on access patterns, not normalization
- **Partition Key**: Distribute data evenly (avoid hot partitions)
- **Clustering Key**: Order data within partition
- **Wide Partitions vs. Skinny Partitions**: Balance partition size (< 100MB ideal)
- **Denormalization**: Duplicate data to avoid JOINs
- **Time-Series Data**: Use time-bucket pattern for bounded partitions

**Example Schema:**
```cql
-- User activity tracking (time-series)
CREATE TABLE user_activity (
    user_id UUID,
    activity_date DATE,          -- Bucketing by date
    activity_time TIMESTAMP,
    activity_type TEXT,
    metadata MAP<TEXT, TEXT>,
    PRIMARY KEY ((user_id, activity_date), activity_time)
) WITH CLUSTERING ORDER BY (activity_time DESC);

-- Partition Key: (user_id, activity_date) - distributes data
-- Clustering Key: activity_time - orders events within partition
-- Queries: "Get user X's activity for date Y" - efficient single partition read
```

---

### Q5: Explain the difference between partition key and clustering key

**Answer:**

| Aspect | Partition Key | Clustering Key |
|--------|--------------|----------------|
| **Purpose** | Determines which node stores data | Orders data within partition |
| **Distribution** | Distributes data across cluster | Organizes rows in partition |
| **Query** | Must be in WHERE clause | Optional in WHERE, defines sort |
| **Cardinality** | Should have high cardinality | Can have low or high cardinality |

```
Table: sensor_data ((device_id, location), timestamp, temperature)
                     └─Partition Key─┘  └─Clustering Key─┘

┌─────────────────────────────────────────────────────────┐
│  Node A                 Node B                Node C     │
│  ┌──────────────┐      ┌──────────────┐               │
│  │Partition:    │      │Partition:    │               │
│  │(dev1, NYC)   │      │(dev2, LA)    │               │
│  ├──────────────┤      ├──────────────┤               │
│  │ts: 10:00 →25°│      │ts: 10:05 →30°│               │
│  │ts: 10:01 →26°│      │ts: 10:06 →31°│               │
│  │ts: 10:02 →24°│      │ts: 10:07 →29°│               │
│  └──────────────┘      └──────────────┘               │
│    Sorted by            Sorted by                      │
│    timestamp            timestamp                      │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Consistency & Performance

### Q6: Explain consistency levels and when to use each

**Answer:**

| Level | Replicas | Use Case | Performance | Consistency |
|-------|----------|----------|-------------|-------------|
| **ANY** | 1 (hinted handoff OK) | Max availability | Fastest | Weakest |
| **ONE** | 1 replica | High throughput reads | Fast | Weak |
| **QUORUM** | N/2 + 1 | Balanced consistency | Medium | Strong |
| **LOCAL_QUORUM** | Local DC quorum | Multi-DC, low latency | Medium | Strong (DC) |
| **ALL** | All replicas | Critical reads | Slowest | Strongest |

**Common Pattern (RF=3):**
- Writes: `LOCAL_QUORUM` (2/3 replicas in local DC)
- Reads: `LOCAL_QUORUM` (ensures consistency)
- Formula: `R + W > RF` for strong consistency (2 + 2 > 3 ✓)

```
Write with QUORUM (RF=3):
┌──────────────────────────────────────────────────────┐
│  1. Client → Coordinator                             │
│  2. Coordinator → Replica A, B, C                    │
│  3. Wait for acknowledgment from 2/3 replicas        │
│                                                       │
│     Replica A ✓ (ack)                                │
│     Replica B ✓ (ack) → QUORUM satisfied → Success  │
│     Replica C ⏳ (slow, timeout, or failed)          │
│                                                       │
│  4. Return success to client                         │
│  5. Replica C eventually gets data (read repair/     │
│     hinted handoff)                                  │
└──────────────────────────────────────────────────────┘
```

---

### Q7: What is read repair and how does it work in ScyllaDB?

**Answer:**
**Read Repair** ensures data consistency by fixing inconsistencies during read operations.

**Types:**
1. **Blocking Read Repair** (< 10% chance): Waits for repair before returning
2. **Background Read Repair**: Asynchronous repair after response

**Process:**
```
1. Client reads with CL=QUORUM (RF=3)
2. Coordinator queries 2 replicas (A, B)
3. Receives responses:
   - Replica A: timestamp=100, value="old"
   - Replica B: timestamp=200, value="new"
4. Detects mismatch → triggers read repair
5. Returns newest value to client: "new"
6. Asynchronously updates Replica A with "new"
```

**Additional Mechanisms:**
- **Hinted Handoff**: Temporary storage when replica is down
- **Anti-Entropy Repair** (`nodetool repair`): Full cluster repair
- **Digest Queries**: Lightweight consistency check using hashes

---

### Q8: How do you handle hot partitions in ScyllaDB?

**Answer:**
**Hot Partition**: Single partition receiving disproportionate traffic, causing performance bottleneck.

**Detection:**
- Monitor metrics: high latency on specific partitions
- `SELECT * FROM system.large_partitions`
- ScyllaDB Monitoring: partition size alerts

**Solutions:**

1. **Application-Level Sharding**:
```cql
-- Before (hot partition):
PRIMARY KEY (user_id)

-- After (sharded):
PRIMARY KEY ((user_id, shard_id))
-- shard_id = hash(user_id) % N
```

2. **Time-Bucketing** (for time-series):
```cql
PRIMARY KEY ((sensor_id, bucket_time), timestamp)
-- bucket_time = YYYYMMDD or YYYYMMDDHH
```

3. **Denormalization**: Split data into multiple tables

4. **Read/Write Caching**: Application-level cache for hot data

5. **Tablets (ScyllaDB 5.2+)**: Automatic splitting of large partitions

---

## 4. Operations & Monitoring

### Q9: Explain ScyllaDB's compaction strategies

**Answer:**

| Strategy | Use Case | Characteristics |
|----------|----------|-----------------|
| **SizeTieredCompactionStrategy (STCS)** | Write-heavy, immutable data | Merges similar-sized SSTables |
| **LeveledCompactionStrategy (LCS)** | Read-heavy, updates | Fixed-size levels, less space amplification |
| **TimeWindowCompactionStrategy (TWCS)** | Time-series, TTL data | Compacts by time windows |
| **IncrementalCompactionStrategy (ICS)** | ScyllaDB-specific | Adaptive, best for most workloads |

**ICS (Recommended for ScyllaDB):**
- Hybrid approach combining STCS and LCS benefits
- Dynamically adjusts based on workload
- Lower write amplification than LCS
- Better space efficiency than STCS

```
Compaction Process:
┌────────────────────────────────────────────────────┐
│  MemTable (memory)                                 │
│  ┌──────────┐                                      │
│  │ Write    │ → Flush when full                    │
│  │ Buffer   │                                      │
│  └──────────┘                                      │
│       ↓                                            │
│  SSTables (disk - immutable)                       │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐                 │
│  │ SS1 │ │ SS2 │ │ SS3 │ │ SS4 │  ← Multiple     │
│  │ 1MB │ │ 2MB │ │ 1MB │ │ 2MB │    small tables  │
│  └─────┘ └─────┘ └─────┘ └─────┘                 │
│       ↓                                            │
│  Compaction (merge + remove tombstones)            │
│       ↓                                            │
│  ┌──────────────┐ ┌──────────────┐               │
│  │   Merged     │ │   Merged     │  ← Fewer      │
│  │   SSTable    │ │   SSTable    │    larger     │
│  │     5MB      │ │     6MB      │    tables     │
│  └──────────────┘ └──────────────┘               │
└────────────────────────────────────────────────────┘
```

---

### Q10: What metrics do you monitor for ScyllaDB in production?

**Answer:**

**Key Metrics:**

1. **Latency Metrics**:
    - `scylla_storage_proxy_coordinator_read_latency` (p95, p99, p999)
    - `scylla_storage_proxy_coordinator_write_latency`
    - Target: p99 < 10ms

2. **Throughput**:
    - `scylla_transport_requests_served` (ops/sec)
    - Read vs. Write ratio

3. **Resource Utilization**:
    - CPU usage per shard
    - Memory usage (memtable, cache)
    - Disk I/O (queue depth, utilization)

4. **Errors**:
    - Timeouts: `scylla_storage_proxy_coordinator_read_timeouts`
    - Unavailable exceptions
    - Read/write failures

5. **Compaction**:
    - Pending compactions
    - Compaction throughput

6. **Node Health**:
    - Node status (up/down)
    - Streaming operations
    - Repair progress

**Monitoring Stack**: Prometheus + Grafana with ScyllaDB dashboards

---

## 5. Advanced Topics

### Q11: How does ScyllaDB handle node failures and recovery?

**Answer:**

**Failure Detection**:
- Gossip protocol (heartbeat every 1 second)
- Node marked down after failure detection timeout
- `phi_convict_threshold` configures sensitivity

**Write Path During Failure (RF=3, CL=QUORUM)**:
```
Normal:      A ✓  B ✓  C ✓  → Success (2/3 acks)
Node C down: A ✓  B ✓  C ✗  → Success + Hinted Handoff
Node B,C down: A ✓  B ✗ C ✗ → Failure (< QUORUM)
```

**Hinted Handoff**:
- Coordinator stores hint for down node
- When node recovers, hints are replayed
- Max hint window: 3 hours (configurable)

**Node Recovery**:
1. Node rejoins cluster via gossip
2. Receives hinted handoffs
3. `nodetool repair` to sync missed data
4. Streaming from replicas for gaps

**Replace Node**:
```bash
# Replace dead node (preserves token)
scylla --replace-address-first-boot=<DEAD_NODE_IP>
```

---

### Q12: Explain ScyllaDB's write path and commit log

**Answer:**

**Write Path Flow**:
```
┌────────────────────────────────────────────────────────┐
│ 1. Client Write Request                                │
│    ↓                                                    │
│ 2. Coordinator Node (token-aware routing)              │
│    ↓                                                    │
│ 3. Determine replicas based on partition key           │
│    ↓                                                    │
│ 4. Each Replica Node (parallel):                       │
│                                                         │
│    ┌──────────────────────────────────────────┐       │
│    │ a) Write to Commit Log (append-only)     │       │
│    │    └→ Durability guarantee               │       │
│    │                                           │       │
│    │ b) Write to MemTable (in-memory)         │       │
│    │    └→ Sorted structure (skip list)       │       │
│    │                                           │       │
│    │ c) Send ACK to Coordinator               │       │
│    └──────────────────────────────────────────┘       │
│    ↓                                                    │
│ 5. Coordinator waits for consistency level ACKs        │
│    (e.g., 2/3 for QUORUM)                              │
│    ↓                                                    │
│ 6. Return success to client                            │
│                                                         │
│ Background:                                             │
│    - MemTable full → Flush to SSTable (disk)           │
│    - Commit log segments deleted after flush           │
│    - SSTables compacted periodically                   │
└────────────────────────────────────────────────────────┘
```

**Commit Log Characteristics**:
- Sequential writes (very fast)
- Per-shard commit log
- Batched fsync (configurable: periodic or immediate)
- Compressed and segmented

---

### Q13: What are Secondary Indexes and Materialized Views? When to use each?

**Answer:**

**Secondary Indexes**:
- Index on non-primary key columns
- Allows querying by indexed column
- **Downside**: Creates hidden table, poor performance on high-cardinality columns

```cql
CREATE INDEX ON users (email);
SELECT * FROM users WHERE email = 'user@example.com';
```

**Use When**:
- Low cardinality columns (status, category)
- Rare queries (not main access pattern)
- Small datasets

**Materialized Views**:
- Automatic denormalization with different primary key
- Query optimized for different access pattern
- **Downside**: Write amplification (2x writes), eventual consistency

```cql
CREATE MATERIALIZED VIEW users_by_email AS
    SELECT * FROM users
    WHERE email IS NOT NULL
    PRIMARY KEY (email, user_id);
```

**Use When**:
- Multiple query patterns needed
- Read-heavy workload
- Acceptable eventual consistency

**Best Practice**: Application-level denormalization often better than both

---

### Q14: How do you perform zero-downtime upgrades?

**Answer:**

**Rolling Upgrade Process**:
```
1. Review release notes for breaking changes
2. Backup configuration and data (snapshots)
3. Upgrade one node at a time:
   
   For each node:
   a) nodetool drain (stop accepting writes)
   b) Stop ScyllaDB service
   c) Upgrade packages
   d) Start ScyllaDB service
   e) Wait for node to be Up and Normal (nodetool status)
   f) Run nodetool upgradesstables (if required)
   g) Proceed to next node

4. Verify cluster health after all nodes upgraded
5. Update drivers if necessary
```

**Monitoring During Upgrade**:
- Watch latencies, timeouts
- Monitor pending compactions
- Check for errors in logs
- Verify replication catch-up

**Rollback Plan**:
- Keep old packages available
- Snapshot before upgrade
- Document rollback procedure

---

### Q15: Explain Lightweight Transactions (LWT) and their performance impact

**Answer:**

**LWT (Linearizable Consistency)**:
- Implements Paxos consensus algorithm
- Ensures atomic compare-and-set operations
- Used for `IF NOT EXISTS`, `IF <condition>`

```cql
-- Example: Prevent duplicate user registration
INSERT INTO users (user_id, email, name)
VALUES (uuid(), 'user@example.com', 'John')
IF NOT EXISTS;

-- Conditional update
UPDATE users SET status = 'active'
WHERE user_id = ?
IF status = 'pending';
```

**How It Works**:
```
Phase 1 - Prepare (2 round trips):
1. Coordinator proposes to replicas
2. Replicas promise not to accept older proposals
3. Reply with current value

Phase 2 - Commit:
1. Coordinator proposes final value
2. Replicas accept and persist
3. Acknowledge to coordinator
```

**Performance Impact**:
- **Latency**: 4x slower than regular writes (4 round trips vs 1)
- **Throughput**: Significantly reduced
- **Serialization**: Contention on same partition

**Best Practices**:
- Avoid in hot paths
- Use sparingly (only when truly needed)
- Consider application-level locking instead
- Don't mix LWT and non-LWT on same partition

---

## 6. Troubleshooting Scenarios

### Q16: How do you diagnose and fix slow queries?

**Answer:**

**Diagnostic Steps**:

1. **Enable Query Tracing**:
```cql
TRACING ON;
SELECT * FROM large_table WHERE partition_key = ?;
-- Review trace output for bottlenecks
```

2. **Check Query Pattern**:
- Full table scan? (missing WHERE clause)
- Large partition read?
- Secondary index scan?
- ALLOW FILTERING used?

3. **Analyze with `nodetool`**:
```bash
nodetool tablehistograms keyspace.table
# Check partition sizes, row counts

nodetool cfstats keyspace.table
# SSTables count, read/write latency
```

4. **Review Metrics**:
- High p99 latency on specific table
- Disk I/O saturation
- CPU bottleneck on specific shard

**Common Issues & Fixes**:

| Issue | Solution |
|-------|----------|
| Large partition scan | Add clustering key to WHERE clause |
| Too many SSTables | Trigger manual compaction |
| Hot partition | Implement sharding |
| Wide partition | Add time-bucketing |
| ALLOW FILTERING | Redesign schema, add appropriate index |
| GC pressure (Cassandra leftover) | N/A in ScyllaDB, check memory settings |

---

## 7. Best Practices Summary

### Key Takeaways:

1. **Data Modeling**: Query-first design, avoid anti-patterns
2. **Consistency**: Use LOCAL_QUORUM for balanced consistency + performance
3. **Monitoring**: Proactive monitoring of latency, errors, resources
4. **Operations**: Regular repairs, careful upgrades, capacity planning
5. **Performance**: Leverage ScyllaDB's shard-per-core architecture
6. **Migration**: Thorough testing, validation, gradual rollout

---

## Additional Resources

- **ScyllaDB University**: Free courses and certifications
- **ScyllaDB Docs**: https://docs.scylladb.com
- **Monitoring Stack**: https://monitoring.docs.scylladb.com
- **ScyllaDB Summit**: Annual conference with advanced talks

---

**Interview Prep Tips**:
- Be ready to discuss your Cassandra → ScyllaDB migration experience
- Prepare real-world scenarios and how you solved them
- Understand performance tuning and operational excellence
- Know when ScyllaDB is the right choice vs. other databases