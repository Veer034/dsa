# Apache Flink — Interview Prep with Java Code & Concept Explanations

> Every concept explained from basics first, then how to configure it in Java code.

---

## Table of Contents

1. [What is Apache Flink?](#1-what-is-apache-flink)
2. [Core Concepts Explained Simply](#2-core-concepts-explained-simply)
3. [Flink in Production — High Load with Java Config](#3-flink-in-production--high-load-with-java-config)
4. [Checkpointing — What It Is, Common Issues & Java Fixes](#4-checkpointing--what-it-is-common-issues--java-fixes)
5. [Flink vs Spark Streaming vs Kafka Streams](#5-flink-vs-spark-streaming-vs-kafka-streams)
6. [Why Flink over Spark?](#6-why-flink-over-spark)
7. [General Data Engineering Questions](#7-general-data-engineering-questions)

---

## 1. What is Apache Flink?

Flink is a **stream processing framework**. It reads data from a source (like Kafka), processes it continuously as each event arrives, and writes results to a sink (like Elasticsearch or another Kafka topic).

Think of it like a water pipe:
- **Kafka** = the water source (events keep flowing in)
- **Flink** = the pipe with filters/transformers in between
- **Elasticsearch / DB** = the bucket at the end

The key difference from a regular application: Flink processes **millions of events per second** with built-in fault tolerance — if a server crashes, it recovers automatically without losing data.

---

## 2. Core Concepts Explained Simply

### 2.1 What is State?

When Flink processes events, sometimes it needs to **remember something from previous events** to make a decision about the current one.

Example: "Count how many calls agent John handled in the last 5 minutes."

Flink can't answer this from a single event. It needs to **remember the count** across many events. That memory is called **state**.

State is stored either:
- **In JVM heap (memory)** — fast but limited by RAM. If the process crashes, state is lost unless checkpointed.
- **In RocksDB** — stored on disk. Survives restarts. Can hold more data than RAM.

---

### 2.2 What is RocksDB?

RocksDB is a **key-value store that writes data to disk** (not RAM). It was created by Facebook.

Think of it like a very fast dictionary stored on your hard drive:
- Key: `"agent_john_callcount"`
- Value: `42`

**Why Flink uses it**: When you have 150 enterprise tenants each with 100 agents, you have 15,000 keys to track. That can easily exceed JVM heap memory. RocksDB stores that state on disk using a structure called an **LSM Tree** (Log-Structured Merge Tree):

```
How RocksDB writes data:
1. New write comes in → goes to in-memory buffer (MemTable)
2. MemTable fills up → flushed to disk as SSTable file (immutable)
3. Background compaction merges SSTables to remove old/duplicate entries
4. Reads check MemTable first, then SSTables on disk
```

This makes writes very fast (sequential disk writes) and reads reasonably fast (indexed files).

**In Flink**, each TaskManager (worker node) has its own RocksDB instance stored locally on disk. When a checkpoint happens, Flink uploads this RocksDB data to a remote store like S3.

---

### 2.3 What is a Watermark?

Events don't always arrive in order. Imagine 3 users clicking at the exact same second — one event might arrive 3 seconds late due to network delay.

If Flink is computing "events per minute", it needs to know: **"Has this minute finished? Can I close this window and emit the result?"**

A **Watermark** is Flink's way of saying: *"I believe all events up to timestamp T have arrived. I'm closing any windows that end before T."*

```
Timeline:  event(t=1) event(t=3) event(t=2, late!) event(t=5) ...
Watermark: Flink waits a bit (e.g., 5 seconds) before declaring a timestamp "done"
```

If you set allowed lateness to 5 seconds:
- Watermark = max_event_time_seen - 5 seconds
- Any event arriving within 5 seconds of window close is still included
- Events arriving after that go to a **side output** (separate stream for late data)

---

### 2.4 What is a Checkpoint?

A checkpoint is a **snapshot of Flink's entire state at a point in time**.

Simple analogy: Like a save point in a video game. If the game crashes, you restart from the last save — not from the beginning.

Flink checkpoints:
1. Pauses briefly (or uses barriers — explained below)
2. Saves all operator state (including Kafka offsets) to a remote store (S3, HDFS)
3. If the job crashes, Flink restores from the last successful checkpoint

**Checkpoint Barrier**: A special marker event Flink injects into the data stream. When an operator sees the barrier, it knows "snapshot your state now." Barriers flow through the pipeline like normal events — this means Flink can checkpoint without stopping the entire job.

```
Data stream:  event event event [BARRIER-42] event event event [BARRIER-43]
                                    ↓
                         Each operator snapshots state when it sees this barrier
```

---

### 2.5 What is Parallelism?

Flink splits a job into multiple **parallel subtasks** running simultaneously on different threads/machines.

If you have a Kafka topic with 8 partitions and set Flink parallelism to 8:
- Subtask 1 reads partition 0
- Subtask 2 reads partition 1
- ...and so on

Each subtask processes independently. This is how Flink handles 300K events/second — it's doing it across many subtasks in parallel.

---

## 3. Flink in Production — High Load with Java Config

### 3.1 Setting Up the Execution Environment

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

// Set global parallelism — how many parallel subtasks per operator
// Best practice: match your Kafka partition count
env.setParallelism(32);

// Enable checkpointing every 60 seconds
env.enableCheckpointing(60_000); // milliseconds

// Set checkpointing mode
// EXACTLY_ONCE: stronger guarantee, slightly more overhead
// AT_LEAST_ONCE: faster, but possible duplicates on recovery
env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

// How long a checkpoint is allowed to take before it's aborted
env.getCheckpointConfig().setCheckpointTimeout(300_000); // 5 minutes

// Minimum gap between end of one checkpoint and start of next
// Prevents checkpoint from running back-to-back under heavy load
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30_000); // 30 seconds

// Maximum concurrent checkpoints (usually keep at 1)
env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
```

---

### 3.2 Choosing State Backend — Heap vs RocksDB

```java
// Option 1: HashMapStateBackend (in JVM heap memory)
// Use when: state is small, latency is critical
// Risk: state lost if TaskManager dies (checkpoint saves it, but slower recovery)
env.setStateBackend(new HashMapStateBackend());

// Option 2: EmbeddedRocksDBStateBackend (on disk, local to TaskManager)
// Use when: state is large (millions of keys, large values)
// 'true' = enable incremental checkpointing (only upload changed data to S3, not full state)
env.setStateBackend(new EmbeddedRocksDBStateBackend(true));

// Where to store checkpoints remotely (S3 in this case)
env.getCheckpointConfig().setCheckpointStorage("s3://your-bucket/flink-checkpoints");
```

**Why we used RocksDB at Cisco**:
We had 150 enterprise tenants × 100 agents each = 15,000 keyed state entries, each holding rolling aggregation windows. This easily exceeded JVM heap. RocksDB stored this on disk while keeping hot entries in its MemTable (in-memory buffer), giving us the best of both worlds.

---

### 3.3 Reading from Kafka with Watermarks

```java
// Define Kafka source
KafkaSource<ContactCenterEvent> kafkaSource = KafkaSource.<ContactCenterEvent>builder()
    .setBootstrapServers("kafka-broker:9092")
    .setTopics("contact-center-events")
    .setGroupId("flink-contact-center-consumer")
    .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
    .setValueOnlyDeserializer(new ContactCenterEventDeserializer())
    .build();

// Watermark strategy:
// BoundedOutOfOrderness = "wait 10 seconds before closing a time window"
// This handles events arriving slightly late from distributed sources
WatermarkStrategy<ContactCenterEvent> watermarkStrategy =
    WatermarkStrategy
        .<ContactCenterEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
        .withTimestampAssigner(
            (event, recordTimestamp) -> event.getEventTimestamp() // use event's own timestamp
        );

// Create the stream
DataStream<ContactCenterEvent> eventStream = env
    .fromSource(kafkaSource, watermarkStrategy, "Kafka Contact Center Source");
```

---

### 3.4 Keyed State — Per-Tenant Aggregation

```java
// Key the stream by tenantId — all events for same tenant go to same subtask
// This ensures state for a tenant is in one place, not scattered across subtasks
DataStream<AgentMetrics> metricsStream = eventStream
    .keyBy(event -> event.getTenantId())
    .process(new TenantMetricsAggregator());


// The stateful operator
public class TenantMetricsAggregator
        extends KeyedProcessFunction<String, ContactCenterEvent, AgentMetrics> {

    // ValueState stores a single value per key (per tenantId here)
    // Flink manages this — persists it in checkpoint, restores on recovery
    private ValueState<TenantStats> tenantStatsState;

    @Override
    public void open(Configuration parameters) {
        // Register the state descriptor — give it a name and type
        ValueStateDescriptor<TenantStats> descriptor =
            new ValueStateDescriptor<>("tenant-stats", TenantStats.class);

        // Set TTL — auto-expire state for tenants inactive for 24 hours
        // Prevents unbounded state growth for tenants that stop sending events
        StateTtlConfig ttlConfig = StateTtlConfig
            .newBuilder(Time.hours(24))
            .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
            .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
            .build();
        descriptor.enableTimeToLive(ttlConfig);

        tenantStatsState = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void processElement(ContactCenterEvent event,
                               Context ctx,
                               Collector<AgentMetrics> out) throws Exception {

        // Get current state for this tenant (null if first event)
        TenantStats stats = tenantStatsState.value();
        if (stats == null) {
            stats = new TenantStats();
        }

        // Update stats
        stats.incrementCallCount();
        stats.updateHandleTime(event.getHandleDuration());

        // Save updated state back — Flink persists this in next checkpoint
        tenantStatsState.update(stats);

        // Emit result downstream
        out.collect(new AgentMetrics(event.getTenantId(), stats));
    }
}
```

---

### 3.5 Async I/O — Non-Blocking External Lookups

Without Async I/O, every Redis lookup blocks the task thread:
```
event1 → [wait 5ms for Redis] → event2 → [wait 5ms for Redis] → ...
// Processing rate: 200 events/sec max
```

With Async I/O, lookups happen in parallel:
```
event1 ──→ [Redis lookup in background]
event2 ──→ [Redis lookup in background]   → results collected as they return
event3 ──→ [Redis lookup in background]
// Processing rate: thousands of events/sec
```

```java
// Async function for Redis enrichment
public class AsyncRedisEnrichment
        extends RichAsyncFunction<ContactCenterEvent, EnrichedEvent> {

    private RedisAsyncClient redisClient;

    @Override
    public void open(Configuration parameters) {
        redisClient = RedisClient.create("redis://localhost:6379").connect().async();
    }

    @Override
    public void asyncInvoke(ContactCenterEvent event,
                            ResultFuture<EnrichedEvent> resultFuture) {
        // Non-blocking Redis call — returns a CompletableFuture
        CompletableFuture<String> redisFuture =
            redisClient.get("agent:" + event.getAgentId());

        redisFuture.thenAccept(agentInfo -> {
            EnrichedEvent enriched = new EnrichedEvent(event, agentInfo);
            resultFuture.complete(Collections.singleton(enriched));
        });
    }
}

// Apply async enrichment to the stream
// 'unorderedWait' = don't preserve event order (faster)
// 'orderedWait'   = preserve order (slightly slower, more memory)
DataStream<EnrichedEvent> enrichedStream = AsyncDataStream.unorderedWait(
    eventStream,
    new AsyncRedisEnrichment(),
    1000,               // timeout per async call in milliseconds
    TimeUnit.MILLISECONDS,
    100                 // max 100 concurrent async requests in flight at once
);
```

---

### 3.6 Side Output — Handling Late Events

Instead of silently dropping late events, route them to a separate stream:

```java
// Define an output tag for late events — like a label for a side channel
OutputTag<ContactCenterEvent> lateEventsTag =
    new OutputTag<ContactCenterEvent>("late-events"){};

// 5-minute tumbling window on event time
SingleOutputStreamOperator<AgentMetrics> mainStream = eventStream
    .keyBy(e -> e.getTenantId())
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .allowedLateness(Time.seconds(10))         // still accept events up to 10s late
    .sideOutputLateData(lateEventsTag)         // anything later goes to side output
    .aggregate(new MetricsAggregator());

// Get the side output stream of late events
DataStream<ContactCenterEvent> lateEvents = mainStream.getSideOutput(lateEventsTag);

// Write late events to a separate Kafka topic for reprocessing later
lateEvents.sinkTo(
    KafkaSink.<ContactCenterEvent>builder()
        .setBootstrapServers("kafka-broker:9092")
        .setRecordSerializer(new ContactCenterEventSerializer("late-events-topic"))
        .build()
);
```

---

## 4. Checkpointing — What It Is, Common Issues & Java Fixes

### 4.1 How a Checkpoint Actually Works (Step by Step)

```
1. JobManager sends "trigger checkpoint N" signal
2. Each Kafka source subtask:
   a. Notes current Kafka offset (e.g., partition 3, offset 10045)
   b. Injects a BARRIER marker into its output stream
3. Barrier flows downstream through all operators
4. When an operator receives barriers from ALL its input streams:
   a. It snapshots its local state (writes to RocksDB or heap)
   b. Uploads snapshot to S3
   c. Forwards the barrier downstream
5. When ALL operators have confirmed → checkpoint is complete
6. JobManager records checkpoint as "successful" with all offsets + state locations

On failure:
1. Flink restarts the job
2. Restores each operator's state from S3 snapshot
3. Resets Kafka consumer offsets to what was recorded in checkpoint
4. Replays events from those offsets → exactly-once (with idempotent sinks)
```

---

### 4.2 Issue: Checkpoint Timeouts — Fix with Incremental Checkpointing

**What happened**: Full RocksDB state was being uploaded to S3 every checkpoint. As state grew to GBs, upload took longer than the timeout.

**Fix — Incremental checkpointing**: Instead of uploading the full state every time, only upload what changed since the last checkpoint.

```java
// Enable incremental checkpointing — the 'true' argument
// Flink tracks which RocksDB SSTable files are new since last checkpoint
// Only those new files are uploaded — previously uploaded files are reused
EmbeddedRocksDBStateBackend rocksDB = new EmbeddedRocksDBStateBackend(true);
env.setStateBackend(rocksDB);

// Also tune how many threads upload checkpoint files to S3 in parallel
// Default is 1 — increasing this speeds up checkpoint upload under large state
Configuration config = new Configuration();
config.set(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS, 1);
// RocksDB transfer threads (set via flink-conf.yaml or programmatically)
// state.backend.rocksdb.checkpoint.transfer.thread.num: 4
env.configure(config);
```

---

### 4.3 Issue: Slow Operators Blocking Checkpoints — Fix with Unaligned Checkpoints

**What happened**: One operator did synchronous DB calls. When a checkpoint barrier arrived, it had to wait for all in-flight DB calls to finish before snapshotting. This delayed the entire checkpoint.

**Normal (aligned) checkpointing**:
```
Barrier arrives at operator → wait for all in-flight data to drain → snapshot → forward barrier
// Problem: in-flight data can be thousands of events → long wait
```

**Unaligned checkpointing**:
```
Barrier arrives → snapshot immediately (including in-flight data) → forward barrier
// In-flight data is included in the snapshot itself
// Checkpoint is larger but much faster
```

```java
// Enable unaligned checkpoints
env.getCheckpointConfig().enableUnalignedCheckpoints();

// Unaligned checkpoints make sense when:
// - You have backpressure regularly
// - Checkpoint duration is the bottleneck
// Trade-off: checkpoint files are larger (include buffered in-flight data)
```

Also fix the root cause — replace synchronous DB calls with Async I/O (see section 3.5).

---

### 4.4 Issue: S3 Filling Up — Fix with Retention Config

```java
// Keep only last 3 successful checkpoints on S3
// Older ones are automatically deleted
env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
env.getCheckpointConfig()
   .setExternalizedCheckpointCleanup(
       CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
       // Use DELETE_ON_CANCELLATION if you don't need checkpoints after job cancel
   );

// In flink-conf.yaml (or via config):
// state.checkpoints.num-retained: 3
```

---

### 4.5 Issue: Job Not Recovering After Restart — Fix with HA Config

**What happened**: JobManager stored checkpoint metadata in memory. When JobManager itself crashed, it forgot where the checkpoints were. Job restarted from scratch.

**Fix — High Availability (HA)**:

```yaml
# flink-conf.yaml
high-availability: zookeeper
high-availability.zookeeper.quorum: zk1:2181,zk2:2181,zk3:2181
high-availability.storageDir: s3://your-bucket/flink-ha
# ZooKeeper stores pointers to checkpoint locations
# Actual checkpoint data stays in S3
# New JobManager reads ZooKeeper → finds checkpoint location → restores
```

```java
// In code — configure checkpoint storage explicitly (not just directory)
env.getCheckpointConfig().setCheckpointStorage(
    new FileSystemCheckpointStorage("s3://your-bucket/flink-checkpoints")
);
```

---

### 4.6 Checkpoint vs Savepoint

| | Checkpoint | Savepoint |
|---|---|---|
| **What it is** | Automatic periodic snapshot for fault tolerance | Manual snapshot for planned operations |
| **Who triggers it** | Flink automatically on schedule | You trigger manually via CLI or REST API |
| **When used** | Crash recovery | Job upgrade, rescaling, A/B testing |
| **Deleted automatically?** | Yes, based on retention config | No, you delete it manually |
| **Format** | Optimized for speed | Stable, portable format (survives code changes) |

```bash
# Trigger a savepoint before deploying new code
flink savepoint <jobId> s3://your-bucket/savepoints/

# Start new job version from that savepoint
flink run -s s3://your-bucket/savepoints/savepoint-abc123 your-job.jar

# This gives you zero-downtime upgrades:
# Old job stops → savepoint created → new job starts from that savepoint
# No events lost, no reprocessing needed
```

---

## 5. Flink vs Spark Streaming vs Kafka Streams

### Comparison Table

| Feature | Apache Flink | Spark Structured Streaming | Kafka Streams |
|---|---|---|---|
| **Processing model** | True streaming — processes each event as it arrives | Micro-batch — collects events for X seconds, then processes the batch | True streaming |
| **Latency** | Milliseconds | Seconds (minimum = batch interval, typically 1–30s) | Milliseconds |
| **State management** | First-class. RocksDB backend, fault-tolerant, TTL support | Limited. `mapGroupsWithState` is complex to manage | Local RocksDB tied to Kafka partitions |
| **Exactly-once** | Yes — end-to-end with Kafka + transactional sinks | Yes — with idempotent sinks and WAL | Yes — within Kafka ecosystem only |
| **Event-time & watermarks** | Native, mature, flexible | Supported but less flexible | Basic support |
| **Windowing** | Tumbling, sliding, session, custom | Tumbling, sliding | Tumbling, hopping, session |
| **Scales independently of Kafka** | Yes — Flink parallelism separate from partition count | Yes | No — max parallelism = number of Kafka partitions |
| **Deployment** | Needs a Flink cluster (standalone, YARN, K8s) | Needs a Spark cluster | Embedded in your Java app — no cluster needed |
| **Operational complexity** | Medium-High | High | Low |
| **Best for** | Complex stateful streaming, low latency, multi-source joins, CEP | Batch + stream unified, SQL-heavy pipelines | Simple Kafka-to-Kafka transforms, small teams |

---

### When to pick each

**Pick Flink when**:
- You need sub-second latency
- You have complex stateful aggregations (e.g., per-tenant rolling windows)
- You need to join multiple streams together
- You need savepoints for zero-downtime upgrades
- You process events out of order and need precise event-time handling

**Pick Spark Streaming when**:
- You already run Spark for batch jobs and want one unified codebase
- Your team knows Spark SQL / DataFrames well
- Latency of a few seconds is acceptable
- You need heavy SQL-based transformations

**Pick Kafka Streams when**:
- You want zero infrastructure overhead (no separate cluster)
- Your pipeline is simple: read from Kafka → transform → write to Kafka
- Team is small and you want simple deployment (it's just a Java library)
- You don't need complex multi-source joins or CEP

---

## 6. Why Flink over Spark?

### The specific reasons at Cisco Webex

**1. Latency requirement ruled out Spark immediately**

Spark Structured Streaming works by collecting events for N seconds (micro-batch), then processing that batch. Minimum latency = batch interval.

Our SLA was <5 seconds end-to-end. With a 1-second Spark batch interval, just the batching alone consumed 20% of our latency budget — before any processing happened. Flink processes each event the moment it arrives: typically 50–200ms end-to-end.

**2. Stateful aggregations were complex in Spark**

In Spark, managing per-tenant state across time required `mapGroupsWithState` or `flatMapGroupsWithState`. This API is verbose, hard to test, and under heavy load we saw increasing memory pressure because Spark keeps all state in executor JVM heap.

In Flink, keyed state is a first-class concept. You define a `ValueState` or `MapState` with a descriptor, and Flink handles persistence, recovery, and TTL automatically. With RocksDB backend, state spills to disk instead of bloating the heap.

**3. Zero-downtime upgrades via savepoints**

We deployed 20+ times per month. With Spark Streaming, a job upgrade meant stopping the job and restarting — risking data loss or requiring manual offset management.

With Flink savepoints, the workflow was:
```
trigger savepoint → stop old job → start new job from savepoint → zero data loss
```
This is built into Flink. Spark has no equivalent.

**4. Better backpressure visibility**

When a downstream operator (e.g., Elasticsearch sink) was slow, Flink's backpressure mechanism automatically slowed upstream operators — preventing out-of-memory errors. The Flink Web UI shows backpressure per operator visually with a percentage.

In Spark, you diagnose lag by looking at batch durations in the Spark UI — less granular and harder to pinpoint which operator in the DAG is the bottleneck.

---

## 7. General Data Engineering Questions

### Q: What is exactly-once semantics and how do you achieve it end-to-end?

**What "exactly-once" means**: Every event is processed and reflected in the output exactly one time — not zero times (loss) and not two times (duplicate).

This is hard because failures can happen at any point. Without care, on recovery you might reprocess events that were already written to the sink.

**How Flink achieves it**:

```
Source (Kafka)     →    Flink Processing    →    Sink (Kafka / DB)
     ↓                        ↓                        ↓
Offsets stored          State checkpointed        2-phase commit
in Flink state          to S3                     or idempotent write
```

Step by step:
1. Kafka offsets are stored in Flink state (not committed to Kafka directly)
2. On checkpoint: state + offsets are snapshotted atomically to S3
3. Sink uses **two-phase commit**: writes are prepared but not finalized until checkpoint succeeds
4. On recovery: Flink rewinds Kafka to checkpointed offsets, replays events, sink discards duplicate pre-committed writes

```java
// Kafka sink with exactly-once (uses Kafka transactions internally)
KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
    .setBootstrapServers("kafka:9092")
    .setRecordSerializer(
        KafkaRecordSerializationSchema.builder()
            .setTopic("output-topic")
            .setValueSerializationSchema(new SimpleStringSchema())
            .build()
    )
    // EXACTLY_ONCE uses Kafka transactions — producer wraps writes in a transaction
    // committed only when Flink checkpoint succeeds
    .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
    .setTransactionalIdPrefix("flink-txn")
    .build();
```

---

### Q: How do you handle schema evolution?

**The problem**: Your Kafka events have a schema. Your Flink job reads that schema. If you add a new field to the event, old Flink code doesn't know about it — could crash or silently ignore it.

**Solution — Schema Registry + Avro**:

```
Producer writes event → validates against schema in registry → publishes to Kafka
Consumer (Flink) reads → fetches schema from registry by ID → deserializes safely
```

Avro supports **backward compatibility**: a new schema can read data written by the old schema. New fields must have defaults.

```java
// Flink reading Avro with schema registry
KafkaSource<GenericRecord> source = KafkaSource.<GenericRecord>builder()
    .setBootstrapServers("kafka:9092")
    .setTopics("events")
    .setValueOnlyDeserializer(
        ConfluentRegistryAvroDeserializationSchema.forGeneric(
            schema,
            "http://schema-registry:8081"
        )
    )
    .build();
```

---

### Q: How do you prevent unbounded state growth?

**The problem**: If your Flink job runs for months, state keeps accumulating for all keys ever seen — even tenants that stopped sending data years ago.

**Solution — State TTL**:

```java
ValueStateDescriptor<TenantStats> descriptor =
    new ValueStateDescriptor<>("stats", TenantStats.class);

StateTtlConfig ttl = StateTtlConfig
    .newBuilder(Time.hours(24))  // expire state after 24 hours of no updates
    .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite) // reset TTL on each write
    .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
    .build();

descriptor.enableTimeToLive(ttl);
// Flink now automatically cleans up state for keys not updated in 24 hours
```

---

### Q: How do Kafka partitions and Flink parallelism relate?

```
Kafka Topic: 8 partitions  →  Flink Source parallelism: 8
  partition-0  →  subtask-0
  partition-1  →  subtask-1
  ...
  partition-7  →  subtask-7

// Each subtask exclusively reads one partition
// If you set parallelism to 4: each subtask reads 2 partitions
// If you set parallelism to 16: 8 subtasks are idle (no partition to read)
```

**Best practice**: Set source parallelism = number of Kafka partitions. You can scale downstream operators independently based on their processing cost.

```java
DataStream<Event> kafkaStream = env
    .fromSource(kafkaSource, watermarkStrategy, "Kafka Source")
    .setParallelism(8);  // match partition count

DataStream<EnrichedEvent> enriched = kafkaStream
    .process(new HeavyEnrichmentFunction())
    .setParallelism(16);  // this operator is CPU-heavy, scale it up independently
```

---

### Q: What is data skew and how do you handle it in Flink?

**The problem**: You key events by `tenantId`. One enterprise tenant sends 90% of all your traffic. All their events go to one subtask — that subtask is overwhelmed while the others are idle.

**Solutions**:

```java
// Option 1: Two-stage aggregation (pre-aggregate with random sub-key, then combine)
// Stage 1: add random suffix to key to spread load
eventStream
    .keyBy(e -> e.getTenantId() + "_" + (e.hashCode() % 10))  // 10 sub-keys per tenant
    .window(TumblingEventTimeWindows.of(Time.seconds(30)))
    .aggregate(new PartialAggregator())  // partial count per sub-key

// Stage 2: combine partial results by real tenant key
    .keyBy(partial -> partial.getTenantId())
    .window(TumblingEventTimeWindows.of(Time.seconds(30)))
    .reduce(new CombinePartials());  // sum up all sub-key results

// Option 2: Rebalance operator (round-robin shuffle before heavy processing)
eventStream
    .rebalance()  // distributes events evenly across subtasks regardless of key
    .process(new StatelessEnrichment());
```

---

### Q: How do you write data to Elasticsearch from Flink efficiently?

```java
// Elasticsearch sink with bulk writes
// Flink buffers events and flushes in batches — much more efficient than one-by-one
ElasticsearchSink.Builder<AgentMetrics> esSinkBuilder =
    new ElasticsearchSink.Builder<>(
        Arrays.asList(new HttpHost("es-host", 9200, "http")),
        new ElasticsearchSinkFunction<AgentMetrics>() {
            @Override
            public void process(AgentMetrics metric,
                                RuntimeContext ctx,
                                RequestIndexer indexer) {
                // Build index request
                IndexRequest request = Requests
                    .indexRequest()
                    .index("agent-metrics-" + metric.getDate())
                    // Deterministic ID = idempotent: re-indexing same event is safe
                    .id(metric.getTenantId() + "_" + metric.getAgentId() + "_" + metric.getWindowStart())
                    .source(metric.toMap());
                indexer.add(request);
            }
        }
    );

// Flush after 10,000 actions OR 5MB OR 5 seconds — whichever comes first
esSinkBuilder.setBulkFlushMaxActions(10_000);
esSinkBuilder.setBulkFlushMaxSizeMb(5);
esSinkBuilder.setBulkFlushInterval(5_000);

// Retry on failure with backoff
esSinkBuilder.setBulkFlushBackoff(true);
esSinkBuilder.setBulkFlushBackoffType(ElasticsearchSinkBase.FlushBackoffType.EXPONENTIAL);
esSinkBuilder.setBulkFlushBackoffRetries(5);
esSinkBuilder.setBulkFlushBackoffDelay(1_000);

metricsStream.addSink(esSinkBuilder.build());
```

---

*Last updated: March 2026*