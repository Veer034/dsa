
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
* **1. String**

    * Simplest key–value (max 512MB).
    * **Use**: cache, counters, tokens.
    * **Example**:

      ```
      SET page_views 100
      INCR page_views
      ```

* **2. List**

    * Ordered collection (linked list).
    * **Use**: queues, task pipelines.
    * **Example**:

      ```
      LPUSH jobs job1
      RPOP jobs
      ```



* **3. Set**

    * Unordered, unique elements.
    * **Use**: unique users, tags.
    * **Example**:

      ```
      SADD online_users u1 u2
      SISMEMBER online_users u1
      ```


* **4. Sorted Set (ZSet)**

    * Set + score (ordered by score).
    * **Use**: leaderboards, ranking.
    * **Example**:

      ```
      ZADD leaderboard 100 user1
      ZRANGE leaderboard 0 10 WITHSCORES
      ```



* **5. Hash**

    * Key → field → value (like a row).
    * **Use**: objects (user profile).
    * **Example**:

      ```
      HSET user:1 name "Alex" age 30
      HGET user:1 name
      ```



* **6. Bitmap**

    * Bit-level operations on strings.
    * **Use**: flags, daily active users.
    * **Example**:

      ```
      SETBIT login:2026-01-07 123 1
      GETBIT login:2026-01-07 123
      ```


* **7. HyperLogLog**

    * Probabilistic structure (very low memory).
    * **Use**: count unique items approximately.
    * **Example**:

      ```
      PFADD visitors u1 u2 u3
      PFCOUNT visitors
      ```

* **8. Streams**

    * Append-only log with consumer groups.
    * **Use**: event streaming, async processing.
    * **Example**:

      ```
      XADD orders * orderId 101 status created
      XREAD STREAMS orders 0
      ```


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

    * [ ] **Is Redis single-threaded or multi-threaded?**
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
    * ```
    # Allow 100 requests per minute per user
    key = "rate:limit:user123"
    INCR key
    EXPIRE key 60
    GET key  # if > 100, reject request
    ```
    * Better approach (sliding window): Use Sorted Set with timestamps as scores, remove old entries, count remaining.
    * ```
    ZADD rate:user123 <timestamp> <request_id>
    ZREMRANGEBYSCORE rate:user123 0 <60_seconds_ago>
    ZCARD rate:user123  # if > 100, reject
    ```
---
* [x] **Explain INCR, INCRBY, and their atomic nature**
    * **INCR:** Increments a key's value by 1. If key doesn't exist, sets it to 0 then increments to 1.
      **INCRBY:** Increments by a specified amount. INCRBY counter 5 adds 5 to the counter.
      **Atomic nature:** Both operations are atomic - they read, increment, and write in a single step with no possibility of race conditions. Even with 1000 concurrent clients, each increment is guaranteed to execute completely before the next one, ensuring accurate counts without locks.
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
    * ```
    # config
    save 900 1          # RDB: save after 900 sec if 1 key changed
    appendonly yes      # AOF: enable
    appendfsync everysec # AOF: sync every second
    ```

---
* [x] **How persistence doesn't slow down Redis?**
    * **RDB snapshots:**
        * ```
      Main thread: [serving requests at full speed]
      Background thread: [fork process → write snapshot to disk]
      ```
        * Uses fork() to create child process
        * Child writes snapshot while parent continues serving requests
        * No blocking of main operations
    * **AOF writes:**
        * ```
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
* [x] Why choose Redis is production?
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
    * ```
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
