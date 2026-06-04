# LLD: Thread-Safe HashMap

> **Experience Level:** 10+ Years Java | Spring Boot · Kafka · Redis · MySQL · Elasticsearch · ScyllaDB · Druid

---

## 🔁 Clarifying Questions (You → Interviewer)

| # | Question | Why It Matters |
|---|----------|----------------|
| 1 | Should this support generic key/value types? | Type safety design |
| 2 | What's the expected read vs write ratio? | Informs lock strategy (R/W lock vs full sync) |
| 3 | Do we need atomic operations like `putIfAbsent`, `computeIfAbsent`? | API completeness |
| 4 | Should we design from scratch or improve on Java's `HashMap`? | Depth of implementation expected |
| 5 | What is the expected concurrency level (number of threads)? | Number of segments for segment-lock design |
| 6 | Is resizing (dynamic rehashing) in scope? | Complexity of implementation |
| 7 | Should we support `null` keys/values? | Edge case handling |

---

## 🔁 Expected Follow-up Questions (Interviewer → You)

- "How does `ConcurrentHashMap` differ from `Collections.synchronizedMap()`?"
- "Why is segment locking better than a single lock?"
- "What is the problem with the double-checked locking pattern in HashMap?"
- "How would you handle hash collisions in your design?"
- "What happens during a resize operation — how do you prevent data loss?"
- "Walk me through the `get()` path without locks."
- "How does Java 8's `ConcurrentHashMap` avoid write locks for reads?"

---

## Approach 1: Coarse-Grained Lock (Simple, Low Throughput)

```java
public class SynchronizedHashMap<K, V> {
    private final HashMap<K, V> map = new HashMap<>();

    public synchronized V get(K key) {
        return map.get(key);
    }

    public synchronized void put(K key, V value) {
        map.put(key, value);
    }

    public synchronized V remove(K key) {
        return map.remove(key);
    }

    public synchronized boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public synchronized int size() {
        return map.size();
    }
}
```

> ❌ All reads block all writes and other reads. Low throughput. Use only for low-contention scenarios.

---

## Approach 2: `ReentrantReadWriteLock` (Better Read Throughput)

```java
public class ReadWriteLockHashMap<K, V> {
    private final HashMap<K, V> map = new HashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock  = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public V get(K key) {
        readLock.lock();
        try {
            return map.get(key);
        } finally {
            readLock.unlock();
        }
    }

    public void put(K key, V value) {
        writeLock.lock();
        try {
            map.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    public V remove(K key) {
        writeLock.lock();
        try {
            return map.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Atomic: put only if key absent. Without this, check-then-act is non-atomic.
     */
    public V putIfAbsent(K key, V value) {
        writeLock.lock();
        try {
            return map.putIfAbsent(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Atomic compute: compute value only if absent (e.g., cache-aside pattern)
     */
    public V computeIfAbsent(K key, java.util.function.Function<K, V> mappingFn) {
        // Optimistic: read first
        readLock.lock();
        try {
            V existing = map.get(key);
            if (existing != null) return existing;
        } finally {
            readLock.unlock();
        }

        // Upgrade to write lock
        writeLock.lock();
        try {
            // Double-check after acquiring write lock
            return map.computeIfAbsent(key, mappingFn);
        } finally {
            writeLock.unlock();
        }
    }

    public int size() {
        readLock.lock();
        try { return map.size(); } finally { readLock.unlock(); }
    }
}
```

---

## Approach 3: Segment-Locked HashMap (High Throughput — ConcurrentHashMap-style)

```java
public class SegmentedHashMap<K, V> {

    private static final int DEFAULT_SEGMENTS = 16;

    @SuppressWarnings("unchecked")
    private final HashMap<K, V>[]              segments;
    private final ReentrantReadWriteLock[]      segmentLocks;
    private final int                           segmentCount;

    public SegmentedHashMap() {
        this(DEFAULT_SEGMENTS);
    }

    @SuppressWarnings("unchecked")
    public SegmentedHashMap(int segmentCount) {
        this.segmentCount = segmentCount;
        this.segments     = new HashMap[segmentCount];
        this.segmentLocks = new ReentrantReadWriteLock[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            segments[i]     = new HashMap<>();
            segmentLocks[i] = new ReentrantReadWriteLock();
        }
    }

    private int segmentIndex(K key) {
        int hash = key == null ? 0 : key.hashCode();
        // Spread high bits (like ConcurrentHashMap's spread())
        hash ^= (hash >>> 16);
        return Math.abs(hash % segmentCount);
    }

    public V get(K key) {
        int idx = segmentIndex(key);
        segmentLocks[idx].readLock().lock();
        try {
            return segments[idx].get(key);
        } finally {
            segmentLocks[idx].readLock().unlock();
        }
    }

    public void put(K key, V value) {
        int idx = segmentIndex(key);
        segmentLocks[idx].writeLock().lock();
        try {
            segments[idx].put(key, value);
        } finally {
            segmentLocks[idx].writeLock().unlock();
        }
    }

    public V remove(K key) {
        int idx = segmentIndex(key);
        segmentLocks[idx].writeLock().lock();
        try {
            return segments[idx].remove(key);
        } finally {
            segmentLocks[idx].writeLock().unlock();
        }
    }

    public V putIfAbsent(K key, V value) {
        int idx = segmentIndex(key);
        segmentLocks[idx].writeLock().lock();
        try {
            return segments[idx].putIfAbsent(key, value);
        } finally {
            segmentLocks[idx].writeLock().unlock();
        }
    }

    /**
     * Approximate size — does NOT lock all segments simultaneously.
     * For exact size, lock all segments (expensive).
     */
    public int size() {
        int total = 0;
        for (int i = 0; i < segmentCount; i++) {
            segmentLocks[i].readLock().lock();
            try { total += segments[i].size(); }
            finally { segmentLocks[i].readLock().unlock(); }
        }
        return total;
    }

    public boolean containsKey(K key) {
        int idx = segmentIndex(key);
        segmentLocks[idx].readLock().lock();
        try { return segments[idx].containsKey(key); }
        finally { segmentLocks[idx].readLock().unlock(); }
    }
}
```

---

## Approach 4: Lock-Free Using `ConcurrentHashMap` (Production Recommendation)

```java
public class LockFreeHashMap<K, V> {
    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();

    public V get(K key)           { return map.get(key); }
    public void put(K key, V val) { map.put(key, val); }
    public V remove(K key)        { return map.remove(key); }

    // Atomic operations
    public V putIfAbsent(K key, V value)    { return map.putIfAbsent(key, value); }
    public boolean remove(K key, V value)   { return map.remove(key, value); }
    public boolean replace(K key, V old, V newVal) { return map.replace(key, old, newVal); }

    // computeIfAbsent is atomic in ConcurrentHashMap (uses bin-level sync internally)
    public V computeIfAbsent(K key, java.util.function.Function<K, V> fn) {
        return map.computeIfAbsent(key, fn);
    }
}
```

---

## Custom HashMap Internals (From Scratch)

```java
public class CustomHashMap<K, V> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final double LOAD_FACTOR = 0.75;

    private Object[] buckets;
    private int size;
    private int capacity;

    private static class Entry<K, V> {
        K key; V value; Entry<K, V> next;
        Entry(K k, V v) { key = k; value = v; }
    }

    @SuppressWarnings("unchecked")
    public CustomHashMap() {
        capacity = DEFAULT_CAPACITY;
        buckets = new Object[capacity];
    }

    private int getBucketIndex(K key) {
        int hash = key == null ? 0 : key.hashCode();
        hash ^= (hash >>> 16);
        return Math.abs(hash & (capacity - 1));
    }

    @SuppressWarnings("unchecked")
    public void put(K key, V value) {
        if ((double) size / capacity >= LOAD_FACTOR) resize();
        int idx = getBucketIndex(key);
        Entry<K, V> head = (Entry<K, V>) buckets[idx];
        for (Entry<K, V> e = head; e != null; e = e.next) {
            if (e.key.equals(key)) { e.value = value; return; }
        }
        Entry<K, V> newEntry = new Entry<>(key, value);
        newEntry.next = head;
        buckets[idx] = newEntry;
        size++;
    }

    @SuppressWarnings("unchecked")
    public V get(K key) {
        int idx = getBucketIndex(key);
        for (Entry<K, V> e = (Entry<K, V>) buckets[idx]; e != null; e = e.next)
            if (e.key.equals(key)) return e.value;
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        capacity *= 2;
        Object[] newBuckets = new Object[capacity];
        for (Object bucket : buckets) {
            for (Entry<K, V> e = (Entry<K, V>) bucket; e != null; e = e.next) {
                int idx = getBucketIndex(e.key);
                Entry<K, V> entry = new Entry<>(e.key, e.value);
                entry.next = (Entry<K, V>) newBuckets[idx];
                newBuckets[idx] = entry;
            }
        }
        buckets = newBuckets;
    }

    public int size() { return size; }
}
```

---

## Comparison Table

| Approach | Thread Safety | Read Performance | Write Performance | Notes |
|----------|--------------|-----------------|-------------------|-------|
| `synchronized` | ✅ | ❌ Blocks all | ❌ Blocks all | Simplest, low throughput |
| `ReadWriteLock` | ✅ | ✅ Concurrent reads | ✅ Exclusive writes | Good for read-heavy |
| Segment lock | ✅ | ✅ | ✅ | Pre-Java 8 `ConcurrentHashMap` style |
| `ConcurrentHashMap` | ✅ | ✅ No locks on reads | ✅ CAS + bin lock | **Recommended for production** |

---

## When to Use What

```
Read-heavy (> 80% reads)       → ReadWriteLock or ConcurrentHashMap
Write-heavy (frequent updates) → ConcurrentHashMap with striped segments
Exact atomic semantics         → ConcurrentHashMap.compute / merge
Simple correctness, low load   → Collections.synchronizedMap()
Custom eviction / TTL          → Build on top of SegmentedHashMap
```

---

## Common Pitfalls

```java
// ❌ NOT atomic — race condition between get and put
if (!map.containsKey(key)) {
    map.put(key, compute(key));
}

// ✅ ATOMIC — use computeIfAbsent
map.computeIfAbsent(key, k -> compute(k));

// ❌ ConcurrentModificationException
for (String key : map.keySet()) {
    if (condition) map.remove(key); // throws!
}

// ✅ Use iterator remove or ConcurrentHashMap
map.keySet().removeIf(key -> condition);
```

---

## Extension Points

- **Distributed HashMap**: Use Redis `HSET`/`HGET` or ScyllaDB for cross-node sharing
- **Monitoring**: Track segment contention via Micrometer metrics
- **Off-heap storage**: Use Chronicle Map for very large maps without GC pressure
- **Consistent hashing**: Extend `segmentIndex()` for distributed node assignment