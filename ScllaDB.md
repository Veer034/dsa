# ScyllaDB — When to Use It & Why It's Better

---

## What is ScyllaDB?

ScyllaDB is a high-performance NoSQL database built as a **drop-in replacement for Apache Cassandra**. It uses the same CQL query language and data model, but is rewritten in C++ from the ground up to eliminate the bottlenecks Cassandra has due to its Java/JVM design.

**One-line summary:** Same interface as Cassandra, 10x the performance, at the same or lower hardware cost.

---

## When Should You Use ScyllaDB?

Use ScyllaDB when your workload has **any combination** of these needs:

| Need | Why ScyllaDB fits |
|------|-------------------|
| **Millions of reads/writes per second** | Shard-per-core architecture saturates hardware fully |
| **Low latency is critical (p99 < 10ms)** | No GC pauses, predictable latency under load |
| **You're already on Cassandra** | 100% CQL compatible — migration is straightforward |
| **Large datasets (TBs to PBs)** | Scales horizontally with consistent performance |
| **Time-series, IoT, event streaming** | Optimized for write-heavy, append-style workloads |
| **Multi-region replication** | Built-in NetworkTopologyStrategy same as Cassandra |
| **You want to reduce infra cost** | Same throughput as Cassandra on fewer nodes |

---

## Why ScyllaDB is Better Than the Alternatives

### ScyllaDB vs Cassandra

Cassandra and ScyllaDB share the same data model, replication logic, and CQL interface. The difference is purely in the engine.

```
Cassandra (Java/JVM)                  ScyllaDB (C++)
─────────────────────                 ─────────────────────
JVM Heap → GC pauses                  Direct memory, no GC
All cores share memory                Each core owns its shard
Java thread per request               Async I/O (no thread blocking)
GC stop-the-world at high load        Consistent latency under load
Needs tuning to avoid GC hell         Works well out of the box
~200k ops/sec on 3 nodes              ~2M ops/sec on 3 nodes (same hw)
```

**Why this matters in practice:**
- Cassandra latency spikes unpredictably when JVM GC kicks in — this causes p99/p999 latency blowouts under heavy load. ScyllaDB doesn't have this problem because there is no garbage collector.
- In production, ScyllaDB typically needs **3x fewer nodes** to handle the same traffic, which directly reduces cost.

---

### ScyllaDB vs DynamoDB

| Aspect | ScyllaDB | DynamoDB |
|--------|----------|----------|
| **Cost model** | Fixed infra cost | Pay-per-request (expensive at scale) |
| **Vendor lock-in** | Open source, self-host or cloud | AWS only |
| **Latency control** | Full control | AWS-managed, less predictable |
| **Query flexibility** | CQL, secondary indexes, MV | Limited scan/query patterns |
| **Migration path** | Cassandra-compatible | Requires full rewrite |

**Use ScyllaDB over DynamoDB when:** your traffic is high and predictable, you want control over your infra, or you need to avoid AWS lock-in.

---

### ScyllaDB vs Redis

Redis is an in-memory store — fast, but your dataset must fit in RAM and durability is secondary. ScyllaDB is a durable disk-based database with optional caching.

| Aspect | ScyllaDB | Redis |
|--------|----------|-------|
| **Data size** | Terabytes (disk) | Gigabytes (RAM) |
| **Durability** | Always durable | Optional (can lose data on restart) |
| **Latency** | Sub-millisecond possible | Sub-millisecond |
| **Use case** | Primary database | Cache / pub-sub / ephemeral state |

**Use ScyllaDB when:** data is too large to fit in RAM, durability is required, or it needs to be your primary store — not a cache layer.

---

### ScyllaDB vs PostgreSQL / MySQL

ScyllaDB is not a relational database. Don't use it where you need JOINs, ACID transactions, or complex relational queries.

| Aspect | ScyllaDB | PostgreSQL |
|--------|----------|------------|
| **Horizontal scale** | Scales to 100+ nodes | Hard to scale writes horizontally |
| **JOINs / transactions** | No | Yes |
| **Schema flexibility** | Query-driven design | Normalized relational design |
| **Best for** | High-volume, known access patterns | Complex queries, strong consistency |

**Use ScyllaDB when:** you have a defined, high-volume access pattern and need horizontal scale. Use PostgreSQL when you need relational queries and complex business logic.

---

## How ScyllaDB Works Internally

### The Shard-per-Core Architecture

> **Shard** — A shard is a slice of a node permanently bound to one CPU core. Each shard owns its own chunk of memory and its own data files on disk. No two shards share anything. When ScyllaDB starts, it creates exactly one shard per CPU core on the machine. A request is routed directly to the shard that owns the data for that partition key — that CPU core handles the entire request alone, start to finish.

This is the core reason ScyllaDB is faster. Every CPU core runs independently — no shared memory, no locks, no context switching between cores.

```
┌──────────────────────────────────────────────────────────┐
│                    ScyllaDB Node                         │
│                                                          │
│   Core 0         Core 1         Core 2         Core N   │
│  ┌────────┐     ┌────────┐     ┌────────┐     ┌──────┐  │
│  │Shard 0 │     │Shard 1 │     │Shard 2 │     │Shard │  │
│  │        │     │        │     │        │     │  N   │  │
│  │- owns  │     │- owns  │     │- owns  │     │      │  │
│  │  its   │     │  its   │     │  its   │     │      │  │
│  │  data  │     │  data  │     │  data  │     │      │  │
│  │- own   │     │- own   │     │- own   │     │      │  │
│  │  mem   │     │  mem   │     │  mem   │     │      │  │
│  └────────┘     └────────┘     └────────┘     └──────┘  │
│       ↓               ↓               ↓            ↓    │
│                                                          │
│        No shared state. No locks. No GC. Just work.      │
└──────────────────────────────────────────────────────────┘

Cassandra equivalent:
┌──────────────────────────────────────────────────────────┐
│  All cores → shared JVM heap → GC pauses everything      │
│  Threads block on I/O, waiting, locking                  │
└──────────────────────────────────────────────────────────┘
```

### Data Distribution (Consistent Hashing)

> **Token** — A token is a number produced by hashing a partition key. ScyllaDB uses the Murmur3 hash function to convert any partition key (e.g. `"user123"`) into a 64-bit integer called a token. The entire token space is divided into ranges, and each node in the cluster owns one or more of those ranges. Whichever node owns the range that contains your token is the node that stores your data.
After the partition key identifies the node, ScyllaDB does a second calculation to find the shard (CPU core):

> **Token Ring** — The set of all nodes and their token ranges arranged in a logical circle. When you add or remove nodes, only the adjacent token ranges need to move, not the entire dataset.

Data is spread across nodes using a token ring. Every partition key is hashed to a token, and each node owns a range of tokens.

```
          Token Ring

        Node A (0–249)
           ┌───┐
    ┌──────┤   ├──────┐
    │      └───┘      │
Node D              Node B
(750–999)          (250–499)
    │      ┌───┐      │
    └──────┤   ├──────┘
           └───┘
        Node C (500–749)

Write "user123":
  hash("user123") = 310  →  lands on Node B
  shard_id = (token >> (64 - 12)) % number_of_cpu_cores -> finds which shard to read
  RF=3 means also written to Node C and Node D
```

### Write Path — Commit Log, MemTable, SSTable

> **Commit Log** — An append-only file on disk. Every write is recorded here first, before anything else happens. Its only job is durability — if the node crashes before data reaches permanent storage, the commit log is replayed on restart to recover the lost writes. It is sequential disk I/O, which makes it extremely fast.

> **MemTable** — An in-memory write buffer, one per table per shard. After the commit log is written, the data is placed into the MemTable. It lives entirely in RAM and is sorted by partition key and clustering key. Reads can be served from the MemTable directly if the data is fresh enough. When a MemTable fills up (or a flush is triggered), it is written to disk as an SSTable.

> **SSTable (Sorted String Table)** — An immutable, sorted file on disk. Once written from a MemTable, an SSTable is never modified. Updates and deletes don't overwrite old SSTables — they write new SSTables with the newer version. Reads check all relevant SSTables and return the most recent value. Over time, many SSTables accumulate, which is why Compaction exists.

> **Compaction** — A background process that merges multiple SSTables into one. It throws away old versions of rows, removes tombstones (deleted rows), and produces a single clean SSTable. Fewer SSTables = faster reads.

```
Client Write
    │
    ▼
Coordinator (any node — receives the request and routes it)
    │
    ├──────────────────────────┐
    ▼                          ▼
Replica 1                  Replica 2  (RF=3 → 3 replicas total)
    │                          │
    ├─ 1. Commit Log (disk)     ├─ 1. Commit Log (disk)
    │    append-only, fast      │    durability guarantee
    │                           │
    └─ 2. MemTable (memory)     └─ 2. MemTable (memory)
         sorted buffer               sorted buffer
         │
         │ (when full)
         ▼
      3. SSTable (disk)
         immutable sorted file
         │
         │ (background)
         ▼
      4. Compaction
         merge SSTables, discard old versions
```

---

## Key Concepts You Must Know

### Partition Key vs Clustering Key

The **partition key** decides which node holds your data. The **clustering key** decides how rows are sorted inside that node.

```cql
CREATE TABLE sensor_readings (
    device_id   TEXT,       -- Partition Key: routes to correct node
    recorded_at TIMESTAMP,  -- Clustering Key: sorts readings newest-first
    temperature FLOAT,
    PRIMARY KEY (device_id, recorded_at)
) WITH CLUSTERING ORDER BY (recorded_at DESC);
```

- **Bad partition key:** low-cardinality field (e.g., `status = 'active'`) — all data piles onto one node (hot partition)
- **Good partition key:** high-cardinality field (e.g., `device_id`, `user_id`) — data spreads evenly

### Consistency Levels — Pick Based on Your Trade-off

> **Replication Factor (RF)** — How many copies of each piece of data are stored across different nodes. RF=3 means every row exists on 3 separate nodes. If one node dies, you still have 2 copies. Higher RF = more durability but more storage cost.

> **Consistency Level** — For any given read or write, how many of those replicas must respond before ScyllaDB tells your client "done". This is a per-query setting, not a cluster-wide one. You trade speed against how sure you are the data is correct.

```
RF = 3 nodes store each piece of data

ONE           → Read/write 1 replica. Fast, but stale reads possible.
QUORUM        → Read/write majority (2/3). Strong consistency, slightly slower.
LOCAL_QUORUM  → QUORUM but only within your local datacenter. Best for multi-DC.
ALL           → All 3 replicas must respond. Strongest, but any node down = failure.

Recommended default:
  Writes → LOCAL_QUORUM
  Reads  → LOCAL_QUORUM
  Rule: R + W > RF ensures you always read what you wrote (2 + 2 > 3 ✓)
```

### Compaction — Why It Matters

> **Tombstone** — A delete in ScyllaDB does not actually remove data immediately. Instead it writes a special marker called a tombstone into a new SSTable. The tombstone says "this row was deleted at time X". The actual data removal happens during compaction. Too many tombstones hurt read performance because every read has to skip over them.

> **TTL (Time To Live)** — An expiry timer you set on a row or column at insert/update time. When the TTL expires, ScyllaDB automatically writes a tombstone for that data. Prefer TTL over explicit deletes for time-series or session data — it's the same mechanism under the hood but avoids you having to manage the deletes yourself.

ScyllaDB never updates data in-place. Every write appends a new version as a new SSTable. Compaction is the background process that merges those SSTables, keeps only the latest version of each row, and removes tombstones.

```
Before compaction:             After compaction:
┌──────────┐                   ┌──────────────────┐
│SSTable 1 │  user1 v1         │  Merged SSTable  │
│SSTable 2 │  user1 v2    →    │  user1 v3 ✓      │
│SSTable 3 │  user1 v3         │  user2 v2 ✓      │
│SSTable 4 │  user2 v2         │  (v1, v2 gone)   │
└──────────┘                   └──────────────────┘

Read without compaction: must check 4 SSTables to find latest value
Read after compaction:   checks 1 SSTable, answer is immediate
```

Too many SSTables = slower reads. Keep compaction running, and choose the right strategy:

| Strategy | Use When |
|----------|----------|
| **STCS** (SizeTiered) | Write-heavy, data rarely updated |
| **LCS** (Leveled) | Read-heavy, lots of updates |
| **TWCS** (TimeWindow) | Time-series data with TTL |
| **ICS** (Incremental) | ScyllaDB default, best general-purpose |

---

## What ScyllaDB Is NOT Good For

Be honest in an interview — knowing the limits shows real experience.

- ❌ **Complex queries with JOINs** — use PostgreSQL
- ❌ **ACID multi-row transactions** — use PostgreSQL or CockroachDB
- ❌ **Ad-hoc analytics / aggregations** — use ClickHouse or Snowflake
- ❌ **Small datasets** — operational overhead isn't worth it under ~10GB
- ❌ **Frequent schema changes** — schema-first design discipline required

---

## Cassandra → ScyllaDB Migration (In Practice)

Since ScyllaDB is CQL-compatible, the migration path is well-defined:

```
Phase 1: Shadow Write
  App writes to Cassandra + ScyllaDB simultaneously
  ScyllaDB reads are off — just collecting data

Phase 2: Historical Backfill
  Use spark-cassandra-connector or sstableloader
  to copy historical data to ScyllaDB

Phase 3: Read Validation
  Compare query results between both clusters
  Fix any discrepancies

Phase 4: Cut Over
  Switch app reads to ScyllaDB
  Keep Cassandra writes running for rollback window

Phase 5: Decommission
  Stop Cassandra writes
  Decommission Cassandra cluster
```

**Real challenges you'll face:**
- Driver compatibility (use the ScyllaDB shard-aware driver — it's faster)
- Tuning compaction and cache sizes fresh (ScyllaDB defaults differ from Cassandra)
- Monitoring is different — ScyllaDB uses Prometheus, not JMX

---

## CQL — Tables, Schemas, Inserts, Queries & Schema Changes

> **CQL (Cassandra Query Language)** — The SQL-like language used to interact with ScyllaDB. It looks like SQL but behaves differently: there are no JOINs, no subqueries, and every query must be rooted in a partition key. Think of it as SQL that enforces good distributed data access patterns.

> **Keyspace** — The top-level container for tables in ScyllaDB, equivalent to a "database" or "schema" in PostgreSQL. You define replication settings at the keyspace level — those settings then apply to all tables inside it.

### Creating a Keyspace

A keyspace is the top-level namespace — like a database in PostgreSQL. You define replication here.

```cql
-- Single datacenter
CREATE KEYSPACE shop
  WITH replication = {
    'class': 'SimpleStrategy',
    'replication_factor': 3
  };

-- Multi datacenter (production standard)
CREATE KEYSPACE shop
  WITH replication = {
    'class': 'NetworkTopologyStrategy',
    'us-east': 3,
    'eu-west': 2
  };

USE shop;
```

---

### Creating Tables

> **Partition Key** — The column (or columns) whose value is hashed to determine which node stores the row. All rows with the same partition key are stored together on the same node. This is the most important design decision — choose it based on what your most common query filters on.

> **Clustering Key** — A secondary key within a partition that controls how rows are physically sorted on disk inside that partition. You use it to fetch rows in a range (e.g. "all events between 9am and 5pm") or in a specific order without any extra sorting at query time.

> **Primary Key** — The combination of Partition Key + Clustering Key. Together they must uniquely identify every row.

ScyllaDB tables are defined around how you will query them — not around normalization.

```cql
-- Simple user table (partition key only)
CREATE TABLE users (
    user_id   UUID,
    email     TEXT,
    name      TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (user_id)
);

-- Orders per user (partition + clustering key)
-- "Give me all orders for user X, newest first"
CREATE TABLE orders_by_user (
    user_id    UUID,
    order_id   UUID,
    placed_at  TIMESTAMP,
    total      DECIMAL,
    status     TEXT,
    PRIMARY KEY (user_id, placed_at, order_id)
) WITH CLUSTERING ORDER BY (placed_at DESC, order_id ASC);

-- Sensor readings (time-series with bucketing)
-- Bucket by day to keep partition sizes bounded
CREATE TABLE sensor_readings (
    device_id  TEXT,
    day        DATE,
    recorded_at TIMESTAMP,
    temperature FLOAT,
    humidity   FLOAT,
    PRIMARY KEY ((device_id, day), recorded_at)
) WITH CLUSTERING ORDER BY (recorded_at DESC)
  AND default_time_to_live = 2592000;  -- auto-expire after 30 days
```

**Key rules:**
- `PRIMARY KEY (partition_key)` — single column partition
- `PRIMARY KEY ((col1, col2), clustering_col)` — composite partition key
- Every query MUST include the full partition key in the WHERE clause

---

### Inserting Data

```cql
-- Insert a user (UUID auto-generated)
INSERT INTO users (user_id, email, name, created_at)
VALUES (uuid(), 'alice@example.com', 'Alice', toTimestamp(now()));

-- Insert with TTL (auto-delete after 1 hour)
-- TTL = Time To Live, a countdown in seconds after which the row is automatically deleted
INSERT INTO sessions (session_id, user_id, token)
VALUES (uuid(), 123e4567-e89b-12d3-a456-426614174000, 'abc123')
USING TTL 3600;

-- Insert a sensor reading
INSERT INTO sensor_readings (device_id, day, recorded_at, temperature, humidity)
VALUES ('sensor-42', '2024-01-15', toTimestamp(now()), 22.5, 65.2);

-- Upsert (INSERT replaces if same primary key exists — there is no separate UPDATE needed)
INSERT INTO users (user_id, email, name, created_at)
VALUES (550e8400-e29b-41d4-a716-446655440000, 'bob@example.com', 'Bob', toTimestamp(now()));

-- Conditional insert — only if row does not exist
-- Uses Paxos (a consensus protocol): all replicas must agree before writing.
-- This guarantees uniqueness but costs 4x more latency than a normal insert. Use sparingly.
INSERT INTO users (user_id, email, name, created_at)
VALUES (uuid(), 'carol@example.com', 'Carol', toTimestamp(now()))
IF NOT EXISTS;
```

---

### Fetching Data

```cql
-- Fetch by partition key (fastest, single-node read)
SELECT * FROM users WHERE user_id = 550e8400-e29b-41d4-a716-446655440000;

-- Fetch all orders for a user (partition key required)
SELECT order_id, placed_at, total, status
FROM orders_by_user
WHERE user_id = 550e8400-e29b-41d4-a716-446655440000;

-- Fetch with clustering key range (within same partition)
SELECT * FROM orders_by_user
WHERE user_id = 550e8400-e29b-41d4-a716-446655440000
  AND placed_at >= '2024-01-01'
  AND placed_at <= '2024-01-31';

-- Fetch sensor data for a specific device and day
SELECT recorded_at, temperature, humidity
FROM sensor_readings
WHERE device_id = 'sensor-42'
  AND day = '2024-01-15'
LIMIT 100;

-- Fetch with token for pagination (cursor-based)
SELECT * FROM users
WHERE token(user_id) > token(550e8400-e29b-41d4-a716-446655440000)
LIMIT 20;
```

**What you CANNOT do without ALLOW FILTERING (avoid in production):**

> **ALLOW FILTERING** — A CQL keyword that lets you filter on non-partition-key columns. ScyllaDB warns you to use it explicitly because it forces a full cluster scan — every node must be checked. At small scale it works. At production scale (millions of rows) it will time out or crush performance. It is a red flag in any schema design.

```cql
-- ❌ Filter on non-partition key without index
SELECT * FROM users WHERE email = 'alice@example.com';
-- This scans the entire cluster. Never do this at scale.

-- ✅ Instead: create a separate lookup table
CREATE TABLE users_by_email (
    email   TEXT,
    user_id UUID,
    PRIMARY KEY (email)
);
```

---

### Updating Data

```cql
-- Update specific columns (only touches specified fields)
UPDATE users
SET name = 'Alice Smith'
WHERE user_id = 550e8400-e29b-41d4-a716-446655440000;

-- Update with TTL
UPDATE sessions USING TTL 7200
SET token = 'newtoken456'
WHERE session_id = ...;

-- Conditional update (uses Paxos — only use when needed, it's slow)
UPDATE users
SET status = 'verified'
WHERE user_id = 550e8400-e29b-41d4-a716-446655440000
IF status = 'pending';

-- Increment a counter column
UPDATE page_view_counts
SET views = views + 1
WHERE page_id = 'homepage';
```

---

### Deleting Data

```cql
-- Delete a specific row
DELETE FROM users WHERE user_id = 550e8400-e29b-41d4-a716-446655440000;

-- Delete specific columns (sets them to null/tombstone)
DELETE email, name FROM users
WHERE user_id = 550e8400-e29b-41d4-a716-446655440000;

-- Delete a range of clustering rows within a partition
DELETE FROM sensor_readings
WHERE device_id = 'sensor-42'
  AND day = '2024-01-15'
  AND recorded_at < '2024-01-15 06:00:00';
```

> ⚠️ **Tombstones:** A delete in ScyllaDB does not remove data from disk immediately. It writes a tombstone — a small marker that says "this data was deleted at timestamp X". The actual disk space is freed during the next compaction cycle. If you delete millions of rows frequently (e.g. expiring sessions), tombstones pile up and slow down reads because every read has to scan past them to find live data. For time-series or expiring data, always use `TTL` on insert instead of explicit deletes — the end result is the same but ScyllaDB handles cleanup more efficiently.

---

### Schema Changes (ALTER TABLE)

ScyllaDB supports online schema changes — the cluster keeps serving traffic while the change propagates. However, there are strict rules.

**What you CAN do:**
```cql
-- Add a new column (safe, always allowed)
ALTER TABLE users ADD phone TEXT;
ALTER TABLE users ADD preferences MAP<TEXT, TEXT>;
ALTER TABLE orders_by_user ADD discount DECIMAL;

-- Change a column type (only to compatible types)
ALTER TABLE users ALTER phone TYPE BLOB;

-- Drop a column (marks as dropped, space reclaimed at compaction)
ALTER TABLE users DROP phone;

-- Add or change TTL default on a table
ALTER TABLE sensor_readings WITH default_time_to_live = 5184000; -- 60 days

-- Change compaction strategy
ALTER TABLE sensor_readings
  WITH compaction = {
    'class': 'TimeWindowCompactionStrategy',
    'compaction_window_unit': 'DAYS',
    'compaction_window_size': 1
  };
```

**What you CANNOT do:**
```cql
-- ❌ Rename a column
-- Not supported — create a new table and migrate data

-- ❌ Change the primary key (partition or clustering key)
-- Not supported — requires creating a new table

-- ❌ Change column order in the primary key
-- Not supported
```

**When you need to change the primary key** (the hard case):
```
1. Create new table with the desired schema
2. Dual-write: app writes to both old and new table
3. Backfill: copy existing data to new table
4. Validate: confirm data parity
5. Switch reads to new table
6. Drop old table
```

This is the standard pattern — there is no shortcut. Design your primary key carefully upfront.

---

### Common Collection Types

```cql
-- List: ordered, allows duplicates
CREATE TABLE posts (
    post_id UUID PRIMARY KEY,
    tags    LIST<TEXT>
);
INSERT INTO posts (post_id, tags) VALUES (uuid(), ['scala', 'backend', 'jvm']);
UPDATE posts SET tags = tags + ['distributed'] WHERE post_id = ...;

-- Set: unordered, unique values
CREATE TABLE user_roles (
    user_id UUID PRIMARY KEY,
    roles   SET<TEXT>
);
INSERT INTO user_roles (user_id, roles) VALUES (uuid(), {'admin', 'editor'});

-- Map: key-value pairs
CREATE TABLE config (
    service_id UUID PRIMARY KEY,
    settings   MAP<TEXT, TEXT>
);
INSERT INTO config (service_id, settings)
VALUES (uuid(), {'timeout': '30s', 'retries': '3', 'region': 'us-east'});
UPDATE config SET settings['timeout'] = '60s' WHERE service_id = ...;
```

---

## Failure Handling — What Happens When a Shard or Node Crashes

### Scenario 1: A Single Shard Crashes

A shard crash means one CPU core's process died on a node. The node itself is still up.

**What ScyllaDB does immediately:**

ScyllaDB's process model means if one shard crashes, the entire node process restarts — ScyllaDB does not run shards as isolated processes that can die independently. When the node restarts, each shard replays its **Commit Log** to recover any writes that were in the MemTable but not yet flushed to an SSTable.

```
Normal state (Node B, 4 shards):
┌─────────────────────────────────────┐
│            Node B                   │
│  Shard 0 ✓  Shard 1 ✓              │
│  Shard 2 ✓  Shard 3 ✓              │
└─────────────────────────────────────┘

Shard 2 crashes → entire node restarts:
┌─────────────────────────────────────┐
│  Node B (restarting...)             │
│                                     │
│  Each shard replays its Commit Log  │
│  to recover MemTable writes         │
│                                     │
│  Commit Log = the safety net        │
│  "I wrote it to disk before RAM,    │
│   so nothing is lost"               │
└─────────────────────────────────────┘

Node B comes back up:
┌─────────────────────────────────────┐
│            Node B                   │
│  Shard 0 ✓  Shard 1 ✓              │
│  Shard 2 ✓  Shard 3 ✓  (recovered) │
└─────────────────────────────────────┘
```

**Why no data is lost:** Every write hits the Commit Log on disk *before* it goes into the MemTable in RAM. So even if a shard crashes mid-flight with unflushed MemTable data, the Commit Log has a record of every write. On restart, ScyllaDB reads the Commit Log and rebuilds the MemTable state.

**What about requests in flight during the restart?** This is where Replication Factor saves you. While Node B is restarting (usually takes seconds), the client driver detects the node is temporarily unreachable and routes requests to the other replicas that hold the same data (Node A and Node C if RF=3). The client sees no downtime.

---

### Scenario 2: An Entire Node Crashes

This is the more serious case. The whole machine is gone — all shards, all MemTables, all in-flight requests.

```
Before crash (RF=3, 4 nodes):

  Key "user123" is stored on: Node B (primary), Node C, Node D

  Node A        Node B        Node C        Node D
  ┌──────┐      ┌──────┐      ┌──────┐      ┌──────┐
  │  ✓   │      │  ✓   │      │  ✓   │      │  ✓   │
  │      │      │ owns │      │ copy │      │ copy │
  │      │      │user123      │user123      │user123
  └──────┘      └──────┘      └──────┘      └──────┘

Node B crashes:

  Node A        Node B        Node C        Node D
  ┌──────┐      ┌──────┐      ┌──────┐      ┌──────┐
  │  ✓   │      │  ✗   │      │  ✓   │      │  ✓   │
  │      │      │ DEAD │      │ copy │      │ copy │
  │      │      │      │      │user123      │user123
  └──────┘      └──────┘      └──────┘      └──────┘

user123 is still readable from Node C or Node D.
Writes still go to Node C and Node D.
Cluster keeps running.
```

**How reads and writes continue during the outage:**

The client driver uses the token to know which nodes own the data. When Node B is unreachable, the driver sends the request to the next replica in the ring. If consistency level is `QUORUM` (2 out of 3 replicas), the cluster remains fully operational as long as 2 of the 3 replicas are alive.

```
Write with CL=QUORUM, RF=3, Node B dead:

Client → Coordinator
              │
              ├──→ Node B  ✗ (dead, skip)
              ├──→ Node C  ✓ (ack)
              └──→ Node D  ✓ (ack)

2 acks received = QUORUM satisfied = success returned to client
Node B is missed — handled by Hinted Handoff
```

---

### Hinted Handoff — Writes During Node Downtime

> **Hinted Handoff** — When a node is down, the coordinator that tried to write to it saves a small record called a "hint" locally. The hint contains the full write that the dead node missed. When the dead node comes back up and rejoins the cluster, the coordinator replays all its saved hints to that node, bringing it back in sync. Hints are stored for up to 3 hours by default. If the node is down longer than that, hints are discarded and a manual repair is needed.

```
Node B down for 20 minutes:

Coordinator saves hint:
┌────────────────────────────────────┐
│  Hint stored on Coordinator        │
│  "Node B missed these writes:      │
│   user123 → {name: Alice} @ 10:05  │
│   user456 → {name: Bob}   @ 10:07  │
│   user789 → {name: Carol} @ 10:09" │
└────────────────────────────────────┘

Node B comes back at 10:25:
┌────────────────────────────────────┐
│  Coordinator detects Node B is up  │
│  Replays all saved hints to Node B │
│  Node B is now fully caught up     │
└────────────────────────────────────┘
```

---

### Anti-Entropy Repair — When Hints Are Not Enough

If a node is down for longer than the hint window (3 hours), or if hints were lost, the node comes back with stale or missing data. ScyllaDB has a repair mechanism for this.

> **nodetool repair** — A manual (or scheduled) operation that compares data between replicas using Merkle trees. A Merkle tree is a hash tree where each node in the tree is a hash of its children — it lets two nodes quickly identify *which* ranges of data differ without having to compare every row. Only the mismatched ranges are synced.

```
Node B back after 6 hours (hints expired):

ScyllaDB runs repair between Node B and Node C:

Step 1: Build Merkle trees
  Node B: hash(token range 250–499) = 0xAB12
  Node C: hash(token range 250–499) = 0xFF99
  Hashes differ → data diverged in this range

Step 2: Find exact mismatches
  Compare sub-ranges recursively
  Find the specific rows that differ

Step 3: Sync only the diff
  Node C sends missing/newer rows to Node B
  Node B is now consistent

No need to copy all data — only what diverged
```

**Repair schedule:** Run `nodetool repair` on each node at least once per `gc_grace_seconds` period (default 10 days). If you don't repair within that window, tombstones may be forgotten and deleted data can "resurrect" on rejoining nodes.

---

### Node Replacement — When a Node Is Permanently Lost

If the machine is gone for good (hardware failure, terminated cloud instance):

```
Step 1: Start a new node, tell it to replace the dead one
  scylla --replace-address-first-boot=<DEAD_NODE_IP>

Step 2: New node joins the ring at the same token position
  Streams data from the surviving replicas (Node C, Node D)
  that already hold copies of the dead node's data

Step 3: New node is fully populated
  Hinted handoffs are replayed
  nodetool repair to catch any remaining gaps

Step 4: Dead node is removed from the ring
  nodetool removenode <dead-node-id>
```

```
Dead: Node B (token range 250–499)

New Node B' joins at same token range:
  ← streams data from Node C and Node D →

  Node A     Node B'    Node C     Node D
  ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐
  │  ✓   │   │stream│   │  ✓   │   │  ✓   │
  │      │   │ ←──  │←──│copy  │   │copy  │
  └──────┘   └──────┘   └──────┘   └──────┘
                 │
                 ▼
             Node B' ✓ (fully restored)
```

---

### Summary — What Protects You at Each Level

```
Threat                    Protection Mechanism
─────────────────────     ──────────────────────────────────────
MemTable lost (crash)  →  Commit Log replayed on restart
Node down briefly      →  Hinted Handoff (up to 3 hours)
Node down long         →  nodetool repair (Merkle tree sync)
Node permanently dead  →  Replace node, stream from replicas
All replicas for a key →  Cannot happen if RF ≥ 3 and CL < ALL
```

The core guarantee: **as long as you keep RF=3 and use QUORUM consistency, you can lose any single node at any time with zero data loss and zero downtime.**

---

## Quick Reference

```
When to use ScyllaDB:
  ✅ High write/read throughput (millions ops/sec)
  ✅ Low-latency SLA (p99 < 10ms)
  ✅ Already on Cassandra and want better perf
  ✅ Large time-series, IoT, event log data
  ✅ Want to reduce node count vs Cassandra

When NOT to use:
  ❌ Need JOINs or ACID transactions
  ❌ Small dataset
  ❌ Purely relational data model
  ❌ Ad-hoc analytics workloads
```

# Spring Boot CRUD with ScyllaDB
## Full Setup — Configuration, Driver, Entities, Repository, REST API

---

## How Spring Boot Connects to ScyllaDB

ScyllaDB speaks the **Cassandra native protocol** on port `9042`. Spring Boot connects via the
DataStax Java driver (or the ScyllaDB shard-aware fork of it). There is no HTTP REST call —
the driver opens a persistent TCP connection directly to the cluster nodes on port `9042`.

```
Your Spring Boot App
        │
        │  TCP port 9042 (Cassandra native protocol)
        │
        ▼
┌───────────────────────────────────────────────────────┐
│              ScyllaDB Cluster                         │
│                                                       │
│   Node A           Node B           Node C            │
│   10.0.0.1:9042    10.0.0.2:9042    10.0.0.3:9042    │
│                                                       │
│   (or a Load Balancer in front: 10.0.0.10:9042)       │
└───────────────────────────────────────────────────────┘
```

> **Contact Points** — The IP addresses you give the driver on startup. The driver connects
> to these nodes first to discover the rest of the cluster topology. You do not need to list
> every node — just 2 or 3 is enough. The driver learns the full cluster from any one node.

> **Load Balancer vs Direct IPs** — In production you can put a TCP load balancer (e.g. HAProxy,
> AWS NLB) in front. However, the ScyllaDB shard-aware driver works best with direct node IPs
> because it knows which shard on which node owns each partition key — it sends each request
> to the exact right node and core, skipping any coordinator hop entirely.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/scylla/
│   │   ├── ScyllaApplication.java
│   │   ├── config/
│   │   │   └── ScyllaConfig.java
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   └── Order.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   └── OrderRepository.java
│   │   ├── service/
│   │   │   └── UserService.java
│   │   └── controller/
│   │       └── UserController.java
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       └── application-prod.yml
```

---

## Step 1 — Maven Dependencies

```xml
<dependencies>

    <!-- Spring Boot Web (REST API) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Data Cassandra — works with ScyllaDB out of the box -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-cassandra</artifactId>
    </dependency>

    <!-- ScyllaDB shard-aware driver (recommended over vanilla DataStax driver) -->
    <!-- Routes requests to the exact shard — avoids coordinator hops -->
    <dependency>
        <groupId>com.scylladb</groupId>
        <artifactId>java-driver-core</artifactId>
        <version>4.14.1.0</version>
    </dependency>

    <!-- Lombok — removes boilerplate getters/setters -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

</dependencies>
```

---

## Step 2 — application.yml (All Environments)

```yaml
# ── application.yml (base config, shared across all environments) ──

spring:
  cassandra:

    # Contact Points — IPs/hostnames of 2–3 ScyllaDB nodes
    # Driver connects here first, then discovers the full cluster automatically
    contact-points:
      - 10.0.0.1:9042
      - 10.0.0.2:9042
      - 10.0.0.3:9042

    # REQUIRED — must match the datacenter name in your ScyllaDB cluster
    # Run: SELECT data_center FROM system.local;  on any node to find this
    local-datacenter: datacenter1

    # The keyspace this app will use — must already exist
    keyspace-name: shop

    # Authentication (leave blank if auth is disabled on the cluster)
    username: cassandra
    password: cassandra

    # Schema action on startup:
    # CREATE_IF_NOT_EXISTS → creates tables from @Table entities  (dev/staging)
    # NONE                 → do nothing                            (production)
    # RECREATE             → drops and recreates tables            (local only — destroys data)
    schema-action: CREATE_IF_NOT_EXISTS

    # How long to wait when opening a connection to a node
    connection:
      connect-timeout: 10s
      init-query-timeout: 10s

    # Per-request settings
    request:
      timeout: 5s
      consistency: LOCAL_QUORUM      # Strong consistency within local DC
      serial-consistency: LOCAL_SERIAL

logging:
  level:
    com.datastax.oss.driver: WARN
    com.scylladb: WARN
    org.springframework.data.cassandra: DEBUG
```

### Dev Config

```yaml
# application-dev.yml — local machine / Docker Compose

spring:
  cassandra:
    contact-points:
      - 127.0.0.1:9042          # ScyllaDB running locally
    local-datacenter: datacenter1
    keyspace-name: shop_dev
    username: ""                  # no auth on local dev
    password: ""
    schema-action: CREATE_IF_NOT_EXISTS
```

### Production Config

```yaml
# application-prod.yml — real cluster with direct node IPs

spring:
  cassandra:
    contact-points:
      - 10.0.1.10:9042            # Node 1
      - 10.0.1.11:9042            # Node 2
      - 10.0.1.12:9042            # Node 3
    local-datacenter: us-east-1   # must match your cluster DC name
    keyspace-name: shop
    username: ${SCYLLA_USER}       # injected from env vars or Vault
    password: ${SCYLLA_PASSWORD}
    schema-action: NONE            # never auto-modify schema in production
    request:
      consistency: LOCAL_QUORUM
      timeout: 3s
```

### Kubernetes Config

```yaml
# application-k8s.yml — pods talking to ScyllaDB via K8s service

spring:
  cassandra:
    # Use StatefulSet pod DNS names for shard-aware routing
    # Format: <pod-name>.<service-name>.<namespace>.svc.cluster.local
    contact-points:
      - scylladb-0.scylladb.default.svc.cluster.local:9042
      - scylladb-1.scylladb.default.svc.cluster.local:9042
      - scylladb-2.scylladb.default.svc.cluster.local:9042
    local-datacenter: datacenter1
    keyspace-name: shop
    username: ${SCYLLA_USER}
    password: ${SCYLLA_PASSWORD}
    schema-action: NONE
```

> **Why StatefulSet pod names and not the Service ClusterIP in Kubernetes?**
> A regular K8s Service is a virtual IP that load-balances randomly across pods. The
> shard-aware driver needs to connect to each node directly by its actual IP so it can
> build a topology map and route each request to the correct shard. StatefulSet pods get
> stable DNS names (`scylladb-0`, `scylladb-1`, ...) that resolve directly to the pod IP,
> which is exactly what the driver needs.

---

## Step 3 — Configuration Bean

```java
package com.example.scylla.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.DataCenterReplication;

import java.util.List;

@Configuration
public class ScyllaConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.keyspace-name}")
    private String keyspace;

    @Value("${spring.cassandra.local-datacenter}")
    private String localDatacenter;

    // Which keyspace to use
    @Override
    protected String getKeyspaceName() {
        return keyspace;
    }

    // Where to scan for @Table entity classes
    @Override
    public String[] getEntityBasePackages() {
        return new String[]{"com.example.scylla.entity"};
    }

    // Auto-create keyspace on startup if it doesn't exist
    // Comment this out in production — keyspace should be created by ops/migrations
    @Override
    protected List<CreateKeyspaceSpecification> getKeyspaceCreations() {
        return List.of(
            CreateKeyspaceSpecification
                .createKeyspace(keyspace)
                .ifNotExists()
                .withNetworkReplication(
                    DataCenterReplication.of(localDatacenter, 3) // RF=3 in local DC
                )
        );
    }
}
```

---

## Step 4 — Entity Classes

```java
package com.example.scylla.entity;

import lombok.*;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

import java.time.Instant;
import java.util.UUID;

// @Table       → maps this class to the "users" table in the keyspace
// @Column      → maps field to a CQL column name
// @PrimaryKeyColumn → defines partition key or clustering key

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User {

    // Partition key — hashed to determine which node stores this row
    @PrimaryKeyColumn(
        name = "user_id",
        type = PrimaryKeyType.PARTITIONED
    )
    private UUID userId;

    @Column("email")
    private String email;

    @Column("name")
    private String name;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;
}
```

```java
package com.example.scylla.entity;

import lombok.*;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("orders_by_user")
public class Order {

    // Partition key — all orders for the same user land on the same node
    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID userId;

    // Clustering key — rows within the partition sorted newest first
    @PrimaryKeyColumn(name = "placed_at", ordinal = 1,
        type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant placedAt;

    // Second clustering key ensures uniqueness per row
    @PrimaryKeyColumn(name = "order_id", ordinal = 2,
        type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private UUID orderId;

    @Column("total")
    private BigDecimal total;

    @Column("status")
    private String status;
}
```

---

## Step 5 — Repositories

```java
package com.example.scylla.repository;

import com.example.scylla.entity.User;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// CassandraRepository<Entity, PrimaryKeyType>
// Free methods: save(), findById(), findAll(), deleteById(), existsById(), count()
@Repository
public interface UserRepository extends CassandraRepository<User, UUID> {

    // Spring Data derives CQL from the method name
    // → SELECT * FROM users WHERE email = ? ALLOW FILTERING
    // Only efficient if email has a secondary index
    Optional<User> findByEmail(String email);

    // Custom CQL — use sparingly, only for low-traffic internal queries
    @Query("SELECT * FROM users WHERE status = ?0 ALLOW FILTERING")
    java.util.List<User> findByStatus(String status);
}
```

```java
package com.example.scylla.repository;

import com.example.scylla.entity.Order;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends CassandraRepository<Order, UUID> {

    // Efficient — reads a single partition (all orders for one user)
    List<Order> findByUserId(UUID userId);

    // Clustering key range query within a single partition
    @Query("SELECT * FROM orders_by_user WHERE user_id = ?0 AND placed_at >= ?1 AND placed_at <= ?2")
    List<Order> findOrdersInRange(UUID userId, Instant from, Instant to);
}
```

---

## Step 6 — Service Layer

```java
package com.example.scylla.service;

import com.example.scylla.entity.User;
import com.example.scylla.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // ── CREATE ──────────────────────────────────────────────────────────────

    public User createUser(String email, String name) {
        User user = User.builder()
            .userId(UUID.randomUUID())   // partition key — generated here, not by DB
            .email(email)
            .name(name)
            .status("active")
            .createdAt(Instant.now())
            .build();

        User saved = userRepository.save(user);
        log.info("Created user: {}", saved.getUserId());
        return saved;
    }

    // ── READ ────────────────────────────────────────────────────────────────

    public Optional<User> getUser(UUID userId) {
        // Goes directly to the partition owning this userId — single node read
        return userRepository.findById(userId);
    }

    public List<User> getAllUsers() {
        // Full table scan across every node — only use for admin/tooling
        return userRepository.findAll();
    }

    // ── UPDATE ──────────────────────────────────────────────────────────────

    // In ScyllaDB, save() on an existing primary key = upsert.
    // Only the columns you include are written — other columns are untouched.
    public User updateUserName(UUID userId, String newName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setName(newName);
        return userRepository.save(user);
    }

    public User updateUserStatus(UUID userId, String status) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setStatus(status);
        return userRepository.save(user);
    }

    // ── DELETE ──────────────────────────────────────────────────────────────

    // Writes a tombstone marker — physical removal happens at next compaction
    public void deleteUser(UUID userId) {
        userRepository.deleteById(userId);
        log.info("Deleted user: {}", userId);
    }
}
```

---

## Step 7 — Request / Response DTOs

```java
package com.example.scylla.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Invalid email")
    @NotBlank(message = "Email is required")
    private String email;
}
```

```java
package com.example.scylla.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID userId;
    private String email;
    private String name;
    private String status;
    private Instant createdAt;

    public static UserResponse from(com.example.scylla.entity.User user) {
        UserResponse r = new UserResponse();
        r.setUserId(user.getUserId());
        r.setEmail(user.getEmail());
        r.setName(user.getName());
        r.setStatus(user.getStatus());
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}
```

---

## Step 8 — REST Controller

```java
package com.example.scylla.controller;

import com.example.scylla.dto.CreateUserRequest;
import com.example.scylla.dto.UserResponse;
import com.example.scylla.entity.User;
import com.example.scylla.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── POST /api/users ─────────────────────────────────────────────────────
    // Create a new user
    // Body: { "name": "Alice", "email": "alice@example.com" }
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        User user = userService.createUser(request.getEmail(), request.getName());
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(UserResponse.from(user));
    }

    // ── GET /api/users/{userId} ──────────────────────────────────────────────
    // Fetch a single user by partition key (fastest possible read)
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
        return userService.getUser(userId)
            .map(user -> ResponseEntity.ok(UserResponse.from(user)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ── GET /api/users ───────────────────────────────────────────────────────
    // List all users — full table scan, admin use only
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers()
            .stream()
            .map(UserResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    // ── PUT /api/users/{userId}/name ─────────────────────────────────────────
    // Update user name
    // Body: { "name": "Alice Smith" }
    @PutMapping("/{userId}/name")
    public ResponseEntity<UserResponse> updateName(
            @PathVariable UUID userId,
            @RequestBody java.util.Map<String, String> body) {

        String newName = body.get("name");
        if (newName == null || newName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            User updated = userService.updateUserName(userId, newName);
            return ResponseEntity.ok(UserResponse.from(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── PATCH /api/users/{userId}/status ─────────────────────────────────────
    // Update user status
    // Body: { "status": "inactive" }
    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponse> updateStatus(
            @PathVariable UUID userId,
            @RequestBody java.util.Map<String, String> body) {

        try {
            User updated = userService.updateUserStatus(userId, body.get("status"));
            return ResponseEntity.ok(UserResponse.from(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── DELETE /api/users/{userId} ───────────────────────────────────────────
    // Delete a user (writes a tombstone, physical delete happens at compaction)
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
```

---

## API Endpoints Summary

```
Method    Endpoint                          What it does
──────    ────────────────────────────────  ────────────────────────────────────
POST      /api/users                        Create a new user
GET       /api/users/{userId}               Fetch user by ID (partition key read)
GET       /api/users                        List all users (full scan — admin only)
PUT       /api/users/{userId}/name          Update user name
PATCH     /api/users/{userId}/status        Update user status
DELETE    /api/users/{userId}               Delete user (tombstone)
```

---

## Step 9 — Main Application

```java
package com.example.scylla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ScyllaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScyllaApplication.class, args);
    }
}
```

---

## Step 10 — Docker Compose (Local Dev)

Run a single ScyllaDB node locally to develop against:

```yaml
# docker-compose.yml
version: "3.9"

services:

  scylladb:
    image: scylladb/scylla:5.4
    container_name: scylladb
    ports:
      - "9042:9042"     # CQL native protocol — this is what Spring Boot connects to
      - "9160:9160"     # Thrift (legacy, usually unused)
      - "10000:10000"   # REST API (ScyllaDB manager)
    volumes:
      - scylla_data:/var/lib/scylla
    command: >
      --smp 2
      --memory 2G
      --overprovisioned 1
      --developer-mode 1    # relaxed settings for local dev

  # Run this once after scylladb is healthy to create the keyspace
  scylla-init:
    image: scylladb/scylla:5.4
    depends_on:
      - scylladb
    entrypoint: >
      bash -c "
        sleep 30 &&
        cqlsh scylladb -e \"
          CREATE KEYSPACE IF NOT EXISTS shop
          WITH replication = {
            'class': 'SimpleStrategy',
            'replication_factor': 1
          };
        \"
      "

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_CASSANDRA_CONTACT_POINTS: scylladb:9042
      SPRING_CASSANDRA_LOCAL_DATACENTER: datacenter1
      SPRING_CASSANDRA_KEYSPACE_NAME: shop
    depends_on:
      - scylladb

volumes:
  scylla_data:
```

---

## Connection Topology — All Scenarios

```
──────────────────────────────────────────────────────────────────
Scenario 1: Local Development
──────────────────────────────────────────────────────────────────

  Spring Boot App
       │ contact-points: 127.0.0.1:9042
       ▼
  ScyllaDB (Docker)
  127.0.0.1:9042


──────────────────────────────────────────────────────────────────
Scenario 2: Production — Direct to Nodes (Recommended)
──────────────────────────────────────────────────────────────────

  Spring Boot App
       │ contact-points: 10.0.1.10, 10.0.1.11, 10.0.1.12
       │ Driver discovers full cluster topology from these 3 nodes
       ▼
  ┌──────────────────────────────────────────────────┐
  │  ScyllaDB Cluster                               │
  │  Node A: 10.0.1.10:9042                         │
  │  Node B: 10.0.1.11:9042                         │
  │  Node C: 10.0.1.12:9042                         │
  └──────────────────────────────────────────────────┘

  Each request goes directly to the node (and shard) that owns the data.
  No coordinator hop. Best latency.


──────────────────────────────────────────────────────────────────
Scenario 3: Production — Via Load Balancer (Simple but slower)
──────────────────────────────────────────────────────────────────

  Spring Boot App
       │ contact-points: 10.0.0.10:9042  (LB VIP)
       ▼
  AWS NLB / HAProxy (10.0.0.10:9042)
       │ round-robin TCP
       ├──▶ Node A: 10.0.1.10:9042
       ├──▶ Node B: 10.0.1.11:9042
       └──▶ Node C: 10.0.1.12:9042

  Shard-aware routing is lost. Every request may hit the wrong node,
  forcing an internal coordinator hop. Works, but adds ~1 extra network hop.


──────────────────────────────────────────────────────────────────
Scenario 4: Kubernetes — StatefulSet with pod DNS
──────────────────────────────────────────────────────────────────

  Spring Boot Pod
       │ contact-points: scylladb-0.scylladb.default.svc:9042
       │                 scylladb-1.scylladb.default.svc:9042
       │                 scylladb-2.scylladb.default.svc:9042
       ▼
  K8s StatefulSet
  ┌────────────────────────────────────────────────┐
  │  scylladb-0 pod  →  10.244.0.5:9042           │
  │  scylladb-1 pod  →  10.244.1.3:9042           │
  │  scylladb-2 pod  →  10.244.2.7:9042           │
  └────────────────────────────────────────────────┘

  Each pod has a stable DNS name. Driver resolves them to pod IPs
  and builds its topology map. Shard-aware routing works correctly.
```

---

## Common Mistakes

**Mistake 1 — Using a Headless Service IP instead of Pod IPs in K8s**
A regular ClusterIP service load-balances randomly. The driver needs direct pod IPs.
Always use a headless service (`clusterIP: None`) with StatefulSet pod names.

**Mistake 2 — Wrong `local-datacenter`**
If this doesn't match the actual datacenter name in ScyllaDB, the driver throws:
`No node was available to execute the query` or `Datacenter not found`.
Fix: run `SELECT data_center FROM system.local;` on any node and copy the exact string.

**Mistake 3 — `schema-action: RECREATE` in production**
This drops and recreates all tables on every app restart. Data gone instantly.
Always use `NONE` in production.

**Mistake 4 — Calling `findAll()` on large tables**
This does a full cluster scan and returns millions of rows into memory.
Only use it for admin tooling on small tables.

**Mistake 5 — `ALLOW FILTERING` on high-traffic endpoints**
Forces every node to scan its full partition range. Fine for a 1000-row table,
crushes performance on 10M+ rows. Redesign the schema instead.


---

## Resources

- [ScyllaDB Docs](https://docs.scylladb.com)
- [ScyllaDB University (free)](https://university.scylladb.com)
- [Cassandra to ScyllaDB Migration Guide](https://docs.scylladb.com/stable/operating-scylla/procedures/migrate-to-scylla/)

---