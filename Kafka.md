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
---
* [x] **Explain idempotent producer**
    * Idempotent producer prevents duplicate messages during retries by using producer IDs and sequence numbers.
---
* [x] **What is producer batching and compression?**
    * Batching reduces network calls by sending messages in bulk, and compression reduces payload size to improve Kafka throughput.
    * Controlled by `batch.size` and `linger.ms`
    * Messages for the same partition are batched together
    * Producer compresses message batches before sending : gzip, snappy, lz4, zstd


---
* [x] **How to ensure message ordering in Kafka?**
    * Kafka guarantees ordering only per partition, so use the same key to route related messages to the same partition.
    * **What is NOT guaranteed**
        * Ordering across partitions ❌
        * Ordering across different keys ❌

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
---
* [x] **What is consumer lag and how to monitor it?**
    * Consumer lag shows how far behind a consumer group is and is a key metric for Kafka health and performance.
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
---
* [x] **What is log compaction?**
    * Log compaction keeps the latest record per key, making Kafka suitable for maintaining the latest state.
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

### Integration

* [x] **How did you integrate Kafka with Spring Boot?**
    * I used Spring Kafka with KafkaTemplate for producers and @KafkaListener for consumers, configured via Spring Boot properties for scalable event-driven communication.
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