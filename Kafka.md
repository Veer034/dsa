## KAFKA

### Core Concepts

* [x] **What is Kafka and why is it used?**
    * Apache Kafka is a distributed event-streaming platform used to publish, store, and process streams of records in real time with high throughput and low latency.
    * **Why Kafka is used**
        * **Decoupling systems:** Producers and consumers are independent (loose coupling).
        * **High throughput & scalability:** Data is partitioned and distributed across brokers.
        * **Durability & fault tolerance:** Messages are persisted and replicated.
        * **Real-time processing:** Supports stream processing and near-real-time analytics.
    * **Typical use cases:** Event-driven microservices, log aggregation, real-time analytics, data pipelines, CDC.

    * **Production War Story — Follow-up (Expert): You migrated from RabbitMQ to Kafka in production. What broke, what surprised you, and what would you do differently?**
        * **What broke:**
            * RabbitMQ consumers received messages pushed to them — Kafka consumers pull. Teams underestimated the polling loop discipline needed (`max.poll.interval.ms` violations causing unexpected rebalances during slow DB writes).
            * RabbitMQ message TTL is per-message; in Kafka, retention is per-topic. Teams accidentally consumed "replayed" historical events on consumer restart because `auto.offset.reset=earliest` was set.
            * DLQ semantics differ completely — RabbitMQ has native dead-lettering; Kafka requires you to build it explicitly.
        * **What surprised us:**
            * Kafka's consumer lag concept — in RabbitMQ, queue depth is the metric. In Kafka, a consumer at lag=0 doesn't mean messages aren't stacking up; it means you're caught up *at that moment*. Under burst traffic, lag spiked silently before anyone noticed.
            * Partition ordering guarantee is stronger than expected — same-key ordering helped eliminate a race condition that existed in RabbitMQ's competing-consumer model.
        * **What I'd do differently:**
            * Set `auto.offset.reset=latest` for new consumer groups in production — never `earliest` unless you explicitly want replay.
            * Pre-tune `max.poll.interval.ms` before go-live based on p99 processing time of the heaviest message type.
            * Build a DLQ framework from day one — don't retrofit it after your first poison pill message causes a production outage.

---
* [x] **Explain Kafka architecture (Broker, Topic, Partition, Producer, Consumer)**

![Image](https://daxg39y63pxwu.cloudfront.net/images/blog/apache-kafka-architecture-/image_589142173211625734253276.png)

![Image](https://developers.redhat.com/sites/default/files/RHOSAK%20LP1%20Fig4.png)

![Image](https://www.researchgate.net/profile/Ana-Filipa-Nogueira/publication/354426180/figure/fig2/AS%3A1065575086317568%401631064306446/Producers-and-consumers-in-a-Kafka-framework-Extracted-from-https-de-confl-uent.png)



* **Broker:**  A Kafka server that **stores data and serves reads/writes**. A cluster has multiple brokers for
  scalability and fault tolerance.

* **Topic:** A **logical stream of events** (e.g., `orders`). Data is written and read per topic.

* **Partition:** A **physical split of a topic**. Enables **parallelism and scalability**. Order is guaranteed
  **only within a partition**.

* **Producer:** An application that **publishes messages to a topic**. It chooses the partition (by key or
  round-robin).

* **Consumer:** An application that **reads messages from topics**. Consumers in a **consumer group** split
  partitions so each partition is processed by only one consumer in the group.


---
* [x] **What is replication factor and how does it work?**
    * the replication factor (RF) is the number of copies of each partition stored across different brokers.
    * **How it works**
        * Each partition has 1 Leader and (RF − 1) Followers.
        * Producers write and consumers read from the Leader.
        * Followers replicate data from the leader.
        * ISR (In-Sync Replicas) are replicas fully caught up with the leader.
    * **Why it’s used**
        * Fault tolerance (no data loss on broker failure).
        * High availability.
        * Safe durability when combined with acks=all.

    * **Production War Story — Follow-up (Expert): You have RF=3 and `acks=all`, yet you experienced data loss after a broker crash. Walk through how this is possible and exactly what configuration was wrong.**
        * **Root cause — `min.insync.replicas` not set correctly:** With RF=3 and `acks=all`, if `min.insync.replicas=1` (default), Kafka only requires 1 replica (the leader itself) to acknowledge. If 2 brokers are already down and the leader crashes, the single remaining replica may not have the latest messages → **data loss despite `acks=all`**.
        * **The safe configuration for zero data loss:**
        ```properties
        # Broker config
        min.insync.replicas=2        # at least 2 replicas must ack (including leader)
        unclean.leader.election.enable=false  # NEVER elect an out-of-sync replica as leader

        # Producer config
        acks=all
        retries=2147483647
        enable.idempotence=true
        ```
        * **`unclean.leader.election.enable=true` is the silent killer:** If all ISR replicas are down and an out-of-sync replica is allowed to become leader, it serves stale data. Messages committed on the old leader are lost permanently — without any error to the consumer.
        * **What actually happens during a network partition:** A network partition isolates 2 brokers. `min.insync.replicas=1` allows the single remaining broker to keep accepting writes. When the partition heals, Kafka truncates the diverged broker’s log to align with the rejoining replicas, **silently discarding committed messages**.
        * **Fix:** Monitor `UnderReplicatedPartitions` metric. Alert immediately when it exceeds 0 — it means ISR has shrunk and you’re operating below your durability guarantee.

---
* [x] **Explain the role of ZooKeeper in Kafka (and KRaft mode in new versions)**


![Image](https://www.xenonstack.com/hubfs/Kafka-zookeeper.png)

![Image](https://docs.cloudera.com/runtime/7.3.1/kafka-overview/images/kafka-kraft-metadata-manage.svg)

![Image](https://images.ctfassets.net/gt6dp23g0g38/1b3EQqsnjLUaGuqMQYK7fr/c9dd3ffb9242e61be06036ce4599a6bb/Kafka_Internals_048.png)

* In older versions of **Apache Kafka**, **Apache ZooKeeper** was used for **cluster coordination**.
* **Responsibilities**
    * Broker registration and liveness tracking
    * Controller election
    * Topic/partition metadata storage
    * Leader election for partitions
* **Limitation**: Extra dependency, operational complexity, and scalability bottleneck.

---

* Kafka now uses **KRaft (Kafka Raft)** to **replace ZooKeeper** with a built-in consensus mechanism.
* **Responsibilities**
    * Metadata management (topics, partitions, configs)
    * Controller quorum using Raft protocol
    * Leader elections without ZooKeeper

* **Benefits**
    * No external dependency
    * Faster recovery and better scalability
    * Simpler operations and deployments

---
---
* [x] HOW Leader Election in Kafka — Old vs New (Interview-Level)

![Image](https://i0.wp.com/vkontech.com/wp-content/uploads/2024/10/2-1.png?ssl=1)

![Image](https://cloudurable.com/images/kafka-architecture-kafka-zookeeper-coordination.png)

![Image](https://docs.arenadata.io/en/ADStreaming/current/concept/_images/kafka/kraft_arch_dark.svg)



* In older **Apache Kafka**, leader election was handled via **Apache ZooKeeper**.
* **How it worked**
    1. Brokers register as **ephemeral znodes** in ZooKeeper.
    2. One broker becomes **Controller** by creating a special znode.
    3. If a **partition leader fails**, ZooKeeper detects session expiry.
    4. Controller picks a **new leader from ISR** and updates metadata in ZooKeeper.
    5. Brokers read the change and switch to the new leader.

* **Issue**: Extra network hops + ZooKeeper dependency → slower failover.

---



* In modern Kafka, ZooKeeper is removed and Kafka uses **Raft** internally (KRaft).
* **How it works**
    1. A set of **controller nodes** form a **Raft quorum**.
    2. One controller is elected as **leader via Raft voting**.
    3. Metadata changes (leader, ISR) are written to the **metadata log**.
    4. If the controller leader fails → quorum **re-elects leader automatically**.
    5. Partition leader is chosen from ISR and propagated directly to brokers.
* **Benefit**: Faster, simpler, no external system.

---

* **ZooKeeper mode**: ZooKeeper detects failure → controller elects leader.
* **KRaft mode**: Kafka uses **Raft quorum** internally → faster, simpler leader election.

    * **Production War Story — Follow-up (Expert): Your Kafka cluster lost its Controller (ZooKeeper mode) at peak traffic and consumers stopped processing for 45 seconds. What happened step-by-step and how did you reduce that to under 5 seconds?**
        * **What happened:** The Controller broker JVM GC paused for 8s, causing its ZooKeeper session to expire (`zookeeper.session.timeout.ms=6000`). ZooKeeper declared the Controller dead. All other brokers raced to become the new Controller by creating the `/controller` znode. The new Controller had to re-read all partition metadata from ZooKeeper and reissue leader election for every partition. With 800 partitions across 3 topics, this took 40+ seconds. During this time producers got `NotLeaderForPartitionException` and consumers stalled.
        * **Root causes:**
            * Default `zookeeper.session.timeout.ms=6000` too short — a single GC pause triggers false failover.
            * 800 partitions meant Controller restart was slow — O(partitions) ZooKeeper reads.
        * **Fixes applied:**
            * Increased `zookeeper.session.timeout.ms=30000` to tolerate GC pauses.
            * Tuned Controller broker JVM: `-XX:MaxGCPauseMillis=200`, G1GC with `InitiatingHeapOccupancyPercent=35`.
            * **Migrated to KRaft mode:** Raft-based controller election takes < 1 second. No ZooKeeper session expiry involved. Partition metadata is in the Raft log — no bulk re-read.
        * **KRaft failover timing:** Raft election requires a quorum vote among controller nodes. With 3 controller nodes, election completes in 1-2 RTTs — typically under 500ms. The new controller already has all metadata in its local Raft log, so no partition-by-partition re-read.
        * **Monitoring signal:** Alert on `ActiveControllerCount=0` — this means no controller exists and your cluster is in a degraded state where no partition leader elections can happen.

---

* [x] **Why Kafka was developed, when already had TIBCO,RabbitMQ?**
    * Earlier systems were queues; Kafka was the first widely adopted distributed commit log built for large-scale event streaming.
    * What Kafka Introduced (New Concept)
        * Immutable, append-only distributed log
        * Retention-based consumption (not delete-on-read)
        * Consumer-managed offsets
        * Scale-out via partitions


### Producers

* [x] **How does a producer publish messages to Kafka?**
    * A Kafka producer serializes the message, selects a partition, writes to the leader broker, waits for ACKs, and receives the offset.
    * Steps
        1. Serialize message : Producer converts key/value to bytes (e.g., JSON, Avro).
        2. Choose partition :
            1. If key present → hash(key) → same partition (ordering).
            2. If no key → round-robin across partitions.
        3. Send to leader broker : Producer writes only to the partition leader.
        4. Replication & ACKs :
            1. Followers replicate the data.
            2. Producer gets acknowledgment based on acks (0, 1, all).
        5. Offset assigned : Kafka assigns a monotonically increasing offset per partition.
---
* [x] **What are producer acknowledgments (acks=0, 1, all)?**
    * acks defines the durability–latency tradeoff for Kafka producers.
    * **Types of acks**
        * acks = 0
            * Producer does not wait for any response.
            * 🔹 Fastest, possible data loss.
            * Use case: metrics, logs where loss is acceptable.

        * acks = 1 (default)
            * Producer waits for leader broker only.
            * 🔹 Balanced performance, leader failure may lose data.

        * acks = all / -1
            * Producer waits for all ISR replicas to acknowledge.
            * 🔹 Highest durability, slightly higher latency.
            * Use case: payments, orders, critical data.

    * **Production War Story — Follow-up (Expert): With `acks=all`, your producer latency spiked from 5ms to 800ms during a rolling broker restart. What was happening and how did you fix it without sacrificing durability?**
        * **Root cause — ISR shrink during restart:** During a rolling restart, the restarting broker leaves ISR. If `min.insync.replicas=2` and RF=3, when 1 broker restarts, only 2 are in ISR — still fine. But if `replica.lag.time.max.ms` is too tight and the restarting broker is slow to rejoin ISR, you can temporarily have ISR=1 (just the leader). With `acks=all` + `min.insync.replicas=2`, the producer blocks waiting for a 2nd replica that isn’t available — requests pile up until timeout.
        * **What actually caused 800ms latency:** `replica.lag.time.max.ms=10000` (default), but our restarting broker took 12s to fully replay its log and rejoin ISR. During those 12 seconds, every producer send with `acks=all` either blocked or threw `NotEnoughReplicasException`.
        * **Fixes:**
            ```properties
            # Give replicas more time to rejoin ISR before being considered lagging
            replica.lag.time.max.ms=30000

            # Producer: don't wait forever, fail fast and let retry logic handle it
            delivery.timeout.ms=30000
            request.timeout.ms=5000
            retries=2147483647
            retry.backoff.ms=100
            ```
        * **Operational fix:** During planned rolling restarts, temporarily set `min.insync.replicas=1` via dynamic config for non-critical topics, then restore after restart completes. For payment topics — never reduce below 2.
        * **Better long-term fix:** Pre-warm brokers before rejoining the cluster. Increase log segment size to reduce replay time. Use `unclean.leader.election.enable=false` always.

---
* [x] **Explain idempotent producer**
    * Idempotent producer prevents duplicate messages during retries by using producer IDs and sequence numbers.

    * **Production War Story — Follow-up (Expert): You enabled `enable.idempotence=true` but still saw duplicate records in your consumer. How is that possible and where was the bug?**
        * **Idempotent producer covers producer→broker duplication only.** It assigns each producer a `PID` (Producer ID) and a monotonically increasing sequence number per partition. If the broker receives the same (PID, partition, sequence) twice due to a retry, it deduplicates at the broker level.
        * **Where it does NOT help:**
            1. **Consumer-side reprocessing:** If the consumer crashes after processing but before committing the offset, it re-reads and reprocesses the same message. Idempotent producer has nothing to do with this — the consumer must be idempotent itself.
            2. **Producer restart:** On JVM restart, the producer gets a **new PID**. The broker can no longer deduplicate against the old PID. Messages sent just before the crash that the broker already committed will be re-sent with a new PID and accepted as new records.
            3. **Multiple producer instances:** Two pods with the same `transactional.id` will compete, but two pods without it produce independently — both can produce the same logical event if they both process the same upstream event (e.g., both read from the same DB row and produce without coordination).
        * **The real fix for end-to-end exactly-once:** Combine `enable.idempotence=true` + `transactional.id` (for atomic produce+offset commit in Kafka Streams / read-process-write flows) + **consumer-side idempotency** using a deduplication key stored in Redis or DB with a TTL equal to your max expected redelivery window.
        * **Production pattern for payment events:**
        ```java
        // Consumer side: check + process atomically
        String dedupKey = "payment:" + record.key() + ":" + record.offset();
        if (redis.setIfAbsent(dedupKey, "1", Duration.ofHours(24))) {
            paymentService.process(record.value()); // only processes once
        }
        // else: silently skip duplicate
        ```

---
* [x] **What is producer batching and compression?**
    * Batching reduces network calls by sending messages in bulk, and compression reduces payload size to improve Kafka throughput.
    * Controlled by `batch.size` and `linger.ms`
    * Messages for the same partition are batched together
    * Producer compresses message batches before sending : gzip, snappy, lz4, zstd

    * **Production War Story — Follow-up (Expert): You increased `linger.ms` from 0 to 20ms to improve batching, but your p99 producer latency went from 8ms to 180ms under certain traffic patterns. What happened?**
        * **Root cause — batch accumulation under bursty traffic:** `linger.ms=20` means the producer waits up to 20ms to fill a batch before sending. Under bursty traffic, when messages arrive in bursts followed by quiet periods, every batch accumulates for the full 20ms even when it could have been sent earlier with just 3-4 messages.
        * **Compound issue — `buffer.memory` exhaustion:** When downstream brokers were slow (GC pause), send buffers filled up. With `linger.ms=20`, batches accumulate longer, filling `buffer.memory` faster. Once full, the producer blocks for `max.block.ms` (default 60s) before throwing `TimeoutException`.
        * **The right tuning approach:**
        ```properties
        # Start conservative
        linger.ms=5                  # not 0 (wastes batching), not too high
        batch.size=65536             # 64KB per batch — tune based on message size
        compression.type=lz4         # fastest compression, ~2x ratio
        buffer.memory=67108864       # 64MB — increase for high-throughput producers
        max.block.ms=5000            # fail fast instead of blocking 60s
        ```
        * **Rule of thumb:** `linger.ms` should be <= your acceptable p99 latency budget minus broker processing time. For payment APIs: `linger.ms=0` (latency matters more than throughput). For event pipelines: `linger.ms=5-20` is fine.
        * **Compression choice by use case:**
            * `lz4` — best for high-throughput, CPU-sensitive producers (lowest CPU overhead)
            * `zstd` — best compression ratio for archival topics (reduces storage costs)
            * `snappy` — good middle ground, widely supported
            * `gzip` — avoid for real-time; highest CPU, slowest compression


---
* [x] **How to ensure message ordering in Kafka?**
    * Kafka guarantees ordering only per partition, so use the same key to route related messages to the same partition.
    * **What is NOT guaranteed**
        * Ordering across partitions ❌
        * Ordering across different keys ❌

    * **Production War Story — Follow-up (Expert): You have strict per-user ordering (user's events must be processed in order). Your service scaled to 10 consumer instances, and users started seeing events out of order. Exactly what broke and how did you fix it?**
        * **What broke:** Messages were being produced with the `userId` as the partition key — correct. But the consumer was using `@Async` to process each record in a thread pool after polling. The poll loop returned 50 records for partition 3 (all for different users, but some for the same user). The async thread pool processed them concurrently — two records for `userId=42` ran simultaneously on different threads, and the slower one (DB write) completed second but represented an earlier event.
        * **Root cause:** Kafka guarantees ordering on the broker/partition side. The moment you parallelize processing *within* a consumer using async threads, you break the ordering guarantee yourself.
        * **Fixes:**
            1. **Process synchronously per partition:** Don't use `@Async` or thread pools inside Kafka listener methods. Process each record sequentially. Kafka’s parallelism comes from partitions, not threads per consumer.
            2. **Per-key ordering with concurrent processing:** If you need concurrency, route to a `ConcurrentHashMap<String, Queue>` keyed by `userId` and drain each user's queue with a single dedicated thread. Same-key records are always processed sequentially.
            3. **`max.concurrency` in Spring Kafka:** Using `@KafkaListener(concurrency="3")` spawns 3 consumer threads — each assigned different partitions. Ordering is preserved *per partition* since each partition has exactly one consuming thread.
        ```java
        // WRONG — breaks ordering
        @KafkaListener(topics = "user-events")
        public void consume(ConsumerRecord<String, Event> record) {
            executor.submit(() -> processEvent(record)); // concurrent = ordering broken
        }

        // CORRECT — per-partition parallelism, in-order per partition
        @KafkaListener(topics = "user-events", concurrency = "6") // 6 threads = 6 partitions max
        public void consume(ConsumerRecord<String, Event> record) {
            processEvent(record); // synchronous, sequential per partition
        }
        ```
        * **Partition count = max parallelism:** If you have 6 partitions, you get 6-way parallelism maximum. Adding a 7th consumer thread gains nothing — one thread will be idle. Partition count must be set correctly upfront (cannot reduce later without data loss risk).

### Consumers

* [x] **What is a consumer group?**
    * A consumer group enables parallel processing and scalability by ensuring each partition is processed by only one consumer in the group.
---
* [x] **How does partition assignment work in consumer groups?**
    * Partition assignment is handled by the group leader using an assignor strategy to evenly and safely distribute partitions among consumers.
---
* [x] **Explain offset management - auto-commit vs manual commit**
    * Auto-commit: Kafka commits offsets automatically at intervals.
        * ✔ Simple, ❌ risk of message loss if processing fails after commit.
    * Manual commit: Application commits offsets after successful processing.
        * ✔ Better reliability, ❌ more code and careful handling needed.
    * **Interview takeaway:** Use auto-commit for simple consumers; manual commit for critical processing.

    * **Production War Story — Follow-up (Expert): Your consumer used `enable.auto.commit=true` and you saw messages getting processed twice after a pod restart. Walk through the exact sequence of events that caused this.**
        * **The sequence:**
            1. Consumer polls 100 records (offsets 1000–1099). `auto.commit.interval.ms=5000`.
            2. Consumer processes records 1000–1089 (90 messages) in 4.8 seconds.
            3. At 5 seconds, auto-commit fires — BUT it commits the offset that was returned by the *last poll*, which was offset 1000 (start of the batch), not 1089 (last processed).
            4. Actually, auto-commit commits the offset of the *last returned record of the last poll* — offset 1099 — even though records 1090–1099 haven’t been processed yet.
            5. Pod crashes at record 1092.
            6. On restart, consumer starts from committed offset 1100 — records 1093–1099 are **silently skipped**.
        * **The inverse scenario (duplicates):** Auto-commit fires at offset 1099. Pod crashes immediately after. On restart, offset is 1099 but business logic for records 1090–1099 was never completed (DB write failed mid-batch). Records 1090-1099 need reprocessing but Kafka thinks they’re done.
        * **The real contract:** Auto-commit gives you **at-most-once** (can skip) or creates silent gaps. It never guarantees at-least-once. For production critical consumers, always use `enable.auto.commit=false` with `AckMode.MANUAL_IMMEDIATE` and commit *after* successful processing:
        ```java
        @KafkaListener(topics = "payments", ackMode = "MANUAL_IMMEDIATE")
        public void consume(ConsumerRecord<String, Payment> record, Acknowledgment ack) {
            try {
                paymentService.process(record.value());
                ack.acknowledge(); // commit ONLY after successful processing
            } catch (RecoverableException e) {
                // don't ack — will be redelivered after rebalance
                throw e;
            } catch (PoisonPillException e) {
                sendToDLQ(record);
                ack.acknowledge(); // commit to unblock partition
            }
        }
        ```

---
* [x] **What is consumer lag and how to monitor it?**
    * Consumer lag shows how far behind a consumer group is and is a key metric for Kafka health and performance.

    * **Production War Story — Follow-up (Expert): Your Grafana dashboard showed consumer lag=0 for 3 days, then suddenly spiked to 2 million messages in 10 minutes. The producer throughput hadn’t changed. What caused it and how do you build a lag alerting system that catches this before it becomes a crisis?**
        * **What caused the sudden spike:** A downstream DB the consumer wrote to had a slow index rebuild running (auto-triggered by a deployment). DB write latency went from 2ms to 450ms. Each consumer record now took 450ms instead of 2ms. With `max.poll.records=500` and a processing rate that dropped from 250K/min to 133/min, lag started accumulating silently.
        * **Why the alert didn’t fire:** The team was alerting on `lag > 100,000` with a 5-minute evaluation window. By the time the alert fired, lag was already 1.8M and the DB was under severe write pressure from the backlog.
        * **Better lag alerting strategy:**
        ```yaml
        # Alert 1: Lag growth rate (catches problems early)
        # Fire when lag increases by >10,000/min for 3 consecutive minutes
        alert: KafkaLagGrowthRate
        expr: rate(kafka_consumer_lag_sum[1m]) > 10000
        for: 3m

        # Alert 2: Absolute lag threshold (catches sustained backlog)
        alert: KafkaLagHigh
        expr: kafka_consumer_lag_sum > 50000
        for: 5m

        # Alert 3: Consumer group has zero active members (consumer crashed)
        alert: KafkaConsumerGroupDead
        expr: kafka_consumer_group_members == 0
        for: 1m
        ```
        * **Key metrics to track (Prometheus + JMX exporter):**
            * `kafka_consumer_lag_sum` — total lag across all partitions per group
            * `kafka_consumer_fetch_rate` — if this drops while lag grows, consumer is the bottleneck
            * `kafka_consumer_records_lag_max` — worst single partition lag (uneven partition distribution)
            * `kafka_network_io_wait_time_ns_avg` — broker I/O bottleneck indicator
        * **Operational response:** When lag spikes: (1) check consumer CPU/memory, (2) check downstream DB/service latency, (3) check for poison pill messages blocking a partition, (4) scale consumer group up to match partition count, (5) if persistent: increase partition count + consumer instances.

---
* [x] **What happens when a consumer fails?**
    * On consumer failure, Kafka rebalances the group and reassigns partitions so processing continues automatically.
---
* [x] **Difference between poll() and subscribe()**
    * subscribe() defines what to consume and group membership, while poll() actually fetches data and keeps the consumer alive.
    * **subscribe()**
        * Used to join a consumer group and declare interest in topics.
        * Handles partition assignment and rebalancing automatically.
        * Called once (or on change).

    * **poll()**
        * Used to fetch records from assigned partitions.
        * Also sends heartbeats to keep the consumer alive.
        * Called repeatedly in a loop.

### Performance & Reliability

* [x] **How to achieve exactly-once semantics(EOS) in Kafka?**
    * Kafka achieves exactly-once semantics using idempotent producers, transactions, and atomic offset commits in a read-process-write flow.
    * **How Kafka achieves EOS**
        * **Idempotent Producer:** Prevents duplicates during retries (enable.idempotence=true).
        * **Transactions:** Producer writes to multiple partitions/topics atomically (transactional.id).
        * Read–Process–Write in one transaction
            * Consume records
            * Produce results
            * Commit consumer offsets as part of the same transaction
        * **Atomic commit or abort:** Either all writes + offsets succeed, or nothing is visible.

    * **Production War Story — Follow-up (Expert): You implemented EOS (exactly-once semantics) with `transactional.id` in your payment service. After a deployment, you saw `ProducerFencedException` exceptions flooding logs and no messages being produced. What happened?**
        * **Root cause — transactional.id reuse during rolling deployment:** Kafka associates a `transactional.id` with an epoch. When a new producer instance initializes with the same `transactional.id`, Kafka **increments the epoch** and fences (kills) the old producer with that id. This is by design — it prevents zombie producers from writing stale data.
        * **What happened:** Rolling deployment spun up new pod with `transactional.id=payment-producer-1`. Kafka fenced the old pod’s producer. Old pod was still processing its current batch — its next send threw `ProducerFencedException`. Since the exception wasn’t handled correctly, the consumer’s offset was never committed, causing both pods to attempt reprocessing.
        * **Correct `transactional.id` strategy for Kubernetes:**
        ```java
        // Use a stable, pod-unique ID — NOT a random UUID (defeats the purpose)
        // Good: tied to partition assignment (Kafka Streams does this automatically)
        String txId = "payment-service-" + partition; // stable per partition

        // In Spring Kafka — use unique-per-partition transaction id prefix
        @Bean
        public ProducerFactory<String, Payment> producerFactory() {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "payment-tx-");
            // Spring Kafka appends partition number automatically
            return new DefaultKafkaProducerFactory<>(props);
        }
        ```
        * **Handle `ProducerFencedException` explicitly:** It is NOT retryable. The correct action is to close the producer, log the event, and let the pod restart/reinitialize cleanly. Never retry on `ProducerFencedException`.
        * **EOS performance cost:** Transactions add ~5-10ms latency per commit (coordinator round-trip). For 10K TPS payment systems, benchmark before enabling. Use transactions only for topics where exactly-once is business-critical (payment events, inventory adjustments) — not for analytics/logging topics.

---
* [x] **What is log compaction?**
    * Log compaction keeps the latest record per key, making Kafka suitable for maintaining the latest state.

    * **Production War Story — Follow-up (Expert): You used a log-compacted topic for user profile state. After enabling compaction, a consumer reading from the beginning got completely different data than a consumer reading from the middle. Explain why and what operational guarantee compaction actually provides.**
        * **What happened:** Log compaction runs asynchronously in the background — it does NOT guarantee that at any given moment, only the latest record per key is present in the topic. The "head" (recent segment) is never compacted. The "tail" (older segments) is compacted eventually.
        * **Concrete sequence:**
            * User 42's profile was updated 5 times. Records at offsets 100, 500, 1200, 3400, 7800.
            * Consumer A starts from offset 0: reads all 5 records, applies them in order, ends with the latest state. Correct.
            * Consumer B starts from offset 2000 (after compaction ran on segments 0–1999): only sees offsets 3400 and 7800. Correct — it gets the "latest at compaction time" version from 3400, then the actual latest at 7800.
            * Consumer C starts from offset 4000: only sees offset 7800. Correct.
        * **The guarantee compaction provides:** After compaction completes on a segment, for each key, only the **latest record in that segment range** survives. It does NOT mean only one record per key exists at any moment.
        * **Tombstone records:** Producing a record with `value=null` for a key marks it for deletion. After compaction, the key is removed. But tombstones are retained for `delete.retention.ms` (default 24h) before removal — so consumers reading after the tombstone but before deletion can still see the "deleted" signal.
        * **Compaction pitfall — missing deletes:** If a consumer reads after a key’s tombstone has been cleaned up (past `delete.retention.ms`), it will never see that the key was deleted. For CDC pipelines syncing Kafka to a database, this means deleted records may never be removed from the target DB. Always set `delete.retention.ms` longer than your maximum consumer restart gap.

---
* [x] **How to handle message retries?**
    * message retries are handled at producer side and consumer side, depending on the failure type.
    * **Producer-side retries**
        * Enabled via `retries` and `retry.backoff.ms`.
        * Used for transient broker/network failures.
        * Idempotent producer prevents duplicates during retries.
    * **Consumer-side retries**
        * On processing failure, consumer can:
            * Retry in-memory (limited attempts).
            * Commit offset after success only (manual commit).
            * Send message to a retry topic with delay.
    * **Dead Letter Queue (DLQ)**
        * Messages that fail after max retries are sent to a DLQ for analysis.
        * Prevents blocking the main consumer.

    * **Production War Story — Follow-up (Expert): Your consumer retry logic used `Thread.sleep()` for backoff between retries inside the `@KafkaListener` method. Under failure conditions, this caused cascading rebalances. Explain the mechanism and the correct retry architecture.**
        * **What happened:** While `Thread.sleep(30_000)` was sleeping (30s backoff), the consumer’s `poll()` was not being called. Kafka’s `max.poll.interval.ms=30000` expired. Kafka declared the consumer dead and triggered a rebalance. The partition was reassigned to another consumer, which also failed and slept, triggering another rebalance. A 3-consumer group was rebalancing every 30 seconds — no messages processed, coordinator CPU spiked.
        * **The correct retry architecture — retry topics:**
        ```
        orders (main)
          → on failure → orders.retry.1 (delay: 1s)
          → on failure → orders.retry.2 (delay: 30s)
          → on failure → orders.retry.3 (delay: 5min)
          → on failure → orders.DLQ
        ```
        * Spring Kafka’s `RetryTopicConfiguration` implements this pattern automatically:
        ```java
        @Bean
        public RetryTopicConfiguration retryTopicConfig(KafkaTemplate<String, String> template) {
            return RetryTopicConfigurationBuilder
                .newInstance()
                .exponentialBackoff(1000, 2, 300000) // 1s, 2s, 4s... max 5min
                .maxAttempts(4)
                .retryTopicSuffix(".retry")
                .dltSuffix(".DLQ")
                .create(template);
        }
        ```
        * **Why retry topics work:** The failing message is immediately published to `orders.retry.1` and the original offset is committed. The main consumer continues processing other messages — no blocking, no sleep, no rebalance.
        * **DLQ monitoring is non-negotiable:** Every message landing in DLQ must trigger an alert. DLQ messages represent data silently dropped from your processing pipeline. At a payments company, an unmonitored DLQ = missing transactions.

---
* [x] **Explain back pressure handling in Kafka**
    * Kafka handles backpressure via a pull-based model where consumers control the read rate, resulting in lag instead of system overload.
---
* [x] **How did you tune Kafka for high throughput in your projects?**
    * We achieved high throughput by tuning batching, compression, partitioning, and consumer parallelism across the Kafka pipeline.

![Image](https://camo.githubusercontent.com/c5a23a486c65497af3c60d088e30d1c875fe13ec43513d614e618249473f7780/68747470733a2f2f696d6167652e6175746f6d712e636f6d2f77696b692f626c6f672f6170616368652d6b61666b612d706572666f726d616e63652d74756e696e672d746970732d626573742d7072616374696365732f352e706e67)

![Image](https://cdn.prod.website-files.com/68ed36e99e31581dedf5dcb1/693736b29da3313dcf64762a_66a3d513f2233db33963e5a5_668812d9d3259cde91caa849_guide-kafka-performance-tuning-img3.png)

* **Producer tuning**
    * Enabled **batching & compression** (`batch.size`, `linger.ms`, `compression.type=lz4`)
    * Used **idempotent producer** with retries
    * Increased `buffer.memory`

* **Broker tuning**
    * Increased **partition count** for parallelism
    * Tuned **replication factor** (usually 3)
    * Optimized disk I/O and network threads

* **Consumer tuning**
    * Increased **consumer parallelism** (more consumers per group)
    * Tuned `max.poll.records`
    * Used **manual offset commit** after batch processing

    * **Result (example)**
        * Improved throughput from **50K → 300K msgs/sec** with stable latency.

    * **Production War Story — Follow-up (Expert): After tuning to 300K msgs/sec, you hit a throughput ceiling that didn’t budge no matter how many consumers you added. The bottleneck wasn’t CPU or network. What was it, and how did you diagnose and fix it?**
        * **The ceiling:** Adding more consumers beyond the partition count gave no improvement. Profiling showed 80% of time spent in DB writes, not Kafka poll.
        * **Root cause — partition count was the hard ceiling:** With 12 partitions and 20 consumers, only 12 consumers were active. 8 were idle. Each active consumer was processing at its DB write rate — ~25K msg/sec/consumer → 300K total. No amount of consumers above 12 could help.
        * **Diagnosis steps:**
        ```bash
        # Check active partition assignments per consumer
        kafka-consumer-groups.sh --bootstrap-server broker:9092           --group my-group --describe
        # If CONSUMER-ID shows "---" for some partitions, they're unassigned (no consumer)
        # If multiple consumers share no partitions, they're idle

        # Check consumer thread utilization
        # In Micrometer: kafka_consumer_fetch_rate per consumer instance
        # If some instances have 0 fetch rate, they got 0 partitions
        ```
        * **Fix — increase partition count:** Increased from 12 to 48 partitions. Scaled to 48 consumer instances. Throughput jumped to 1.2M msgs/sec.
        * **Partition count planning rules:**
            * `partitions = target_throughput / throughput_per_consumer`
            * For a topic expected to need 10x growth, over-provision partitions upfront (partitions can only increase, never decrease without data loss risk)
            * Rule of thumb: `partitions = max(target_consumers, target_throughput_MB/s / 10)`
        * **The other hidden bottleneck — Kafka broker disk I/O:** At 300K msgs/sec with 3x replication, each message is written 3 times (leader + 2 followers). If broker disks are SATA HDDs instead of NVMe SSDs, disk I/O becomes the ceiling regardless of partition count. At Zee5 scale, dedicated NVMe brokers are non-negotiable.

### Operations & Monitoring

* [x] **How to monitor Kafka cluster health?**

![Image](https://camo.githubusercontent.com/ce06ea8468525f9cbf2c886a09c8c55e5b6691ec19f391f468d0f6ded6482a41/68747470733a2f2f696d6167652e6175746f6d712e636f6d2f77696b692f626c6f672f6b61666b612d6d6f6e69746f72696e672d746f6f6c732d626573742d7072616374696365732f312e706e67)

![Image](https://imgix.datadoghq.com/img/dashboard/dashboard-header-kafka.png)

![Image](https://cdn.confluent.io/wp-content/uploads/prometheus-based-monitoring-preview-1.png)


* Kafka health is monitored by tracking broker availability, replication status, and consumer lag using Prometheus and Grafana.


---
* [x] **What metrics do you track? (Throughput, latency, consumer lag)**
    * I primarily monitor throughput, latency, and consumer lag, supported by replication and broker health metrics to ensure Kafka stability.
---
* [x] **How to handle rebalancing in consumer groups?**
    * Rebalancing is handled by cooperative assignors, timely offset commits, and rebalance listeners to minimize disruption and reprocessing.

![Image](https://camo.githubusercontent.com/2aaf02be2cd8d5f88aba6d9a61a501c5cbc81e9d79ce61ecab9afbb8666d6656/68747470733a2f2f696d6167652e6175746f6d712e636f6d2f77696b692f626c6f672f6b61666b612d726562616c616e63696e672d636f6e63657074732d626573742d7072616374696365732f312e706e67)

![Image](https://cdn.confluent.io/wp-content/uploads/eager-rebalancing-protocol.jpg)

![Image](https://tomlee.co/img/KafkaRebalance.png)

In **Apache Kafka**, rebalancing occurs when consumers join/leave or partitions change.



* **Use cooperative rebalancing** (`partition.assignment.strategy=cooperative-sticky`)
  → Minimizes stop-the-world rebalances.
* **Commit offsets before rebalance**
  → Prevents message reprocessing.
* **Implement `ConsumerRebalanceListener`**
  → Gracefully stop processing and save state.
* **Tune timeouts** (`session.timeout.ms`, `max.poll.interval.ms`)
  → Avoid unnecessary rebalances.
    * New consumer joins → only a few partitions move instead of all.

    * **Production War Story — Follow-up (Expert): During peak traffic, a consumer group was experiencing a rebalance storm — rebalancing every 2-3 minutes continuously. No consumers were added or removed. What caused it and how did you stop it?**
        * **Root cause — `max.poll.interval.ms` violation from slow processing:** The consumer was using `max.poll.records=500` and processing each record required a DB lookup averaging 8ms. 500 × 8ms = 4 seconds, but occasionally a slow DB query took 200ms, pushing total batch time to `500 × 200ms = 100 seconds`. `max.poll.interval.ms=30000` (30s default) expired — Kafka declared the consumer dead and rebalanced.
        * **The vicious cycle:** On rebalance, other consumers got the reassigned partitions and also hit slow DB queries (the DB was under load) — triggering *their* max.poll.interval violations — causing cascading rebalances across the entire group.
        * **Diagnosis:**
        ```bash
        # Look for frequent rebalance events in consumer logs
        grep "Rebalancing" consumer.log | awk '{print $1,$2}' | uniq -c

        # Check commit rate — if commits happen then stop for 30s, consumer is stuck
        kafka-consumer-groups.sh --describe --group my-group
        # Watch the LAG column: if it grows then suddenly drops by exact batch size, rebalance is happening
        ```
        * **Fixes applied:**
        ```properties
        # Reduce batch size to keep processing under max.poll.interval.ms
        max.poll.records=50          # from 500 to 50 — 50 * 200ms = 10s, safely under 30s

        # Or increase the interval to match realistic worst-case processing time
        max.poll.interval.ms=300000  # 5 minutes for heavy processing

        # Switch to cooperative-sticky to minimize partition movement on legitimate rebalances
        partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor
        ```
        * **Cooperative-sticky assignor is critical at scale:** Eager rebalancing (default) stops ALL consumers in the group (stop-the-world) during rebalance. With 20 consumers and 200 partitions, eager rebalance = 20 consumers idle simultaneously. Cooperative rebalancing only revokes and reassigns the partitions that actually need to move — other consumers keep processing.
        * **Heartbeat vs poll timeout — common confusion:**
            * `heartbeat.interval.ms` / `session.timeout.ms` — for detecting consumer *crashes* (no heartbeat from background thread)
            * `max.poll.interval.ms` — for detecting consumer *slowness* (foreground processing too slow between polls)
            * These are independent. A consumer can send heartbeats normally while its processing loop is too slow — `max.poll.interval.ms` triggers even if heartbeats are healthy.

---
* [x] **How to add/remove brokers from a cluster?**
  ![Image](https://www.michael-noll.com/assets/uploads/kafka-cluster-overview.png)

![Image](https://cdn.prod.website-files.com/68ed36e99e31581dedf5dcb1/690211c9f9eb9f1dc940f5e6_66a3d48228e3e933f5ee5e50_66880f0749f675709dbf3c22_guide-kafka-partition-img4.png)

![Image](https://cdn.prod.website-files.com/6541750d4db1a741ed66738c/65df6ad7407e03459c1f6ec9_Apache_Kafka_data_Decommissioning%20Brokers.webp)

In **Apache Kafka**, brokers can be added or removed **without downtime** using partition reassignment.

* New Broker
1. Start a **new broker** with a unique `broker.id`.
2. Broker registers with the cluster.
3. **Reassign partitions** to the new broker (manual or automated).
4. Data is **rebalanced automatically**.


* Removing a broker
    1. **Trigger partition reassignment** to move data off the broker.
    2. Wait until partitions are fully replicated elsewhere.
    3. **Shutdown the broker safely**.

* Tools
    * Kafka reassignment tools / admin APIs
    * Automated rebalancing in managed Kafka


---
* [x] **What is ISR (In-Sync Replicas)?**
    * ISR is the group of replicas that are fully synchronized with the leader and guarantee safe leader election and durability.
    * **Key points :**
        * Each partition has 1 leader and multiple followers.
        * Replicas that lag beyond a threshold are removed from ISR.
        * Only ISR members are eligible to become leader.
        * acks=all waits for all ISR replicas to acknowledge.

    * **Production War Story — Follow-up (Expert): Your monitoring showed `UnderReplicatedPartitions > 0` for 20 minutes. The team ignored it thinking it was a transient blip. What were the actual downstream risks during those 20 minutes and what should the incident response have been?**
        * **What `UnderReplicatedPartitions > 0` means:** At least one partition has fewer in-sync replicas than `replication.factor`. The cluster is operating with reduced durability.
        * **Risks during those 20 minutes:**
            1. **Reduced durability:** If `min.insync.replicas=2` and ISR=1, every `acks=all` write is only on 1 broker. A broker crash during this window = permanent data loss for those messages.
            2. **No leader failover safety:** If the partition leader crashes, only ISR members can become leader. With ISR=1 (the leader itself), a leader crash = partition becomes unavailable (LeaderNotAvailableException) until the lagging replica catches up.
            3. **Silent producer errors:** With `min.insync.replicas=2` and only 1 ISR, producers using `acks=all` get `NotEnoughReplicasException`. If this wasn't alerting, messages may have been silently dropped depending on producer error handling.
        * **Correct incident response:**
        ```
        T+0:  UnderReplicatedPartitions alert fires
        T+1:  Identify which broker is lagging: kafka-topics.sh --describe | grep "Isr" | grep -v all replicas
        T+2:  Check broker logs for: disk full, GC pressure, network issues, high replication lag
        T+5:  If broker is alive but slow: check replica.lag.time.max.ms — may need temporary increase
        T+10: If broker is down: check if it’s recovering; don’t force unclean election
        T+15: If broker can’t recover: reassign affected partitions to healthy brokers
        T+20: Validate ISR count is back to replication.factor for all partitions before closing incident
        ```
        * **Prevention:** Set `replica.lag.time.max.ms=60000` (generous), alert on `UnderReplicatedPartitions > 0` for **more than 2 minutes** (short blips are normal during broker restarts), and have a runbook that mandates immediate escalation — not "watch and wait".

### Integration

* [x] **How did you integrate Kafka with Spring Boot?**
    * I used Spring Kafka with KafkaTemplate for producers and @KafkaListener for consumers, configured via Spring Boot properties for scalable event-driven communication.

    * **Production War Story — Follow-up (Expert): In your Spring Boot Kafka consumer, you processed a batch of 200 messages inside `@KafkaListener`. Record 147 threw a non-retryable exception. You sent it to DLQ. But now the remaining records 148-200 were never processed. How does Spring Kafka handle partial batch failures and what’s the correct implementation?**
        * **The problem with batch listeners:** When `@KafkaListener` processes a `List<ConsumerRecord>`, throwing any exception after partially processing the batch leaves the uncommitted records in a grey zone. Spring Kafka’s default error handling for batch mode retries the **entire batch** — records 1-146 get processed again (duplicates), and record 147 triggers the exception again (infinite loop without proper retry limits).
        * **Correct implementation with `BatchListenerFailedException`:**
        ```java
        @KafkaListener(topics = "orders", containerFactory = "batchFactory")
        public void consume(List<ConsumerRecord<String, Order>> records) {
            for (int i = 0; i < records.size(); i++) {
                try {
                    orderService.process(records.get(i).value());
                } catch (NonRetryableException e) {
                    // Tell Spring Kafka exactly which record failed
                    // It will commit up to index i-1, send record i to DLQ, then continue from i+1
                    throw new BatchListenerFailedException("Failed at index " + i, e, i);
                }
            }
        }
        ```
        * **`BatchListenerFailedException` is the key:** Spring Kafka’s `DefaultErrorHandler` (with DLT configured) handles this correctly — it commits the offset up to the failed record, sends the failed record to the DLQ, then seeks to the next record. The batch resumes from where it left off.
        * **Batch vs single-record listener tradeoff:**
            * Single-record: simpler error handling, `BatchListenerFailedException` not needed, lower throughput
            * Batch: higher throughput (fewer DB round-trips via batch insert), complex error handling
        * **For payment processing:** Use single-record mode + synchronous DB write + manual ack. Throughput is secondary to correctness.
        * **For analytics pipelines:** Use batch mode + `BatchListenerFailedException` + DLQ. Throughput matters, occasional DLQ messages are acceptable.

---
* [x] **What is Kafka Streams vs Kafka Connect?**

![Image](https://miro.medium.com/v2/resize%3Afit%3A1400/1%2A5DMYoWniIyN7YRoJof4K_w.png)

![Image](https://media.geeksforgeeks.org/wp-content/uploads/20230111142733/kafka_stream_architecturedrawio.png)

![Image](https://images.ctfassets.net/gt6dp23g0g38/1bgj1Q463j7XVML0s10KV6/00a9b051eefefb4926610152efc40db8/image13.png)

In **Apache Kafka**, **Kafka Streams** and **Kafka Connect** serve different purposes.

* **Kafka Streams**
    * **Library** for building **real-time stream processing** apps.
    * Write code (Java) to **transform, aggregate, join** streams.
    * Runs **inside your application**.
    * **Example**: Aggregate orders per minute.

---

* **Kafka Connect**

    * **Framework** for **moving data in/out of Kafka**.
    * Uses **connectors** (source/sink), **no business logic code**.
    * Runs as a **separate service/cluster**.
    * **Example**: Sync MySQL → Kafka → Elasticsearch.
---

* [x] **How to implement dead letter queue in Kafka?**
    * A DLQ in Kafka is a separate topic where messages are sent after retry exhaustion, allowing the main consumer to continue processing.
  ```java
  @KafkaListener(topics = "orders", groupId = "order-group")
  public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
  try {
    process(record.value());   // business logic
    ack.acknowledge();         // commit offset on success
  } catch (Exception ex) {
      sendToDLQ(record, ex);     // after retry exhaustion
      ack.acknowledge();         // commit to avoid reprocessing
    }
  }
  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;
  
  private void sendToDLQ(ConsumerRecord<String, String> record, Exception ex) {
  ProducerRecord<String, String> dlqRecord =
  new ProducerRecord<>("orders.DLQ", record.key(), record.value());
  
      dlqRecord.headers()
          .add("error", ex.getMessage().getBytes())
          .add("source-topic", record.topic().getBytes());
  
      kafkaTemplate.send(dlqRecord);
  }

  spring:
  kafka:
    consumer:
      enable-auto-commit: false      # We commit offsets manually (after success / DLQ)
      auto-offset-reset: earliest
      max-poll-records: 10           # Batch size per poll
    listener:
      ack-mode: MANUAL               # Explicit acknowledgment in code
    producer:
      retries: 3                     # Producer retry on transient failure
      acks: all                      # Strong durability

  ```

    * **Production War Story — Follow-up (Expert): Messages in your DLQ have been accumulating for 3 days. The team wants to replay them back to the main topic after fixing the bug. What are the risks and what is the safe replay procedure?**
        * **Risks of naive DLQ replay:**
            1. **Ordering violation:** DLQ messages are out of order relative to the messages processed after them. Replaying them to the main topic injects old events *after* newer ones have already been processed. For state-changing events (inventory update, balance change), this corrupts state.
            2. **Thundering herd:** Replaying 3 days of DLQ messages into the main topic alongside live traffic spikes consumer load. The system that just recovered may get overwhelmed again.
            3. **Duplicate processing:** If the original message was partially processed before failing (e.g., payment initiated but not confirmed), replaying it may double-charge a customer.
        * **Safe replay procedure:**
        ```bash
        # Step 1: Verify the fix is deployed and tested
        # Step 2: Analyze DLQ messages — are they all the same failure type?
        kafka-console-consumer.sh --topic orders.DLQ --from-beginning --max-messages 100

        # Step 3: Replay during low-traffic window (not peak hours)
        # Step 4: Use a separate consumer group for replay — don't touch the main group’s offsets
        kafka-consumer-groups.sh --bootstrap-server broker:9092           --group dlq-replay-$(date +%Y%m%d) --reset-offsets           --topic orders.DLQ --to-earliest --execute

        # Step 5: Replay with rate limiting (don’t flood main topic)
        # Use a dedicated replay service with throttling: Thread.sleep between produces
        ```
        * **Idempotency is the safety net:** If your consumer is idempotent (checks a dedup key before processing), replay is safe even with duplicates. Design for replay from day one.
        * **Replay to a separate topic first:** Don’t replay directly to `orders` (main). Replay to `orders.replay`, have a shadow consumer verify processing results, then move to main only if results are correct.
        * **Operational lesson:** DLQ messages must have rich headers: original topic, original partition, original offset, failure timestamp, exception type, stack trace. Without this, debugging and safe replay is nearly impossible.