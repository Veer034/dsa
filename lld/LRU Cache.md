# LLD: LRU Cache

> **Experience Level:** 10+ Years Java | Spring Boot · Kafka · Redis · MySQL · Elasticsearch · ScyllaDB · Druid

---

## 🔁 Clarifying Questions (You → Interviewer)

| # | Question | Why It Matters |
|---|----------|----------------|
| 1 | What are the expected operations? Just `get`/`put`, or also `delete`? | API surface |
| 2 | Is this single-threaded or must it be thread-safe? | Synchronization strategy |
| 3 | Should it support generic types or just `<Integer, Integer>`? | Type parameterization |
| 4 | Do we need TTL (time-to-live) expiry per entry, or just capacity-based eviction? | Cache feature set |
| 5 | Should eviction be synchronous or can it happen in a background thread? | Latency vs throughput tradeoff |
| 6 | What's the expected capacity range — thousands or millions of entries? | Memory model considerations |
| 7 | Do we need cache statistics (hit rate, eviction count)? | Observability |

---

## 🔁 Expected Follow-up Questions (Interviewer → You)

- "Why `LinkedHashMap` over `HashMap + LinkedList`?"
- "How do you achieve O(1) for both `get` and `put`?"
- "Walk me through what happens on a cache hit vs cache miss."
- "How would you make this distributed (like Redis)?"
- "How does `synchronized` differ from using `ReentrantReadWriteLock` here?"
- "What are the tradeoffs of using `LinkedHashMap` with `accessOrder=true`?"
- "How would you add TTL support?"

---

## Core Algorithm

LRU Cache requires **O(1) get and O(1) put**. The standard approach:

- **HashMap** → O(1) lookup by key → maps key to doubly-linked-list node
- **Doubly Linked List** → O(1) move to head (most recently used), remove from tail (least recently used)

---

## Implementation 1: Manual Doubly Linked List + HashMap

```java
public class LRUCache<K, V> {

    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final DoublyLinkedList<K, V> list;

    // ─── Node ────────────────────────────────────────────────────────────────

    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    // ─── Doubly Linked List ───────────────────────────────────────────────────

    private static class DoublyLinkedList<K, V> {
        private final Node<K, V> head; // dummy head (MRU side)
        private final Node<K, V> tail; // dummy tail (LRU side)

        DoublyLinkedList() {
            head = new Node<>(null, null);
            tail = new Node<>(null, null);
            head.next = tail;
            tail.prev = head;
        }

        void addToFront(Node<K, V> node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
        }

        void remove(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            node.prev = null;
            node.next = null;
        }

        Node<K, V> removeLast() {
            if (tail.prev == head) return null; // empty
            Node<K, V> lru = tail.prev;
            remove(lru);
            return lru;
        }

        void moveToFront(Node<K, V> node) {
            remove(node);
            addToFront(node);
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    public LRUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be > 0");
        this.capacity = capacity;
        this.map = new HashMap<>(capacity);
        this.list = new DoublyLinkedList<>();
    }

    /**
     * O(1) get. Returns -1 (or null) if not found.
     */
    public V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) return null;
        list.moveToFront(node); // mark as recently used
        return node.value;
    }

    /**
     * O(1) put. Evicts LRU entry if at capacity.
     */
    public void put(K key, V value) {
        Node<K, V> existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            list.moveToFront(existing);
            return;
        }

        if (map.size() == capacity) {
            Node<K, V> lru = list.removeLast();
            if (lru != null) map.remove(lru.key);
        }

        Node<K, V> newNode = new Node<>(key, value);
        map.put(key, newNode);
        list.addToFront(newNode);
    }

    public int size() { return map.size(); }
    public int capacity() { return capacity; }
}
```

---

## Implementation 2: Using `LinkedHashMap` (Concise)

```java
public class LRUCacheLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    public LRUCacheLinkedHashMap(int capacity) {
        // accessOrder = true → moves entry to tail on access (most recently used at end)
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }

    // Wrap to match expected API
    public V getEntry(K key) {
        return getOrDefault(key, null);
    }

    public void putEntry(K key, V value) {
        put(key, value);
    }
}
```

> ⚠️ `LinkedHashMap` is **not thread-safe**. Wrap it with `Collections.synchronizedMap()` or use a `ReentrantReadWriteLock` for production.

---

## Thread-Safe LRU Cache

```java
public class ThreadSafeLRUCache<K, V> {
    private final LRUCache<K, V> cache;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock  = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    // Stats
    private final AtomicLong hits   = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public ThreadSafeLRUCache(int capacity) {
        this.cache = new LRUCache<>(capacity);
    }

    public V get(K key) {
        // get() promotes the node (write operation internally), so use write lock
        writeLock.lock();
        try {
            V value = cache.get(key);
            if (value != null) hits.incrementAndGet();
            else misses.incrementAndGet();
            return value;
        } finally {
            writeLock.unlock();
        }
    }

    public void put(K key, V value) {
        writeLock.lock();
        try {
            cache.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    public double hitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }

    public long getHits()   { return hits.get(); }
    public long getMisses() { return misses.get(); }
}
```

---

## LRU Cache with TTL (Time-To-Live)

```java
public class TTLLRUCache<K, V> {
    private final int capacity;
    private final long ttlMillis;

    private static class Entry<V> {
        V value;
        long expiresAt;
        Entry(V v, long ttl) { this.value = v; this.expiresAt = System.currentTimeMillis() + ttl; }
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    private final LRUCache<K, Entry<V>> inner;

    public TTLLRUCache(int capacity, long ttlMillis) {
        this.capacity = capacity;
        this.ttlMillis = ttlMillis;
        this.inner = new LRUCache<>(capacity);
    }

    public V get(K key) {
        Entry<V> entry = inner.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            // Lazy eviction
            inner.put(key, null); // will be evicted; alternatively remove explicitly
            return null;
        }
        return entry.value;
    }

    public void put(K key, V value) {
        inner.put(key, new Entry<>(value, ttlMillis));
    }
}
```

---

## Complexity Summary

| Operation | Time Complexity | Space |
|-----------|----------------|-------|
| `get(key)` | O(1) | — |
| `put(key, value)` | O(1) | — |
| Eviction | O(1) | — |
| Total space | — | O(capacity) |

---

## Redis as Distributed LRU Cache

```yaml
# Redis config (redis.conf)
maxmemory 512mb
maxmemory-policy allkeys-lru
```

```java
// Spring Boot Redis usage
@Service
public class RedisLRUCache {
    @Autowired
    private StringRedisTemplate redis;

    public void put(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }
}
```

Redis handles LRU eviction automatically when `maxmemory-policy allkeys-lru` is set.

---

## Spring Boot Integration Example

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();

        return RedisCacheManager.builder(cf)
                .cacheDefaults(config)
                .build();
    }
}

@Service
public class ProductService {
    @Cacheable(value = "products", key = "#id")
    public Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow();
    }

    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
}
```

---

## Design Patterns Used

| Pattern | Usage |
|---------|-------|
| Decorator | `ThreadSafeLRUCache` wraps `LRUCache` |
| Composite | TTL wraps entry inside LRU |
| Proxy | Spring `@Cacheable` as transparent cache proxy |

---

## Extension Points

- **Segment locking**: Split into N buckets with separate locks (like `ConcurrentHashMap`) for higher throughput
- **Async eviction**: Background thread evicts expired TTL entries proactively
- **Metrics**: Expose hit/miss rate to Prometheus via Micrometer
- **Write-through cache**: On `put()`, also persist to MySQL asynchronously