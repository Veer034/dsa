## REDIS
### Core Concepts

* [x] **What is Redis and why is it used?**
    * Redis is an in-memory, key–value data store used as a cache, database, and message broker. It stores data in RAM, making read/write operations extremely fast (microseconds).
    * **Why Redis is used:**
        * **High performance** – In-memory storage gives very low latency.
        * **Caching** – Reduces database load and improves response time.
        * **Rich data structures** – Supports strings, hashes, lists, sets, sorted sets.
        * **Scalability** – Supports replication and clustering.
        * **Use cases** – Caching, session storage, rate limiting, leaderboards, pub/sub.
---
* [x] **Explain Redis data structures (String, List, Set, Sorted Set, Hash, Bitmap, HyperLogLog, Streams)**
    * Redis provides specialized data structures optimized for speed, scalability, and specific access patterns, making it ideal for caching, messaging, analytics, and real-time systems.

![Image](https://media.licdn.com/dms/image/v2/D4E12AQEUTqxcuPgyoQ/article-cover_image-shrink_600_2000/article-cover_image-shrink_600_2000/0/1674494655446?e=2147483647\&t=SZHK8-G4v_Dk4alpCngtq1eJVVwdPIYSaigywXMd0d4\&v=beta)
Below is the **same `.md` format**, but now each example explains **what actually happens inside Redis**, not just the command.

---

* **1. String**

    * Simplest key–value (stored as raw bytes, optimized internally).
    * **Use**: cache, counters, tokens.
    * **What happens internally**:

        * Redis stores `page_views` as a single value.
        * `INCR` is **atomic** → no race condition even with many clients.
    * **Example**:

      ```
      SET page_views 100      # Redis stores: key=page_views, value=100
      INCR page_views         # Redis reads 100, increments to 101, writes back atomically
      ```

---

* **2. List**

    * Ordered collection (implemented as **quicklist**: linked list of ziplists).
    * **Use**: queues, task pipelines.
    * **What happens internally**:

        * `LPUSH` adds element to head.
        * `RPOP` removes from tail → FIFO queue.
        * O(1) push/pop.
    * **Example**:

      ```
      LPUSH jobs job1         # jobs -> [job1]
      LPUSH jobs job2         # jobs -> [job2, job1]
      RPOP jobs               # returns job1, jobs -> [job2]
      ```

---

* **3. Set**

    * Unordered collection, unique elements (hash table internally).
    * **Use**: unique users, tags.
    * **What happens internally**:

        * Redis hashes each element.
        * Duplicate inserts are ignored.
        * Membership check is O(1).
    * **Example**:

      ```
      SADD online_users u1 u2 u2   # stored: {u1, u2}
      SISMEMBER online_users u1    # hash lookup → true
      ```

---

* **4. Sorted Set (ZSet)**

    * Set + score (skiplist + hash table).
    * **Use**: leaderboards, ranking.
    * **What happens internally**:

        * Hash → fast lookup by member.
        * Skiplist → ordered traversal by score.
    * **Example**:

      ```
      ZADD leaderboard 100 user1
      ZADD leaderboard 200 user2
      ZRANGE leaderboard 0 1 WITHSCORES
      # Redis walks skiplist from lowest score
      ```

---

* **5. Hash**

    * Key → field → value (like a row in DB).
    * **Use**: objects (user profile).
    * **What happens internally**:

        * Small hashes stored as compact structure.
        * Large hashes converted to hash table.
        * Avoids many small keys.
    * **Example**:

      ```
      HSET user:1 name "Alex" age 30
      HGET user:1 name
      # Redis fetches field "name" from hash user:1
      ```

---

* **6. Bitmap**

    * Bit-level operations on strings.
    * **Use**: flags, daily active users.
    * **What happens internally**:

        * Redis treats string as bit array.
        * Bit offset = userId (or index).
        * Extremely memory efficient.
    * **Example**:

      ```
      SETBIT login:2026-01-07 123 1
      GETBIT login:2026-01-07 123
      # Redis flips and reads a single bit in memory
      ```

---

* **7. HyperLogLog**

    * Probabilistic structure (~12KB fixed memory).
    * **Use**: approximate unique counts.
    * **What happens internally**:

        * Hashes input values.
        * Tracks max leading-zero patterns.
        * Returns approximate cardinality.
    * **Example**:

      ```
      PFADD visitors u1 u2 u3 u2
      PFCOUNT visitors
      # Redis estimates unique count, not exact
      ```

---

* **8. Streams**

    * Append-only log with consumer groups.
    * **Use**: event streaming, async processing.
    * **What happens internally**:

        * Each entry gets a monotonic ID.
        * Consumer group tracks offsets.
        * Messages stay until trimmed.
    * **Example**:

      ```
      XADD orders * orderId 101 status created
      XREAD STREAMS orders 0
      # Redis reads entries starting from ID 0
      ```

---

### Interview-ready one-liner

> “Redis data structures are not just types—they are specialized in-memory algorithms optimized for specific access patterns.”

---
* [x] **What is the difference between Redis and Memcached?**
    * | Aspect       | **Redis**                                     | **Memcached**        |
                | ------------ | --------------------------------------------- | -------------------- |
      | Data model   | Rich (String, List, Set, ZSet, Hash, Streams) | Simple key–value     |
      | Persistence  | Yes (RDB, AOF)                                | No                   |
      | Use cases    | Cache, DB, queues, streaming                  | Cache only           |
      | Replication  | Yes                                           | No                   |
      | Atomic ops   | Yes                                           | Limited              |
      | Memory usage | Slightly higher                               | Very efficient       |
      | Scalability  | Clustering + replication                      | Client-side sharding |
---
* [x] **Is Redis single-threaded or multi-threaded?**
    * Redis is hybrid. Command execution is single-threaded, but Redis 6.0+ uses additional threads for network I/O and background tasks like persistence.
    * **Why single-threaded for commands?**
        * Operations are extremely fast (microseconds) because data is in-memory
        * Multi-threading would add locking overhead that's slower than the operations themselves
        * No context switching or synchronization needed
    * **Why is it fast then?**
        * All data in RAM - no disk I/O waits
        * Simple, optimized C code
        * No locking overhead
        * Event loop handles I/O efficiently without blocking
    * **Can't multiple threads use multiple cores better?**
        * For Redis, no. When operations take microseconds, thread synchronization overhead makes it slower.
        * **To use multiple cores:**
            * Run multiple Redis instances (one per core)
            * Use Redis Cluster (auto-sharding across nodes)

### Data Structures & Commands
* [x] **When to use List vs Set vs Sorted Set?**
    * **List:** Use when order matters and duplicates are allowed. Example: activity feed, message queue, recent items list.
    * **Set:** Use when you need unique items and fast membership checks (O(1)). Example: unique visitors, tags, IP blacklist.
    * **Sorted Set:** Use when you need unique items ranked by score. Example: leaderboards, priority queues, time-series data with timestamps.

* [x] **How to implement rate limiting using Redis?**
    * Simple approach: Use a String key with expiry and INCR command.
  ```
    # Allow 100 requests per minute per user
    key = "rate:limit:user123"
    INCR key
    EXPIRE key 60
    GET key  # if > 100, reject request
    ```
    * Better approach (sliding window): Use Sorted Set with timestamps as scores, remove old entries, count remaining.
  ```
    ZADD rate:user123 <timestamp> <request_id>
    ZREMRANGEBYSCORE rate:user123 0 <60_seconds_ago>
    ZCARD rate:user123  # if > 100, reject
    ```
---
* [x] **Explain INCR, INCRBY, and their atomic nature**
    * **INCR:** Increments a key's value by 1. If key doesn't exist, sets it to 0 then increments to 1.
    * **INCRBY:** Increments by a specified amount. INCRBY counter 5 adds 5 to the counter.
    * **Atomic nature:** Both operations are atomic - they read, increment, and write in a single step with no
      possibility of race conditions. Even with 1000 concurrent clients, each increment is guaranteed to execute completely before the next one, ensuring accurate counts without locks.
---
* [x] **What are Pub/Sub in Redis?**
    * Pub/Sub is Redis's messaging system where publishers send messages to channels and subscribers receive them in real-time.
    * **Key points:**
        * Messages are fire-and-forget - if no subscriber is listening, message is lost
        * Subscribers receive messages only while connected
        * No persistence or message queue
  ```
  # Subscriber
  SUBSCRIBE chat:room1

  # Publisher
  PUBLISH chat:room1 "Hello everyone"
  ```
    * **Use cases:** Real-time notifications, chat applications, live dashboards. For reliable messaging, use Streams instead.

### Persistence

* [x] **What are RDB and AOF persistence?**
    * **RDB (Redis Database):** Point-in-time snapshots saved to disk at intervals (e.g., every 5 minutes). Fast to load, compact file size, but can lose data between snapshots.
    * **AOF (Append-Only File):** Logs every write command to disk. More durable (can lose only 1 second of data), but larger files and slower restart.
    * **Best practice:** Use both - RDB for fast restarts, AOF for durability. Redis can rebuild from AOF if crash occurs between RDB snapshots.
  ```
    # config
    save 900 1          # RDB: save after 900 sec if 1 key changed
    appendonly yes      # AOF: enable
    appendfsync everysec # AOF: sync every second
    ```

---
* [x] **How persistence doesn't slow down Redis?**
    * **RDB snapshots:**
      ```
      Main thread: [serving requests at full speed]
      Background thread: [fork process → write snapshot to disk]
      ```
        * Uses fork() to create child process
        * Child writes snapshot while parent continues serving requests
        * No blocking of main operations
    * **AOF writes:**
      ```
      Main thread: [execute command] → [append to AOF buffer in memory]
      Background thread: [flush buffer to disk every 1 second]
      ```
        * Commands written to memory buffer first (fast)
        * Disk writes happen asynchronously
        * Main thread doesn't wait for disk
    * **The tradeoff:**
        * Speed: All reads/writes happen in RAM (fast)
        * Durability: Background threads persist to disk (slow, but doesn't block)
        * Risk: Can lose 1 second of data if crash happens before disk sync
    * **Redis prioritizes speed over durability.** If you need guaranteed durability, use a traditional database. Redis is for speed with "good enough" persistence.
---
* [x] **Which persistence mechanism did you use and why?**
    * I would use RDB + AOF as default for important data like sessions. For pure caching, RDB only or even no persistence since cache can be rebuilt.
---
* [x] **What happens during Redis restart?**
    * **Single Instance Redis:**
      With persistence enabled:
        * Redis loads data from disk (AOF or RDB)
        * AOF: Replays all commands (slower but complete)
        * RDB: Loads snapshot (faster but may lose recent data)
        * Starts accepting connections after loading completes
        * Without persistence: Starts with empty dataset, all data lost.

    * **Redis Cluster (3 masters + 3 replicas):**
      When one master restarts:
        * Master goes down → replica promotes to master automatically
        * Cluster continues serving requests (no downtime)
        * Restarted node loads data from disk
        * Rejoins as replica, syncs from current master

      When all nodes restart simultaneously:
    * Each node loads its data from disk independently
    * Cluster reforms, nodes discover each other
    * Masters serve their hash slots, replicas sync from masters
    * Brief unavailability until quorum is reached

    * **Key point:** Cluster provides high availability - individual node restarts don't cause downtime because replicas take
      over.

### Cluster & High Availability

* [x] **Explain Redis Cluster architecture**
    * Redis Cluster is Redis's distributed implementation that provides automatic data sharding, high availability, and horizontal scalability across multiple Redis nodes.
    * **Sharding Model**
        * Redis Cluster uses hash slot partitioning to distribute data. There are 16,384 hash slots (0-16383), and each key is mapped to a slot using CRC16(key) mod 16384. Each master node owns a subset of these slots. For example, in a 3-master setup:
        - Node A: slots 0-5460
        - Node B: slots 5461-10922
        - Node C: slots 10923-16383

    * **Cluster Topology**
        * A minimal production cluster has 6 nodes: 3 masters and 3 replicas. Masters handle read/write operations for their slots, while replicas provide redundancy and can serve reads if configured. Nodes communicate using a gossip protocol on a separate bus port (cluster port = client port + 10000).

    * **Data Distribution**
        * When a client requests a key, the cluster calculates which slot owns it. If the current node doesn't own that slot, it returns a MOVED redirection to the correct node. Clients should cache this slot-to-node mapping to minimize redirections.

    * **High Availability**
        * If a master fails, the cluster automatically promotes one of its replicas to master through a voting process. A majority of master nodes must agree on the promotion. If a master has no replicas and fails, the slots it owned become unavailable unless cluster-require-full-coverage is disabled.

    * **Limitations**
        * Multi-key operations only work if all keys hash to the same slot (use hash tags like {user123}:profile and {user123}:settings). No support for SELECT command - only database 0 is available. Resharding requires moving slots between nodes, which can be done online but requires careful coordination.


---
* [x] **What is Redis Sentinel?**
    * Redis Sentinel is Redis's high availability solution for non-clustered (standalone or master-replica) Redis deployments. It provides monitoring, automatic failover, and service discovery.
    * **Core Responsibilities:**
        * **Monitoring**: Sentinel continuously checks if master and replica instances are working correctly. Multiple Sentinel processes monitor the same Redis instances, providing redundancy in the monitoring system itself.
        * **Automatic Failover:** When a master fails, Sentinel automatically promotes one of its replicas to master.
          It reconfigures other replicas to use the new master and notifies clients of the topology change. This happens without manual intervention.
        * **Configuration Provider:** Clients connect to Sentinel to discover the current master address. When
          failover occurs, clients query Sentinel to get the new master's location, making the system resilient to master changes
        * **Notification:**: Sentinel can notify system administrators or other programs via API about important events
          like failovers, instance failures, or recoveries.
    * **How It Works**
        * **Quorum and Agreement** You typically run at least 3 Sentinel instances (odd number recommended). When a Sentinel
          detects a master is down (subjective down or SDOWN), it asks other Sentinels. If enough Sentinels agree (reaches quorum), the master is marked objectively down (ODOWN), triggering failover.
        * **Failover Process**
            1. Sentinels vote to elect a leader Sentinel to perform failover
            2. Leader selects the best replica (based on replication offset, priority, and replication lag)
            3. Promotes the replica to master using REPLICAOF NO ONE
            4. Reconfigures other replicas to follow the new master
            5. Updates clients about the new topology

  **Example Configuration**
  ```
  sentinel monitor mymaster 127.0.0.1 6379 2
  sentinel down-after-milliseconds mymaster 5000
  sentinel parallel-syncs mymaster 1
  sentinel failover-timeout mymaster 10000
  ```
    * The quorum of 2 means at least 2 Sentinels must agree the master is down before failover.


---
* [x] **Difference between Redis Cluster and Redis Sentinel**
    * **One-Liner Difference**
        * **Sentinel** = High availability for single master.
        * **Cluster** = Horizontal scaling via sharding + HA.

    * **Primary Purpose**
        * **Sentinel**: Monitors one master-replica setup, auto-failover when master dies
        * **Cluster**: Splits data across multiple masters for scalability

    * **Data Distribution**
        - **Sentinel**: All data on ONE master (no sharding)
        - **Cluster**: Data sharded across MULTIPLE masters (16,384 hash slots)

    * **Scalability**
        - **Sentinel**: Vertical only (bigger server for more data)
        - **Cluster**: Horizontal (add masters to scale)

    * **Minimum Setup**
        - **Sentinel**: 1 master + 1+ replicas + 3 Sentinels
        - **Cluster**: 3 masters (6 nodes with replicas recommended)

    * **Failover**
        - **Sentinel**: External Sentinels vote and promote replica
        - **Cluster**: Built-in, nodes handle it themselves

    * Multi-Key Operations
        - **Sentinel**: ✅ Full support (MGET, transactions, Lua scripts)
        - **Cluster**: ⚠️ Only if keys in same slot (use hash tags: `{user}:name`)

    * **Client Complexity**
        - **Sentinel**: Simple, ask Sentinel for master address
        - **Cluster**: Complex, handle MOVED redirections, cache slot mapping

    * **When to Use**
        * **Sentinel**: Dataset fits one server, need simple HA
        * **Cluster**: Dataset too large for one server, need write scaling


---
* [x] **How does sharding work in Redis Cluster?**
    * Redis Cluster divides the keyspace into 16,384 hash slots numbered 0-16383. Each key is mapped to a slot using CRC16(key) mod 16384. These slots are distributed among master nodes—for example, with 3 masters: Node A gets slots 0-5460, Node B gets 5461-10922, and Node C gets 10923-16383. When a client requests a key, the cluster calculates its slot and redirects to the node owning that slot if needed.


---
* [x] **What is hash slot in Redis Cluster?**
    * A hash slot is a logical partition used to distribute keys across cluster nodes. Redis Cluster divides the keyspace into 16,384 hash slots (0-16383). Each key is assigned to a slot using the formula CRC16(key) mod 16384. These slots are then distributed among master nodes—for example, Node A owns slots 0-5460, Node B owns 5461-10922, and Node C owns 10923-16383. When a client requests a key, Redis calculates its slot number to determine which node should handle the request, enabling automatic data sharding and horizontal scaling.


---
* [x] **How does failover work in Redis?**
    * Failover works differently depending on whether you're using **Sentinel** or **Cluster**.

    * **Redis Sentinel Failover**
        * **Detection (SDOWN → ODOWN)**:
            - Sentinels ping the master periodically
            - If a Sentinel can't reach master for `down-after-milliseconds`, it marks it as **SDOWN** (Subjectively Down)
            - Sentinel asks other Sentinels if they agree
            - If **quorum** is reached (e.g., 2 out of 3 Sentinels agree), master is marked **ODOWN** (Objectively Down)

        * **Leader Election**:
            - Sentinels vote to elect a **leader Sentinel** to handle failover
            - Requires majority vote (why you need odd number of Sentinels)

        * **Promotion Process**:
            - Leader selects best replica based on: replication priority, replication offset (most up-to-date), and lowest run ID
            - Sends `REPLICAOF NO ONE` to promote chosen replica to master
            - Reconfigures other replicas to follow new master
            - Updates Sentinel configuration and notifies clients

        * **Timing**: Typically completes in seconds (5-30s depending on configuration)

    * **Redis Cluster Failover**
        * **Detection**:
            - Cluster nodes send PING messages via gossip protocol
            - If a master doesn't respond for `cluster-node-timeout`, it's marked as **PFAIL** (Possible Failure)
            - If majority of masters mark it PFAIL, it becomes **FAIL**

        * **Automatic Promotion**:
            - Replicas of the failed master notice the failure
            - Replica with best replication offset requests votes from other masters
            - If **majority of masters vote yes**, replica promotes itself to master
            - New master claims the hash slots of failed master
            - Cluster configuration propagates via gossip

        * **No Leader Election**: Unlike Sentinel, there's no separate leader—the replica promotes itself after getting
          votes

    * **Key Differences**

        - **Sentinel**: External monitors, elected leader performs failover
        - **Cluster**: Self-healing, replicas promote themselves with peer voting
        - **Sentinel**: Centralized decision by leader
        - **Cluster**: Distributed consensus among nodes


---
* [x] **Why choose Redis is production?**
    * We evaluated Redis, Memcached, and [X]. Redis won because we needed persistence for session data, sorted sets for leaderboards, and pub/sub for cache invalidation. The operational maturity, community support, and our team's existing expertise made it a safe choice. We use Redis Cluster in production with RDB+AOF persistence, handling 100K+ ops/sec with sub-millisecond latency.
    * **Why Not Others:**
        * **Not Memcached:** No persistence, limited data types, no built-in HA
        * **Not Hazelcast/Ignite:** Heavier, JVM-based, higher memory overhead for our use case
        * **Not Aerospike:** More complex setup, overkill for our scale

### Caching Strategies

* [x] **Explain cache-aside, write-through, write-behind patterns**

    * **Cache-Aside (Lazy Loading)**
        - Application checks cache first
        - **Cache hit**: Return data from cache
        - **Cache miss**: Read from database → store in cache → return data
        - Writes go directly to database, then invalidate/update cache

      **Flow**:
      ```
      Read: App → Cache (miss) → DB → Cache (set) → App
      Write: App → DB → Cache (delete/update)
      ```

      **Pros**: Only requested data is cached (memory efficient), cache failures don't break app (just slower)

      **Cons**: First request always slow (cache miss), potential stale data if cache not invalidated properly

      **Use Case**: Most common pattern—user profile caching, product catalogs



* **Write-Through**
    - Writes go to cache first, then **synchronously** to database
    - Cache and database updated together in same operation
    - Reads always from cache (cache always has latest data)

  **Flow**:
  ```
  Write: App → Cache (update) → DB (update) → App
  Read: App → Cache → App
  ```

  **Pros**: Cache always consistent with DB, no stale data, good for read-heavy workloads

  **Cons**: Higher write latency (waits for both cache + DB), writes data that might never be read (wastes cache
  space)

  **Use Case**: Financial transactions, inventory management where consistency is critical

* **Write-Behind (Write-Back)**
    - Writes go to cache first, acknowledged immediately
    - Cache **asynchronously** writes to database later (batched or delayed)
    - Reads from cache (fast)

  **Flow**:
  ```
  Write: App → Cache (update, immediate ack) → [later] → DB (batched update)
  Read: App → Cache → App
  ```

  **Pros**: Ultra-fast writes, can batch DB writes (reduces load), high throughput

  **Cons**: Risk of data loss if cache crashes before DB write, complex to implement, eventual consistency

  **Use Case**: High-write workloads like logging, analytics, gaming leaderboards (where some data loss acceptable)


* **Quick Comparison Table**

| Pattern | Write Speed | Read Speed | Consistency | Complexity |
|---------|-------------|------------|-------------|------------|
| Cache-Aside | Fast (DB only) | Medium (miss penalty) | Eventually consistent | Low |
| Write-Through | Slow (cache + DB sync) | Fast | Strongly consistent | Medium |
| Write-Behind | Very Fast (cache only) | Fast | Eventually consistent | High |


---
* [x] **What is cache invalidation strategy?**
    * Cache invalidation determines when and how to remove or update stale data from cache to ensure consistency with the database.
    * Common strategies include:
        * TTL (Time-To-Live) where cache entries expire after a set time,
        * Event-based where cache is invalidated on data updates (delete key after DB write),
        * LRU/LFU eviction where least recently/frequently used items are removed when cache is full, and
        * Write-through/behind where cache updates happen alongside database writes.


---
* [x] **How to handle cache stampede problem?**
    * When a popular cache key expires, multiple requests simultaneously hit the database to regenerate it, causing a thundering herd that can overwhelm the DB.
        * **Locking (Mutex/Semaphore)**
            * First request acquires lock, regenerates cache
            * Other requests wait for lock, then read from cache
        * **Probabilistic Early Expiration**
            * Refresh cache before actual expiry with some probability
            * Formula: current_time - (TTL * beta * log(rand(0,1))) >= expiry_time
            * Popular keys get refreshed more often (more requests = higher probability)
        * **Stale-While-Revalidate**
            * Serve stale data immediately to all requests
            * One background thread refreshes cache asynchronously
            * Mark cache with soft TTL (serve) and hard TTL (refresh trigger)


---
* [x] **What is cache warming?**
    * Cache warming (or cache preloading) is the process of proactively loading data into cache before user requests, rather than waiting for cache misses to populate it. You "warm up" the cache during application startup or during off-peak hours.


---
* [x] **Explain TTL and expiration policies**
    * TTL defines how long cached data remains valid before automatic expiration. Redis uses a hybrid expiration approach—passively deleting on access and actively scanning keys periodically. For eviction when memory is full, I typically use allkeys-lru which removes least recently used keys, suitable for general caching. We set TTLs based on data volatility: user profiles get 1 hour, product listings 5 minutes, and configs 1 day. We monitor cache hit rates to tune TTL values—too short causes DB load, too long serves stale data

### Performance & Best Practices

* [x] **How to handle memory limits in Redis?**
    * To handle memory limits in Redis, I configure maxmemory with an appropriate eviction policy like allkeys-lru for general caching. I set TTLs on all cached data to ensure automatic expiration—sessions get 30 minutes, product data gets 5 minutes. I monitor memory usage with INFO memory and alert at 80% capacity. For memory efficiency, I use hashes for small objects and compress serialized data. When data exceeds single-instance capacity, I scale horizontally using Redis Cluster to shard across nodes. I also separate critical data (noeviction policy) from cache data (LRU eviction) into different Redis instances.

---
* [x] **What are eviction policies in Redis?**
    * **noeviction** (Default)
        - Returns error when memory limit reached
        - No keys are evicted
        - New writes fail until memory freed
        - **Use**: When data loss is unacceptable

    * **allkeys-lru**
        - Evicts **Least Recently Used** keys from all keys
        - Removes keys not accessed recently
        - **Use**: General purpose caching (most common)

    * **allkeys-lfu**
        - Evicts **Least Frequently Used** keys from all keys
        - Removes keys accessed least often
        - **Use**: When popularity matters more than recency

    * **allkeys-random**
        - Randomly evicts keys from all keys
        - No intelligence, pure random selection
        - **Use**: When all keys have equal importance

    * **volatile-lru**
        - Evicts LRU keys **only from keys with TTL set**
        - Keys without expiry are never evicted
        - **Use**: Mix of permanent and temporary data

    * **volatile-lfu**
        - Evicts LFU keys **only from keys with TTL set**
        - Frequency-based eviction for expiring keys only
        - **Use**: Similar to volatile-lru but popularity-based

    * **volatile-ttl**
        - Evicts keys with **shortest remaining TTL first**
        - Prioritizes removing soon-to-expire keys
        - **Use**: When expiry time indicates importance

    * **volatile-random**
        - Randomly evicts keys **only from keys with TTL**
        - Random selection among expiring keys
        - **Use**: Simple eviction for temporary data

* **Quick Selection Guide**

  | Scenario | Policy |
        |----------|--------|
  | General caching | **allkeys-lru** |
  | Hot data stays longer | **allkeys-lfu** |
  | Mix of cache + persistent | **volatile-lru** |
  | Expire sooner = less important | **volatile-ttl** |
  | Can't lose any data | **noeviction** |



* **Configuration**

  ```redis
  CONFIG SET maxmemory 2gb
  CONFIG SET maxmemory-policy allkeys-lru
  ```

  Or in `redis.conf`:
  ```
  maxmemory 2gb
  maxmemory-policy allkeys-lru
  ```


---
* [x] **How to monitor Redis performance?**
    * Use INFO command to check key metrics: INFO stats for ops/sec and hit rate, INFO memory for memory usage and fragmentation ratio. Calculate cache hit rate = keyspace_hits / (keyspace_hits + keyspace_misses) (should be >80%). Use SLOWLOG GET to identify slow commands and redis-cli --stat for real-time monitoring. For production, integrate with Prometheus + Grafana or use RedisInsight GUI for comprehensive dashboards tracking latency, throughput, memory, connections, and evictions.
---
* [x] **What is pipelining in Redis?**
    * Pipelining allows sending multiple commands to Redis in one batch without waiting for individual responses. The client sends all commands at once, then reads all responses together, reducing network round-trip time (RTT).
  ```
    Jedis jedis = new Jedis("localhost");
    Pipeline pipeline = jedis.pipelined();
  
    pipeline.set("user:1", "Alice");
    pipeline.set("user:2", "Bob");
    pipeline.set("user:3", "Charlie");
    pipeline.set("user:4", "David");
   
    List<Object> results = pipeline.syncAndReturnAll();  // Single RTT
    ```
---
* [x] **How to handle large keys in Redis?**
    * For long key names, I use abbreviated but meaningful names like u:p:123 instead of user:profile:123 to reduce memory overhead. I follow a consistent naming pattern like type:id:attribute for organization. For keys pointing to large values, I either chunk the data into multiple smaller keys or use Redis hashes to group related fields under one key, which is more memory efficient than separate keys.

---

## Advanced Topics for 11+ Years Experienced Engineers

### Architecture & Design Decisions

* [ ] **How would you design a distributed rate limiter using Redis that works across multiple application servers with guaranteed accuracy under high concurrency?**
    * This is a classic production problem when you run 10+ app server instances behind a load balancer. A naive per-server counter fails because each server only sees its own traffic.
    * **Approach 1: Token Bucket with Lua Script (Recommended)**
        * Use a Lua script to make the check-and-decrement atomic — no race conditions across servers.
        ```lua
        -- token_bucket.lua
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])  -- tokens per second
        local now = tonumber(ARGV[3])
        local requested = tonumber(ARGV[4])

        local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
        local tokens = tonumber(bucket[1]) or capacity
        local last_refill = tonumber(bucket[2]) or now

        -- Refill tokens based on elapsed time
        local elapsed = now - last_refill
        tokens = math.min(capacity, tokens + elapsed * refill_rate)

        if tokens >= requested then
            tokens = tokens - requested
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
            redis.call('EXPIRE', key, 60)
            return 1  -- allowed
        else
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
            return 0  -- rejected
        end
        ```
        * **Why Lua?** Redis executes Lua scripts atomically — no other command runs between lines.
    * **Approach 2: Sliding Window with Sorted Set**
        ```
        MULTI
        ZADD rate:user123 <timestamp_ms> <uuid>
        ZREMRANGEBYSCORE rate:user123 0 <timestamp_ms - window_ms>
        ZCARD rate:user123
        EXPIRE rate:user123 <window_seconds + 1>
        EXEC
        ```
        * **Problem:** MULTI/EXEC (optimistic locking) doesn't prevent multiple clients from passing the ZCARD check before any of them writes — need WATCH or Lua.
    * **Production Consideration at Scale:**
        * For 1M+ RPS, a single Redis key becomes a hotspot. Partition rate limit keys across multiple Redis nodes using consistent hashing on user_id.
        * Use Redis Cluster with hash tags `{user123}:rate` to ensure all operations for a user land on the same node.
        * Implement a local in-memory counter with a short TTL (100ms) to absorb burst without hitting Redis on every request — sync with Redis periodically.

---

* [ ] **Explain how you'd implement a distributed lock using Redis (Redlock algorithm) and its trade-offs**
    * Single-node Redis locks use `SET key value NX PX ttl` but fail if Redis restarts or in master-replica failover scenarios (replica may not have the lock key yet).
    * **Single-Node Lock (Simple case):**
        ```
        SET lock:resource <unique_token> NX PX 30000
        -- NX = only set if not exists
        -- PX 30000 = expire in 30 seconds
        ```
        * **Release (always use Lua for atomicity):**
        ```lua
        if redis.call("GET", KEYS[1]) == ARGV[1] then
            return redis.call("DEL", KEYS[1])
        else
            return 0
        end
        ```
        * **Why unique token?** Prevents a slow process from deleting another process's lock after its TTL expired.
    * **Redlock Algorithm (Multi-node):**
        * Acquire lock on N independent Redis nodes (typically 5). Succeed if you get locks from majority (N/2 + 1 = 3) within elapsed time less than lock TTL.
        * **Steps:**
            1. Record start time
            2. Try `SET lock:res <token> NX PX <ttl>` on all 5 nodes sequentially
            3. If 3+ succeed AND total elapsed < TTL: lock is acquired with effective TTL = original_TTL - elapsed
            4. If fewer than 3: release all acquired locks immediately
        * **Release:** Send DEL to all 5 nodes regardless.
    * **Known Controversies (Martin Kleppmann critique):**
        * If a process pauses (GC, OS scheduling) after acquiring the lock and before completing work, the lock may expire — another process acquires it — now two processes hold the lock simultaneously.
        * **Defense:** Use fencing tokens (monotonically increasing counter). Storage systems that receive requests check the fencing token and reject stale ones.
    * **Production Recommendation:**
        * For non-critical ops: single-node lock is fine.
        * For financial or inventory operations: use Redlock + fencing tokens + idempotency keys at the database level.
        * Consider ZooKeeper or etcd for use-cases requiring stronger guarantees (they use consensus protocols like ZAB/Raft).

---

* [ ] **How would you design a real-time leaderboard system using Redis for 50 million users with sub-10ms read latency?**
    * **Core Data Structure:** Sorted Set (ZSet) — O(log N) insert/update, O(log N + K) range query.
    ```
    ZADD leaderboard:global <score> <user_id>
    ZREVRANK leaderboard:global <user_id>    -- user's rank (0-indexed)
    ZREVRANGE leaderboard:global 0 99 WITHSCORES  -- top 100
    ```
    * **Problem at 50M users scale:**
        * Single sorted set with 50M members is fine for Redis (ZSets handle millions easily), but:
        * Range queries like "show my rank among friends" require a separate per-user friend ZSet.
        * Global rank computation is O(log N) — fast.
        * Daily/weekly reset requires full key deletion and rebuilding.
    * **Production Architecture:**
        ```
        leaderboard:global       → All-time global ZSet
        leaderboard:weekly:2026-19  → Weekly ZSet (reset every Monday)
        leaderboard:daily:2026-05-07 → Daily ZSet (TTL: 48h)
        leaderboard:segment:india  → Country/segment ZSet
        ```
        * **Sharding by segment:** If 50M becomes too hot on one node, shard by country/game-mode across cluster nodes.
        * **Score update pattern:** Use ZADD with XX flag (update only, don't add) for existing users; NX for new users.
    * **Rank Pagination (avoid ZREVRANGE 0 N for large N):**
        ```
        -- Use cursor-based pagination
        ZREVRANGEBYSCORE leaderboard:global +inf -inf LIMIT offset count
        ```
    * **Score Tiebreaking:**
        * Encode score as: `score * 1e9 + (MAX_TIMESTAMP - event_timestamp)` — same score, earlier achiever ranks higher.
    * **Cache the top N separately:**
        * Top 100 leaderboard: cache in a separate Redis string with 5-second TTL — serve 99% of reads from there.
        * Individual rank lookup: serve directly from ZSet (fast enough).
    * **Write throughput optimization:**
        * Batch score updates using pipeline — send 1000 ZADD commands in a single pipeline call.
        * Use write-behind: update in-memory counter, flush to Redis every 10 seconds.

---

### Deep Internals

* [ ] **Explain Redis memory internals: how does Redis encode small vs large data structures, and how does this affect your key design?**
    * Redis uses different internal encodings depending on collection size and value types — this is where significant memory savings come from.
    * **Hash encoding:**
        * Small hash (≤128 fields, values ≤64 bytes): `listpack` (formerly `ziplist`) — contiguous memory, cache-friendly, no pointer overhead.
        * Large hash: `hashtable` — O(1) access but with per-entry overhead (pointers, metadata).
        ```
        # Check encoding
        OBJECT ENCODING user:1000
        # Returns: "listpack" or "hashtable"
        ```
    * **Practical Implication — Hash vs Flat Keys:**
        ```
        ❌ Flat keys (wasteful):
        SET user:1:name "Alice"        # 3 Redis objects, 3 × key overhead
        SET user:1:age 30
        SET user:1:city "Delhi"

        ✅ Hash (memory efficient for small objects):
        HSET user:1 name "Alice" age 30 city "Delhi"  # 1 Redis object, listpack encoded
        ```
        * For 1M users with 10 fields each: hashes can save 60-70% memory vs flat keys.
    * **Sorted Set encoding:**
        * Small ZSet (≤128 members, values ≤64 bytes): `listpack` — sequential scan, but tiny and cache-efficient.
        * Large ZSet: `skiplist + hashtable` combo.
    * **String encoding:**
        * Integer strings (e.g., "123"): stored as actual integer — saves ~40 bytes vs a raw string object.
        * Short strings (≤44 bytes): `embstr` — string object and data in a single allocation.
        * Long strings: `raw` — separate allocation, pointer-based.
    * **Key Design Best Practices Based on Encoding:**
        * Keep hash field counts below `hash-max-listpack-entries` (default 128) for memory efficiency.
        * Tune `hash-max-listpack-value` based on your field sizes.
        * Use `OBJECT ENCODING key` and `OBJECT FREQ key` in production to audit encoding.
        * Run `redis-cli --bigkeys` and `redis-cli --memkeys` to find memory hogs.

---

* [ ] **How does Redis handle persistence during a fork? Explain Copy-On-Write (COW) and its impact on memory during peak traffic**
    * When Redis forks for RDB snapshot (`BGSAVE`) or AOF rewrite (`BGREWRITEAOF`), it uses the OS fork() system call.
    * **Fork and Copy-On-Write:**
        * After fork(), parent and child share the same physical memory pages — no data is copied yet.
        * OS marks all shared pages as read-only.
        * When the **parent** (serving requests) modifies a page, the OS copies that page for the child — this is COW.
        * The child always sees the original (snapshot) data. Parent continues with modified pages.
    * **Memory Impact:**
        * **Worst case:** If your workload writes to 100% of keys during the fork window, Redis uses 2× its dataset memory.
        * **Real-world peak:** During BGSAVE on a write-heavy instance, expect 20-50% memory spike.
        * **Production mitigation:**
            * Set `maxmemory` to 50-60% of total RAM (not 80%) to leave room for COW.
            * Schedule BGSAVE during low-write periods (e.g., off-peak hours).
            * Use `save ""` and AOF-only persistence to reduce fork frequency.
            * Monitor `used_memory_rss` vs `used_memory` — large gap = fragmentation or COW in progress.
    * **Transparent Huge Pages (THP) — a hidden enemy:**
        * Linux THP makes COW 2MB pages instead of 4KB — a single byte write copies 2MB.
        * **Always disable THP for Redis in production:**
        ```bash
        echo never > /sys/kernel/mm/transparent_hugepage/enabled
        ```
        * This is one of the most common causes of unexplained Redis latency spikes in production.

---

### Production War Stories & Scenarios

* [ ] **Your Redis instance is showing latency spikes every 2-3 minutes. How do you diagnose and fix it?**
    * This is a classic production incident. The 2-3 minute periodicity is a huge clue — it matches scheduled operations.
    * **Step 1: Enable latency monitoring:**
        ```
        CONFIG SET latency-monitor-threshold 50   # log commands >50ms
        LATENCY LATEST
        LATENCY HISTORY event
        LATENCY RESET
        ```
    * **Step 2: Check slow log:**
        ```
        SLOWLOG GET 25
        SLOWLOG LEN
        ```
    * **Step 3: Cross-reference with system:**
        * Check if spikes align with cron jobs, backup schedules, or RDB saves.
        * `INFO persistence` → check `rdb_last_bgsave_time_sec`
        * `INFO stats` → check `blocked_clients`, `rejected_connections`
    * **Common Root Causes and Fixes:**
    * 
      | Symptom | Root Cause | Fix |
      |---------|-----------|-----|
      | Every 5min spike | BGSAVE triggered | Tune `save` config or move to AOF-only |
      | Spike during backup | THP enabled | `echo never > /sys/.../transparent_hugepage/enabled` |
      | Random spikes | KEYS * or SMEMBERS on large set | Find and replace with SCAN + cursor |
      | Network I/O spike | AOF fsync=always | Change to `appendfsync everysec` |
      | GC-like pause | Large expired key deletion | Use lazy free: `lazyfree-lazy-expire yes` |
    * **Enable lazy freeing (Redis 4.0+):**
        ```
        lazyfree-lazy-eviction yes
        lazyfree-lazy-expire yes
        lazyfree-lazy-server-del yes
        replica-lazy-flush yes
        ```
        * By default, deleting a large key (e.g., a Hash with 1M fields) blocks the main thread. Lazy free moves deletion to a background thread.

---

* [ ] **How would you handle a Redis cache stampede in production that's bringing down your database? Walk through both the immediate response and long-term fix.**
    * **Scenario:** Your "product catalog" Redis key TTL expires at 3 PM. 50,000 concurrent users hit your API simultaneously. All 50,000 requests miss cache and hammer MySQL.
    * **Immediate Mitigation (during incident):**
        * 1. Extend TTL of whatever is in cache (even if slightly stale) using `EXPIRE key 300` — buy 5 minutes.
        * 2. If cache is empty: manually seed cache from a DB read using a deploy script.
        * 3. Enable circuit breaker to serve stale response or graceful degradation.
    * **Correct Long-term Fix — Probabilistic Early Recomputation (PER):**
        ```python
        import math, random, time

        def get_cached_value(key, ttl, beta=1.0):
            data = redis.hgetall(key)  # stores value + expiry_time
            if not data:
                return recompute_and_cache(key, ttl)
            
            expiry = float(data['expiry'])
            value = data['value']
            
            # Probabilistically recompute before expiry
            # Higher traffic = more chances = earlier recompute
            if time.time() - ttl * beta * math.log(random.random()) >= expiry:
                value = recompute_and_cache(key, ttl)
            
            return value
        ```
    * **Alternative: Stale-While-Revalidate Pattern:**
        * Keep two TTLs: `soft_ttl` (serve from cache) and `hard_ttl` (max age).
        * When soft_ttl expires, serve stale data AND trigger async refresh via a background job queue (Celery, SQS).
        ```python
        SOFT_TTL = 300   # 5 min: serve fresh
        HARD_TTL = 600   # 10 min: max stale age

        value = redis.get(key)
        if value is None:
            value = rebuild_from_db()
            redis.setex(key, HARD_TTL, value)
            redis.setex(key + ':soft', SOFT_TTL, '1')
        elif not redis.exists(key + ':soft'):
            # Soft TTL expired — serve stale, trigger async refresh
            trigger_background_refresh.delay(key)
        ```
    * **Mutex Lock Pattern (for non-probabilistic cases):**
        ```python
        lock_key = f"lock:{key}"
        if redis.set(lock_key, 1, nx=True, ex=10):
            value = rebuild_from_db()
            redis.setex(key, ttl, value)
            redis.delete(lock_key)
        else:
            # Wait briefly and retry — someone else is rebuilding
            time.sleep(0.05)
            value = redis.get(key) or serve_default()
        ```

---

* [ ] **Design a session management system using Redis that handles 10 million active users, supports multi-device logout, and complies with GDPR**
    * **Data Model:**
        ```
        # Session token → session data
        session:{session_token} → Hash {
            user_id, device_id, ip, created_at, last_active, metadata
        }
        TTL: 30 days (sliding)

        # User → all active sessions (for multi-device management)
        user:sessions:{user_id} → Set {session_token_1, session_token_2, ...}
        TTL: 35 days

        # Device fingerprint deduplication
        user:device:{user_id}:{device_fingerprint} → session_token
        TTL: 30 days
        ```
    * **Session Creation:**
        ```python
        def create_session(user_id, device_id, metadata):
            token = secrets.token_urlsafe(32)  # cryptographically secure
            pipe = redis.pipeline()
            pipe.hset(f"session:{token}", mapping={
                'user_id': user_id,
                'device_id': device_id,
                'created_at': time.time(),
                'last_active': time.time()
            })
            pipe.expire(f"session:{token}", 30 * 86400)
            pipe.sadd(f"user:sessions:{user_id}", token)
            pipe.expire(f"user:sessions:{user_id}", 35 * 86400)
            pipe.execute()
            return token
        ```
    * **Multi-device Logout:**
        ```python
        def logout_all_devices(user_id):
            sessions = redis.smembers(f"user:sessions:{user_id}")
            pipe = redis.pipeline()
            for token in sessions:
                pipe.delete(f"session:{token}")
            pipe.delete(f"user:sessions:{user_id}")
            pipe.execute()
        ```
    * **GDPR Right-to-Erasure:**
        ```python
        def delete_user_data(user_id):
            logout_all_devices(user_id)  # removes all sessions
            redis.delete(f"user:profile:{user_id}")
            redis.delete(f"user:preferences:{user_id}")
            # Also publish event for downstream services
            redis.publish('gdpr:deletion', json.dumps({'user_id': user_id}))
        ```
    * **Sliding Expiry on Activity:**
        ```python
        def validate_session(token):
            pipe = redis.pipeline()
            pipe.hgetall(f"session:{token}")
            pipe.expire(f"session:{token}", 30 * 86400)  # reset TTL on access
            results = pipe.execute()
            return results[0]  # session data or None
        ```
    * **Scaling Consideration:**
        * 10M users × avg 2 sessions × ~300 bytes per session = ~6GB — fits on a single Redis instance easily.
        * Use Redis Cluster if write throughput exceeds single-node limit (typically ~100K ops/sec).
        * Shard session keys by user_id: `{user_id % 16}:session:{token}` for cluster-aware routing.

---

### Observability & Operations

* [ ] **How do you detect and fix memory fragmentation in Redis?**
    * Memory fragmentation occurs when Redis's allocator (jemalloc) cannot reuse freed memory efficiently, leading to `used_memory_rss` (physical RAM used) being much larger than `used_memory` (logical data size).
    * **Detect:**
        ```
        INFO memory
        # Key metrics:
        # used_memory: 4GB (what Redis thinks it uses)
        # used_memory_rss: 7GB (what OS actually allocated)
        # mem_fragmentation_ratio: 1.75  ← dangerous (>1.5 is high)
        ```
        * `mem_fragmentation_ratio > 1.5`: High fragmentation — investigate.
        * `mem_fragmentation_ratio < 1.0`: Redis is swapping to disk — immediate action needed.
    * **Root Causes:**
        * Frequent deletion/expiry of variable-size keys → jemalloc can't reuse freed slabs.
        * Large number of small keys → internal allocator overhead.
        * Workloads with lots of key resizing (e.g., frequent APPEND on strings).
    * **Fix Options:**
        * **Redis 4.0+ Active Defragmentation (recommended):**
            ```
            CONFIG SET activedefrag yes
            CONFIG SET active-defrag-ignore-bytes 100mb
            CONFIG SET active-defrag-threshold-lower 10
            CONFIG SET active-defrag-threshold-upper 30
            CONFIG SET active-defrag-cycle-min 25
            CONFIG SET active-defrag-cycle-max 75
            ```
            * Redis incrementally copies live objects to new memory regions in the background.
        * **Restart Redis** (nuclear option): Causes downtime but reclaims all fragmented memory.
        * **Adjust TTLs** to cause more uniform expiry — reduces fragmentation long-term.

---

* [ ] **Walk through how you'd set up a Redis Cluster from scratch for a high-traffic production system with zero-downtime deployment requirements**
    * **Minimum Production Setup:** 6 nodes (3 masters + 3 replicas), spread across 3 availability zones.
    * **Node Configuration (each redis.conf):**
        ```
        # Cluster
        cluster-enabled yes
        cluster-config-file nodes.conf
        cluster-node-timeout 5000
        cluster-require-full-coverage no  # serve partial data if some nodes down

        # Persistence
        save 3600 1
        save 300 100
        appendonly yes
        appendfsync everysec

        # Performance
        hz 20
        lazyfree-lazy-eviction yes
        lazyfree-lazy-expire yes
        tcp-backlog 511

        # Memory
        maxmemory 12gb
        maxmemory-policy allkeys-lru
        ```
    * **Cluster Bootstrap:**
        ```bash
        redis-cli --cluster create \
          10.0.1.1:6379 10.0.2.1:6379 10.0.3.1:6379 \
          10.0.1.2:6379 10.0.2.2:6379 10.0.3.2:6379 \
          --cluster-replicas 1
        ```
    * **Zero-Downtime Rolling Upgrade:**
        1. Upgrade replicas one at a time (no traffic impact — replicas don't serve writes by default).
        2. Trigger manual failover on each upgraded replica to make it master: `CLUSTER FAILOVER TAKEOVER`.
        3. The old master becomes replica — upgrade it.
        4. Repeat per shard.
        * `CLUSTER FAILOVER` (without TAKEOVER): waits for replica to fully catch up before promotion — safest.
        * `CLUSTER FAILOVER FORCE`: don't wait for sync — use only if master is unresponsive.
    * **Health Checks:**
        ```bash
        redis-cli --cluster check <any-node-ip>:6379
        redis-cli --cluster info <any-node-ip>:6379
        ```
    * **Multi-key operations across slots (hash tags):**
        ```
        # These keys all land on the same slot because {user:1} is the hash tag
        {user:1}:profile
        {user:1}:settings
        {user:1}:sessions
        ```

---

### Advanced Patterns

* [ ] **Explain the difference between Redis Streams and Pub/Sub, and when you'd choose each in a production event-driven system**
    * Both are messaging primitives, but they solve fundamentally different problems.
    * **Pub/Sub — Fire and Forget:**
        * Message is delivered only to currently connected subscribers. If no subscriber is listening, message is lost.
        * No persistence, no consumer tracking, no replay.
        ```
        SUBSCRIBE notifications:user:123
        PUBLISH notifications:user:123 '{"type": "order_shipped", "order_id": 456}'
        ```
        * **Use when:** Real-time push notifications, live dashboards, cache invalidation signals where missing a message is acceptable.
    * **Streams — Durable Event Log:**
        * Append-only log with persistent storage. Messages survive restarts. Consumer groups track per-consumer offsets. Failed messages can be reclaimed.
        ```
        # Producer
        XADD orders * order_id 101 status created user_id 999

        # Consumer group setup
        XGROUP CREATE orders order-processors $ MKSTREAM

        # Consumer reads
        XREADGROUP GROUP order-processors worker-1 COUNT 10 STREAMS orders >

        # Acknowledge after processing
        XACK orders order-processors <message-id>

        # Reclaim pending messages from crashed consumers
        XAUTOCLAIM orders order-processors worker-2 60000 0-0
        ```
    * **Decision Framework:**
    * 
      | Need | Use |
      |------|-----|
      | Real-time, loss-tolerant | Pub/Sub |
      | Guaranteed delivery | Streams |
      | Replay from beginning | Streams |
      | Multiple independent consumers | Streams (consumer groups) |
      | Fan-out to all listeners | Pub/Sub |
      | At-least-once processing | Streams + XACK |
    * **Production Pattern — Outbox with Streams:**
        * Write event to DB and Redis Stream in the same transaction (or use CDC to feed Streams). Consumer group processes events with at-least-once semantics. Dead-letter queue for failed messages after N retries.

---

* [ ] **How would you implement cache invalidation across microservices without distributed transactions?**
    * This is one of the hardest distributed systems problems — "there are only two hard things in CS: naming and cache invalidation."
    * **Pattern 1: Event-Driven Invalidation via Redis Pub/Sub**
        * Order Service writes to DB → publishes `cache:invalidate:product:123` on Redis channel → all services subscribed to that channel delete their local cache entries.
        * **Problem:** Pub/Sub is not reliable — if a service is down during the publish, it misses the invalidation.
    * **Pattern 2: Event-Driven via Redis Streams (Reliable)**
        ```python
        # On write (Order Service)
        redis.xadd('cache:invalidation:events', {
            'entity': 'product',
            'id': '123',
            'version': new_version,
            'timestamp': time.time()
        })

        # Each microservice runs a consumer group
        events = redis.xreadgroup('GROUP', 'inventory-service', 'worker-1', 'COUNT', 100, 'STREAMS', 'cache:invalidation:events', '>')
        for event in events:
            redis.delete(f"product:{event['id']}")
            redis.xack('cache:invalidation:events', 'inventory-service', event['id'])
        ```
    * **Pattern 3: Version-Based / Tag-Based Invalidation**
        * Attach a version number or cache tag to every cached object.
        * Instead of invalidating by key, invalidate by incrementing a version counter.
        ```python
        # Cache key includes version
        version = redis.get('product:version:123') or '1'
        cache_key = f"product:123:v{version}"

        # Invalidate by bumping version (old keys expire via TTL)
        redis.incr('product:version:123')
        # Old versioned keys have TTL, so they clean up automatically
        ```
    * **Pattern 4: Write-Through with CDC (Change Data Capture)**
        * Use Debezium (or MySQL binlog reader) to stream DB changes into Kafka/Redis Streams. Cache consumers listen to the stream and update/invalidate cache proactively.
        * **Gold standard** for strong consistency — DB is the single source of truth.
    * **Key Principle:** Design for eventual consistency. Use TTL as the safety net — even if invalidation fails, the cache expires eventually. Short TTLs + event-driven invalidation = strong enough consistency for most systems.