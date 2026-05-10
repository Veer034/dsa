## JAVA

### Core Java Fundamentals

* [x] **Difference between == and equals(), and when to override hashCode()?**

  `==` checks reference equality (same object in memory). `equals()` checks logical equality (same value).

    ```java
    String a = new String("hello");
    String b = new String("hello");
    
    a == b        // false — different heap objects
    a.equals(b)   // true  — same content
    ```
    
  ---

  **Why hashCode() must follow**

  HashMap finds the bucket via `hashCode()`, then confirms the match via `equals()`. If two logically equal objects return different hash codes, they land in different buckets — `get()` returns `null` for a key you just `put()`.

  **The contract:** if `a.equals(b)` → `a.hashCode() == b.hashCode()`. Always override both, always use the same fields.

    ```java
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point other)) return false;
        return x == other.x && y == other.y;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, y);   // same fields as equals()
    }
    ```

---
* [x] **Explain immutability and why String is immutable**

  Immutability means once an object is created, its state cannot change.

  **1. String pool / interning** — the JVM caches string literals in a pool and reuses them across references. If strings were mutable, one reference changing `"hello"` to `"world"` would silently affect every other reference pointing to the same pooled object.

  **2. Thread safety** — immutable objects are inherently safe to share across threads with no synchronization needed.

  **3. HashCode caching** — `String` caches its hash code after first computation. Safe only because the value can never change. This makes strings efficient as HashMap keys.

  **4. Security** — class names, file paths, and network URLs are passed as strings to sensitive APIs. If strings were mutable, code could pass a validated path to a security check, then mutate it before the actual file operation.
    
  ---

  **How it's enforced internally:**

    ```java
    public final class String {          // final — can't subclass and override behaviour
        private final char[] value;      // final — reference can't be reassigned
                                         // char[] itself is never exposed
    }
    ```

  `final` on the field prevents reassigning the reference, but the `char[]` array is technically mutable. Java protects it by never returning a reference to the internal array — methods like `toCharArray()` return a defensive copy.

  **The `+=` illusion:**

    ```java
        String s = "hello";
        s += " world";   // does NOT modify the original string
                         // creates a new String object; s now points to it
                         // "hello" is still in the pool, unchanged
    ```

---
* [x] **What are marker interfaces? Give examples**
    * **Marker interfaces** are empty interfaces (no methods) used to **mark a class** so that JVM or libraries apply **special behavior**.
      They are checked using `instanceof` or reflection.
    *  **Examples:** `Serializable` (allows object serialization), `Cloneable` (enables `clone()`), `RandomAccess`
       (fast indexed access in lists).

---
* [x] **Difference between abstract class and interface in Java 8+**
    1. An **abstract class** can have instance variables and constructors; an **interface cannot have instance state or constructors**.
    2. A class can **extend only one abstract class** but **implement multiple interfaces**.
    2. Abstract class methods can have **any access level**; interface methods are **public by default** (except private helper methods).
    3. Interfaces (Java 8–17) can have **default, static, and private methods**.
    4. Use **abstract class** for shared state and base logic, **interface** for contracts and multiple inheritance.


---
* [ ] **What is the difference between `final`, `finally`, and `finalize()`?**
    * Three completely unrelated keywords — seniors must answer precisely:
    * **`final`:** Modifier applied to variables (prevents reassignment), methods (prevents overriding), or classes (prevents subclassing). A `final` reference still lets you mutate the object's internals — only the reference itself is locked.
    * **`finally`:** Block in try-catch-finally that **always executes** after try/catch regardless of whether an exception was thrown. Used for guaranteed resource cleanup. Exceptions: `System.exit()` call or JVM crash.
    * **`finalize()`:** Instance method on `Object`, called by GC before reclaiming memory. **Deprecated Java 9, removed Java 18.** GC timing is non-deterministic — resource cleanup here is unreliable and can delay GC. Use `try-with-resources` or the `Cleaner` API instead.
    * **Critical trap in `finally`:** If `finally` contains a `return` or `throw`, it silently swallows any exception from the `try` block:
    ```java
    int riskyMethod() {
        try {
            throw new RuntimeException("real error");
        } finally {
            return 42; // ← exception is silently swallowed. Never do this.
        }
    }
    ```
    * **Rule:** Never use `return`, `break`, or `continue` inside `finally`. It hides exceptions and makes production debugging a nightmare.

---
* [ ] **Explain Java Generics — type erasure, bounded wildcards, and PECS**
    * **Type Erasure:** Generic type parameters (`<T>`, `<String>`) exist only at compile time. At runtime they are erased to `Object` (or the bound). `List<String>` and `List<Integer>` are both just `List` at runtime. This is why `new T[]`, `instanceof List<String>`, and `T.class` are all illegal — the type info doesn't exist at runtime.
    * **Bounded wildcards:**
        * `<? extends T>` — upper bounded. Accepts `T` and any subtype. **Read-only producer** — you can read elements as `T`, but cannot add (compiler doesn't know the exact subtype).
        * `<? super T>` — lower bounded. Accepts `T` and any supertype. **Write-capable consumer** — you can add `T` or subtypes, but reading gives only `Object`.
    * **PECS — Producer Extends, Consumer Super (effective Java rule):**
        * If a collection **produces** values you read: use `extends`.
        * If a collection **consumes** values you write: use `super`.
    ```java
    // Copy from source (producer) to dest (consumer)
    public static <T> void copy(List<? super T> dest, List<? extends T> src) {
        for (T item : src) dest.add(item);
    }
    ```
    * **Heap pollution:** Mixing raw types with generics bypasses compile-time checks. A raw `List` can accept any type; the `ClassCastException` surfaces far from the actual bug when you try to use the element. Enable `-Xlint:unchecked` in CI and never mix raw types with generic types.
    * **Type erasure and reflection:** To recover generic type info at runtime, use `ParameterizedType` via reflection — e.g., Jackson's `TypeReference<List<Order>>` trick works by capturing the type in an anonymous subclass so reflection can see it.

---
* [ ] **What are sealed classes (Java 17) and records (Java 16)? When do you use each?**
    * **Records (Java 16+):** Compact, immutable data carriers. The compiler auto-generates constructor, getters, `equals()`, `hashCode()`, and `toString()` from the declared components. Ideal for DTOs, value objects, and data returned from queries.
    ```java
    public record OrderSummary(long orderId, String status, double amount) {}
    // Immutable, auto-equals/hashCode/toString, zero boilerplate
    ```
    * Records **cannot** extend other classes, cannot add mutable state, and all fields are implicitly `final`. Compact constructor allows validation: `if (amount < 0) throw new IllegalArgumentException(...)`.
    * **Sealed Classes (Java 17+):** Restrict which classes can extend/implement a type. Every permitted subtype must be in the same package/module. Together with `pattern matching` in `switch`, sealed classes enable exhaustive type-safe dispatch.
    ```java
    public sealed interface PaymentResult
        permits PaymentSuccess, PaymentFailed, PaymentPending {}

    // Exhaustive switch — compiler error if a permitted type is missing
    String message = switch (result) {
        case PaymentSuccess s  -> "Paid: " + s.transactionId();
        case PaymentFailed f   -> "Failed: " + f.reason();
        case PaymentPending p  -> "Pending since: " + p.since();
    };
    ```
    * **Use records** for pure data; **use sealed classes** to model closed domain hierarchies (event types, result types, state machines) where you need compile-time exhaustiveness guarantees.

---
* [ ] **What is the difference between `String`, `StringBuilder`, and `StringBuffer`?**
    * **`String`:** Immutable. Every `+` or `concat()` creates a new `String` object. Safe to share across threads without synchronization. Suitable when the string is not modified after creation.
    * **`StringBuilder`:** Mutable, **not thread-safe**. Backed by a resizable char array. Append/insert/delete are in-place with no new object creation. Use in single-threaded string-building (loops, builders, serializers).
    * **`StringBuffer`:** Mutable, **thread-safe** (all methods are `synchronized`). Same API as `StringBuilder` but ~2–3× slower due to lock overhead. Only use when a mutable string truly needs to be shared across threads — which is rare. Prefer `StringBuilder` + external synchronization if needed.
    * **Production rule:** Use `StringBuilder` in hot loops. The compiler auto-optimizes single-line `+` to `StringBuilder`, but **loop-based `+` concatenation is not optimized** — each iteration allocates a new `String`. At 1B+/hour event volumes (like your Cisco pipeline), this is measurable GC pressure.
    ```java
    // ❌ Bad — O(n²) allocations, GC pressure
    String result = "";
    for (String part : parts) result += part;

    // ✅ Good — single char array, O(n) total
    StringBuilder sb = new StringBuilder(parts.size() * 20); // pre-size hint
    for (String part : parts) sb.append(part);
    String result = sb.toString();
    ```


### Collections Framework

* [x] **Internal working of HashMap - how does it handle collisions?**

  A HashMap is an array of buckets (default 16). The bucket index for a key is computed as:

    ```java
    index = (n - 1) & hash(key)   // n = array length
    ```

  `hash()` takes the key's `hashCode()` and applies a secondary mix (XORs high bits into low bits) to reduce clustering from poor `hashCode()` implementations.
    
  ---

  **Collision handling — chaining**

  When two keys land in the same bucket, they're stored as a linked list at that index. On `get()`, HashMap finds the bucket, then walks the list calling `equals()` to find the right key.

    ```
    bucket[3] → Entry("cat", 1) → Entry("dog", 2) → null
    ```

  **Java 8 optimisation — treeify:** when a single bucket's chain exceeds 8 entries, the linked list converts to a red-black tree. Lookup degrades from O(n) to O(log n) instead of O(n) in the worst case. It converts back to a list if entries drop below 6.
    
  ---

  **Resize / rehash**

  When entries exceed `capacity × loadFactor` (default 0.75), the array doubles in size and every entry is rehashed into the new array. This is expensive — O(n) — which is why you should pass an initial capacity if you know the size upfront.

    ```java
    new HashMap<>(64);   // avoids rehashing if you're storing ~48 entries
    ```
    
  ---

  **End to end on `put("cat", 1)`:**

    ```
    1. hash("cat")          → compute bucket index
    2. bucket empty?        → insert directly
       bucket occupied?     → walk the chain, check equals()
          key exists?       → overwrite value
          key not found?    → append new Entry to chain
    3. size > threshold?    → resize + rehash
    ```


**The practical gotcha:** if your `hashCode()` always returns the same value, every key lands in the same bucket. The map degrades to a linked list — O(n) for everything. Java 8's treeification softens this to O(log n) but it's still a serious performance problem. Good `hashCode()` distribution matters.

---
* [x] **Difference between HashMap, ConcurrentHashMap, and Hashtable**
    1. **HashMap**: Not thread-safe, fastest, allows **one null key and multiple null values**.
    2. **Hashtable**: Thread-safe using **method-level synchronization**, slower, **no null key/value** (legacy).
    2. **ConcurrentHashMap**: Thread-safe with **fine-grained locking / lock-free reads**, high concurrency, **no null key/value**.
    3. **Concurrency**: HashMap → none, Hashtable → full lock, ConcurrentHashMap → scalable concurrency.
    4. **Use case**: HashMap (single thread), ConcurrentHashMap (multi-threaded), Hashtable (avoid; legacy).
---
* [x] **HashSet vs LinkedHashSet vs CopyOnWriteArraySet**
    1. **HashSet**: No ordering, fastest for add/remove/contains; use when **order doesn’t matter**.
    2. **LinkedHashSet**: Maintains **insertion order** with slight overhead; use when **iteration order matters**.
    3. **CopyOnWriteArraySet**: **Thread-safe**, iteration without locks; very slow writes, fast reads.
    4. Use **HashSet** for single-threaded performance, **LinkedHashSet** for ordered sets,
    5. **CopyOnWriteArraySet** only for **read-heavy, rarely-updated concurrent** scenarios.

---
* [x] **When to use ArrayList vs LinkedList?**
    1. **ArrayList**: Use when you need **fast random access (O(1))**, frequent reads, and appends at the end; most use
       cases fit this.
    2. **LinkedList**: Use when you frequently **insert/remove via iterator in the middle** or need **Deque operations** (`addFirst`, `removeLast`).
    3. **Avoid LinkedList** for random access (`get(i)` is O(n)) and cache-inefficient.
    4. **Rule of thumb**: Choose **ArrayList by default**; use **LinkedList** only for specific deque or iterator-heavy insert/remove needs.

---
* [x] **Explain fail-fast vs fail-safe iterators**
    1. **Fail-fast** iterators throw `ConcurrentModificationException` if the collection is modified during iteration.
       *Example:* `ArrayList`, `HashMap` iterators.
        1. They detect modification using a **modCount** check and fail immediately.
        2. **Fail-safe** iterators iterate over a **copy or snapshot**, so no exception is thrown.
           *Example:* `CopyOnWriteArrayList`, `ConcurrentHashMap`.
        3. **Use fail-fast** to catch bugs early; **use fail-safe** for concurrent, read-heavy scenarios.

```  
# Fail-fast iterator (throws exception)

  List<Integer> list = new ArrayList<>();
  list.add(1);
  list.add(2);
  
  Iterator<Integer> it = list.iterator();
  while (it.hasNext()) {
    Integer val = it.next();
    list.add(3);   // structural modification
  }
  
  Output: ConcurrentModificationException
  
  
  # Fail-safe iterator (no exception)
  
  List<Integer> list = new CopyOnWriteArrayList<>();
  list.add(1);
  list.add(2);
  
  Iterator<Integer> it = list.iterator();
  while (it.hasNext()) {
      Integer val = it.next();
      list.add(3);   // allowed
  }
  Output: No exception
```
---
* [x] **How does TreeMap maintain sorting?**
    1. **TreeMap is sorted because it is implemented as a Red-Black Tree**, not because of hashing.
    2. **TreeMap implements `NavigableMap` → `SortedMap`**, whose **contract requires keys to be kept in sorted order** (natural or via `Comparator`).
    3. **HashMap / ConcurrentHashMap** use **hashing**, so they have **no concept of order** at all.
    4. **LinkedHashMap** maintains **insertion or access order**, but **not sorted order** (it uses a linked list, not comparisons).
    5. So, **only TreeMap is sorted** because it uses **comparison-based tree structure** and explicitly follows the `SortedMap` contract.

  **In short:**

  > TreeMap is sorted due to its **tree-based implementation + SortedMap/NavigableMap contract**, others are not because they are **hash-based or list-ordered**, not comparison-based.

---
* [x] ***Time Complexity - Collections:**

| Collection | get/access | add | remove | contains/search |
|------------|-----------|-----|--------|----------------|
| **ArrayList** | O(1) | O(1)* | O(n) | O(n) |
| **LinkedList** | O(n) | O(1) | O(n) | O(n) |
| **HashMap** | O(1)* | O(1)* | O(1)* | O(1)* |
| **TreeMap** | O(log n) | O(log n) | O(log n) | O(log n) |
| **HashSet** | - | O(1)* | O(1)* | O(1)* |
| **TreeSet** | - | O(log n) | O(log n) | O(log n) |
| **LinkedHashMap** | O(1)* | O(1)* | O(1)* | O(1)* |
| **LinkedHashSet** | - | O(1)* | O(1)* | O(1)* |

**Notes:**
- `*` = amortized/average case
- ArrayList `add`: O(1) amortized, O(n) when resizing
- HashMap/HashSet: O(log n) worst case (tree collision handling)
- LinkedList `add`: O(1) at ends, O(n) at middle

---
* [x] **Comparable vs Comparator:**

**Comparable** - Natural ordering inside the class:
```java
class Student implements Comparable<Student> {
    int id;
    
    public int compareTo(Student s) {
        return this.id - s.id;
    }
}

Collections.sort(students);  // sorts by id
```

**Comparator** - Custom ordering outside the class:
```java
Comparator<Student> byName = (s1, s2) -> s1.name.compareTo(s2.name);

Collections.sort(students, byName);  // sorts by name
```

**Key Difference:**
- **Comparable**: One default way, defined in class
- **Comparator**: Multiple ways, defined externally

Use Comparable for natural order, Comparator for flexibility.



---
* [ ] **How does `LinkedHashMap` work internally and what is its LRU cache use case?**
    * `LinkedHashMap` extends `HashMap` but additionally maintains a **doubly-linked list** through all entries in either insertion order (default) or access order (`accessOrder=true` constructor flag).
    * Every `put` and `get` operation updates the linked list to move the accessed entry to the tail (in access-order mode). The head of the list is always the **least recently used** entry.
    * **LRU Cache implementation** — override `removeEldestEntry()` to evict the oldest entry when capacity is exceeded:
    ```java
    int capacity = 1000;
    Map<String, AdDecision> lruCache = new LinkedHashMap<>(capacity, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AdDecision> eldest) {
            return size() > capacity; // auto-evict when full
        }
    };
    ```
    * **Time complexity:** Same O(1) average as `HashMap` for get/put — the linked list update is O(1). Memory overhead: two extra pointers per entry.
    * **Production use:** In-process caches for frequently accessed but bounded datasets (user session data, ad targeting configs). For distributed eviction, use Redis with `maxmemory-policy allkeys-lru`.

---
* [ ] **What is `ArrayDeque` vs `LinkedList` as a Queue? Which is preferred?**
    * Both implement `Deque` and work as queue or stack, but `ArrayDeque` is preferred in almost every case:
    * **Memory:** `LinkedList` allocates a `Node` wrapper object per element (two pointer fields overhead + object header). `ArrayDeque` uses a resizable circular array — no per-element object allocation, much better cache locality.
    * **Performance:** `ArrayDeque` operations are O(1) amortized and CPU cache-friendly. `LinkedList` has O(1) at head/tail but poor cache performance due to pointer-chasing across non-contiguous heap memory.
    * **Null handling:** `ArrayDeque` does **not** allow `null` elements (fails fast). `LinkedList` allows nulls — which can mask bugs where `null` is returned as a sentinel.
    * **As a Stack:** Use `ArrayDeque.push()`/`pop()`. Never use the `Stack` class — it extends `Vector` and synchronizes every method (legacy, poor performance).
    * **As a Queue:** Use `ArrayDeque.offer()`/`poll()`. For bounded, thread-safe, blocking queues in producer-consumer systems use `ArrayBlockingQueue`.

---
* [ ] **Explain `WeakReference`, `SoftReference`, `PhantomReference`, and `WeakHashMap`. When do they matter in production?**

  | Reference Type | GC collects when | Primary use |
      |---------------|-----------------|-------------|
  | Strong (default) | Object is unreachable | Normal usage |
  | `SoftReference<T>` | JVM is memory-pressured (before OOM) | In-process memory-sensitive caches |
  | `WeakReference<T>` | Next GC cycle regardless of memory | Canonical maps, metadata caches |
  | `PhantomReference<T>` | After finalization, before memory reclaim | Off-heap resource cleanup |

    * **`WeakHashMap`:** Keys are weakly referenced. When a key has no strong references elsewhere, the GC can collect it — the map entry is **automatically removed**. Use for metadata/attribute maps keyed on objects you don't own. The map self-cleans as objects are collected.
    * **`SoftReference` cache:** JVM guarantees soft references are cleared before throwing `OutOfMemoryError`. The cache automatically releases memory under pressure — objects stay cached as long as there's memory to spare. Guava's `CacheBuilder.softValues()` uses this.
    * **`PhantomReference` + `Cleaner`:** Safer alternative to `finalize()` for cleaning up off-heap resources (native memory, file handles). The `Cleaner` API (Java 9+) registers a cleanup action that runs after the object is phantom-reachable.
    * **Why `WeakHashMap` is not a general cache:** Entries can be evicted by GC at any time, even under no memory pressure, if the key has no other strong references. If correctness depends on the entry being present, use a proper cache (`Caffeine`, `Guava Cache`).

---
* [ ] **What is `CopyOnWriteArrayList`? When should you use it and when must you avoid it?**
    * `CopyOnWriteArrayList` is a thread-safe `List` where every write operation (add, set, remove) creates a **full copy of the underlying array**, applies the change to the copy, then atomically replaces the reference. Reads iterate over the snapshot array with no locking.
    * **When to use:** Read-heavy lists that change very rarely — event listener registries, static configuration lists, observer patterns. The "zero-lock read" makes it ideal when writes happen infrequently (e.g., once at startup, or once per minute).
    * **When to avoid:** Any write-heavy workload. A 100-element list copied on every write under high write throughput causes massive heap allocation and GC pressure. Also — iterators reflect the snapshot at iteration start and will **never** see writes made during iteration (fail-safe but potentially stale).
    * **Alternative for write-heavy concurrent lists:** `ConcurrentLinkedQueue` (lock-free), or a `ReentrantReadWriteLock`-guarded `ArrayList` with short critical sections.

---
* [ ] **What is `PriorityQueue`? How does it work internally and what are its thread-safe alternatives?**
    * `PriorityQueue` is an unbounded priority heap. It **does not guarantee FIFO order** — it always returns the element with the smallest value (natural ordering or `Comparator`). Internally it is a **min-heap** stored in a resizable array. Parent node at index `i` has children at `2i+1` and `2i+2`.
    * `offer()` adds to the end and bubbles up (sift-up): O(log n). `poll()` removes the root, moves the last element to root, and sinks down (sift-down): O(log n). `peek()` is O(1) — just read index 0.
    * **Not thread-safe.** Thread-safe alternatives:
        * `PriorityBlockingQueue` — unbounded, thread-safe, blocking `take()`. Use for producer-consumer where priority matters. Unbounded — monitor heap usage under burst load.
        * `DelayQueue` — elements become available only after their delay expires. Used for scheduling, retry-with-backoff, cache expiration.
    ```java
    // Process highest-priority ads first
    PriorityQueue<Ad> adQueue = new PriorityQueue<>(
        Comparator.comparingDouble(Ad::getBidPrice).reversed()
    );
    ```


### Multithreading & Concurrency

* [x] **Difference between process and thread**

    * When you run:
  ```bash
  java MyApp
  ```

    1. The operating system creates a **new process**
        1. This process runs an instance of the **Java Virtual Machine (JVM)**
        2. The JVM loads your `MyApp.class` and executes the `main()` method
        3. This single JVM process is your application's runtime environment

  **Inside that one JVM process:**
    - You have one **main thread** that starts automatically and executes your `main()` method
        - You can create additional **threads** within this same process
        - All these threads share the same heap memory and JVM resources

  **Example to illustrate:**

```java
public class MyApp {
    public static void main(String[] args) {
        // This runs in the main thread of the JVM process
        System.out.println("Process ID: " + ProcessHandle.current().pid());
        System.out.println("Main thread: " + Thread.currentThread().getName());
        
        // Creating additional threads within the same process
        new Thread(() -> {
            System.out.println("Same process, different thread: " + 
                             Thread.currentThread().getName());
        }).start();
    }
}
```

    Key point: One Java application = One JVM process (unless you explicitly spawn additional processes). All the threads you create in your Java code run within that single process.

---
* [x] **Explain thread lifecycle and thread states**
  > NEW → RUNNABLE → (BLOCKED / WAITING / TIMED_WAITING) → RUNNABLE → TERMINATED

  | State | Meaning | Caused by |
      |-------|---------|-----------|
  | `NEW` | Thread created, `start()` not yet called | `new Thread(task)` |
  | `RUNNABLE` | Eligible to run — may be running or waiting for CPU slot | `thread.start()` |
  | `BLOCKED` | Waiting to acquire a `synchronized` lock held by another thread | Lock contention |
  | `WAITING` | Waiting indefinitely for notification | `Object.wait()`, `LockSupport.park()` |
  | `TIMED_WAITING` | Waiting for a fixed duration | `Thread.sleep()`, `wait(timeout)` |
  | `TERMINATED` | `run()` completed or threw an uncaught exception | Method returned |

    * **Production insight:** `jstack` / thread dump shows thread states. Seeing many threads in `BLOCKED` on the same lock = lock contention bottleneck. Many `WAITING` threads = correct (waiting for work). Many `TIMED_WAITING` = timeouts/sleeps. Constant context-switching between `RUNNABLE` threads on CPU-bound work = reduce thread count to match CPU cores.

---
* [x] **What is the difference between wait(), sleep(), and yield()?****
  > `wait()` releases the lock and waits for notification, `sleep()` pauses the thread for a fixed time without
  > releasing the lock, and `yield()` only hints the scheduler to give CPU to other runnable threads without blocking.

---
* [x] **Explain synchronized keyword and its types (method level, block level)**
  **Synchronized Keyword:**

Ensures only one thread can access the synchronized code at a time.

**1. Method Level:**
```java
public synchronized void method() {
    // entire method locked
}
// Locks on: 'this' object (instance method) or Class object (static method)
```

**2. Block Level:**
```java
public void method() {
    synchronized(this) {  // or any object
        // only this block locked
    }
}
// Locks on: specified object
```

**Key Differences:**
- **Method level**: Locks entire method, less granular
- **Block level**: Locks specific section, better performance, more control over lock object

**When to use:**
- Method level: When entire method needs synchronization
- Block level: When only part of method needs synchronization (preferred for performance)
---
* [x] **What are volatile, atomic variables, and when to use them?**
  In **Java concurrency**, `volatile` and **atomic variables** are used to make shared variables safe when accessed by multiple threads.

---
1. `volatile`

`volatile` ensures **visibility** of a variable across threads.

**Meaning:**
When one thread updates the variable, **other threads immediately see the updated value**.

### Example

```java
class FlagExample {
    volatile boolean running = true;

    void stop() {
        running = false;
    }
}
```

Thread 1:

```java
while (running) {
   // do work
}
```

Thread 2:

```java
stop();
```

Without `volatile`, Thread-1 might keep using a **cached value** and never see the change.

* When to use `volatile`
    * A variable is **shared between threads**
    * Only **one thread writes**
    * Other threads **only read**
    * **No compound operations** like increment

Example:
* flags
* status indicators
* configuration values

---

2. Atomic Variables (`AtomicInteger`, `AtomicLong`, etc.)

Atomic variables provide **thread-safe operations without using locks**.

Package:

```
java.util.concurrent.atomic
```

### Example

```java
AtomicInteger counter = new AtomicInteger(0);

counter.incrementAndGet();
```

Multiple threads can safely increment the value.

### Problem with normal variable

```java
count++;
```

This is **not atomic**. It actually does:

1. read
2. add
3. write

Two threads may cause **race condition**.

---

### Atomic version

```java
AtomicInteger count = new AtomicInteger();

count.incrementAndGet();
```

This operation happens **atomically (as one step)**.

---

## When to use each

| Use Case                       | Use                           |
| ------------------------------ | ----------------------------- |
| Visibility only                | `volatile`                    |
| Thread-safe increments/updates | `AtomicInteger`, `AtomicLong` |
| Complex multi-step logic       | `synchronized` or `Lock`      |

---

### Quick Example

**volatile**

```java
volatile boolean shutdown;
```

**atomic**

```java
AtomicInteger requestCount = new AtomicInteger();
requestCount.incrementAndGet();
```

---

✅ **Summary**

* `volatile` → guarantees **visibility**
* `Atomic variables` → guarantees **atomic operations without locks**
* `synchronized/Lock` → used for **complex thread-safe logic**.

---
* [x] **Explain ThreadLocal and its use cases**

`ThreadLocal` gives each thread its own isolated copy of a variable. Threads never see each other's copy — no synchronization needed.


Each `Thread` object holds a `ThreadLocalMap` — a map of `ThreadLocal` → value. When you call `get()` or `set()`, it looks up the map on the **current thread**, not on the `ThreadLocal` object itself. The `ThreadLocal` instance is just the key.



**Real use case — request context propagation**

Store the current user once at the request boundary, read it anywhere in the call chain without passing it through every method signature.

```java
public class RequestContext {
    
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

    public static void setUser(String user) { currentUser.set(user); }
    public static String getUser()          { return currentUser.get(); }
    public static void clear()              { currentUser.remove(); }
}
```

```java
// Entry point — servlet filter or Spring interceptor
RequestContext.setUser(extractUserFromToken(request));
try {
    chain.doFilter(request, response);   // entire request runs here
} finally {
    RequestContext.clear();              // critical — thread goes back to pool
}
```

```java
// Anywhere downstream — service, repository, audit logger
public void createOrder(Order order) {
    String user = RequestContext.getUser();   // correct user, no parameter needed
    auditLog.record(user, "CREATE_ORDER", order.getId());
}
```

Each thread handles one request at a time, so `getUser()` always returns the right value even under concurrent traffic.

---

**Other common uses**
- **Per-thread expensive objects** — `SimpleDateFormat` and `Random` are not thread-safe. Rather than
  synchronizing,
  give each thread its own instance via `ThreadLocal.withInitial(...)`.
- **Transaction management** — Spring's `@Transactional` stores the active DB connection in a `ThreadLocal` so
  every repository in the same call chain shares the same connection without explicit passing.
- **MDC logging** — SLF4J's Mapped Diagnostic Context uses `ThreadLocal` to attach a trace/request ID to every log line from the current thread automatically.



**The memory leak gotcha**

In thread pools, threads are reused and never die. If you `set()` a value but forget `remove()`, the value lives on that thread forever — the next request reusing that thread picks up stale data from a previous request. Always `remove()` in a `finally` block.



**Virtual threads (Java 21)**
With millions of virtual threads, `ThreadLocal` causes memory pressure since each thread carries its own copy. Java 21 introduces `ScopedValue` as the preferred alternative — immutable, scoped to a specific call context, and garbage collected when the scope exits.

---
* [x] **Difference between Callable and Runnable**

  Both represent tasks executed by a thread, but differ in return value and exception handling.

  | | Runnable | Callable\<T> |
      |---|---|---|
  | Returns | `void` | `T` (any type) |
  | Throws checked exception | No | Yes |
  | Used with | `Thread`, `ExecutorService` | `ExecutorService` only |
  | Result retrieval | — | `Future<T>.get()` |

    ```java
    // Runnable — fire and forget
    Runnable task = () -> System.out.println("running");
    new Thread(task).start();
    
    // Callable — returns a result, can throw
    Callable<Integer> task = () -> {
        return expensiveComputation();   // can throw checked exceptions
    };
    
    Future<Integer> future = executor.submit(task);
    Integer result = future.get();       // blocks until done, throws if task failed
    ```
    
  ---

  **The practical difference**

  `Runnable` is for side-effect tasks where you don't need a result back — logging, sending an email, updating a cache.

  `Callable` is for tasks where you need the result, or where the task can legitimately fail with a checked exception and the caller needs to handle it. `future.get()` wraps any exception thrown inside the callable in an `ExecutionException` — you unwrap it with `getCause()`.

    ```java
    try {
        Integer result = future.get();
    } catch (ExecutionException e) {
        Throwable cause = e.getCause();   // the actual exception from inside the Callable
    }
    ```

  **One practical note:** if you need a `Callable` that always succeeds and returns nothing, `Callable<Void>` works — just `return null` at the end. But at that point `Runnable` is cleaner unless you specifically need the checked exception propagation.
---
* [x] **What is ExecutorService? Types of thread pools?**
    * **ExecutorService** is a high-level concurrency framework that manages a pool of worker threads and executes submitted tasks asynchronously.
    * ExecutorService es = Executors.newFixedThreadPool(3);
    * ExecutorService es = Executors.newCachedThreadPool();
    * ExecutorService es = Executors.newSingleThreadExecutor();
    * ScheduledExecutorService es =
      Executors.newScheduledThreadPool(2);
    * **newFixedThreadPool**() uses an unbounded queue, which can cause memory exhaustion under load; in production, a bounded ThreadPoolExecutor is safer.
    * Better use this:
        *
    ```
  ExecutorService es = new ThreadPoolExecutor(
  10,                     // corePoolSize
  10,                     // maximumPoolSize
  0L,                     // keepAliveTime
  TimeUnit.MILLISECONDS,  // time unit
  new ArrayBlockingQueue<>(1000),
  new ThreadPoolExecutor.CallerRunsPolicy() // If 10 threads + queue of 1000 are full, the task runs in the caller thread instead of rejecting.
  );
  
          Tasks < corePoolSize → create new thread
          Tasks ≥ corePoolSize → add to queue
          Queue full → create thread up to maxPoolSize
          All full → apply rejection policy
  ```

| Policy                | Behaviour                  |
| --------------------- | -------------------------- |
| `AbortPolicy`         | Throws exception           |
| `CallerRunsPolicy`    | Caller thread runs task    |
| `DiscardPolicy`       | New task discarded         |
| `DiscardOldestPolicy` | Oldest queued task removed |


To run **custom logic when a task is rejected**, implement **`RejectedExecutionHandler`**.

### Example

```java
class CustomRejectedHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        System.out.println("Task rejected: " + r);
        // custom logic (logging, retry, alert, etc.)
    }
}
```

Use it in `ThreadPoolExecutor`:

```java
ExecutorService executor = new ThreadPoolExecutor(
        2,
        2,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(2),
        new CustomRejectedHandler()
);
```

### Flow

1. Threads busy
2. Queue full
3. New task submitted
4. **`rejectedExecution()` method is called**

So this method acts like a **callback when `RejectedExecutionException` would normally occur**.



---

* [x] **Explain CountDownLatch, CyclicBarrier, and Semaphore**
1. `CountDownLatch`

   Used when **one or more threads wait until some tasks finish**.

   Idea: **Wait until counter becomes 0**.

   Example: wait for 3 services to start.

    ```java
    CountDownLatch latch = new CountDownLatch(3);
    
    new Thread(() -> {
        System.out.println("Service1 started");
        latch.countDown();
    }).start();
    
    latch.await(); // main thread waits until count = 0
    System.out.println("All services started");
    ```

   **Key points**

    * Counter only **decreases**
    * **Cannot be reused**

---

2. `CyclicBarrier`

   Used when **multiple threads must reach the same point before continuing**.

   Example: 3 threads must wait for each other.

   ```java
   CyclicBarrier barrier = new CyclicBarrier(3);
   
   Runnable task = () -> {
       System.out.println(Thread.currentThread().getName() + " reached barrier");
       barrier.await();
       System.out.println("All threads continue");
   };
   ```

   **Key points**

    * Threads **wait for each other**
    * **Reusable (cyclic)**

---

3. `Semaphore`

Controls **how many threads can access a resource at the same time**.

Example: only **2 threads can access DB**.

```java
Semaphore semaphore = new Semaphore(2);

semaphore.acquire();   // take permit
// access resource
semaphore.release();   // return permit
```

**Key points**

* Limits **concurrent access**
* Uses **permits**

---

### Simple difference

| Tool             | Purpose                            |
| ---------------- | ---------------------------------- |
| `CountDownLatch` | Wait until tasks finish            |
| `CyclicBarrier`  | Threads wait for each other        |
| `Semaphore`      | Limit number of concurrent threads |

---
* [x] **What are deadlock, livelock, and starvation? How to prevent them?**
    * Deadlock: threads wait on each other forever
    * Livelock: threads run but never progress
    * Starvation: thread never gets resources
---
* [x] **Explain happens-before relationship in Java Memory Model**
    * The happens-before relationship defines when one thread’s actions are guaranteed to be visible and ordered before another thread’s actions in Java.


---
* [ ] **What is a race condition? Show a concrete example and fix it.**
    * A race condition occurs when the program's output depends on the **relative timing of thread execution** — the result is non-deterministic and incorrect under concurrent access.
    * **Classic example:** `count++` appears atomic but is actually three operations — read, increment, write. Two threads can both read the same value, both increment, both write — losing one increment:
    ```java
    // ❌ Broken — two threads lose increments
    int count = 0;
    Runnable task = () -> {
        for (int i = 0; i < 100_000; i++) count++; // read-modify-write, not atomic
    };
    // Two threads running this → final count is NOT 200,000

    // ✅ Fix 1 — AtomicInteger (lock-free CAS)
    AtomicInteger count = new AtomicInteger(0);
    Runnable task = () -> { for (int i = 0; i < 100_000; i++) count.incrementAndGet(); };

    // ✅ Fix 2 — synchronized block
    int count = 0;
    Runnable task = () -> {
        for (int i = 0; i < 100_000; i++) {
            synchronized(this) { count++; }
        }
    };
    ```
    * **Detecting race conditions in production:** Run with `-ea` + stress tests under high concurrency. Use **Thread Sanitizer** (native), or **jcstress** (Java concurrency stress testing tool). Bugs like this disappear under low concurrency and appear only at scale — exactly when they do the most damage.

---
* [ ] **Explain `ReentrantLock` vs `synchronized`. When would you use `ReentrantLock` in production?**
    * `synchronized` is implicit — acquired on entry to a block, released on exit (even on exception). `ReentrantLock` is explicit — you call `lock()` and must call `unlock()` yourself (always in `finally`).

  | Feature | `synchronized` | `ReentrantLock` |
      |---------|---------------|-----------------|
  | Syntax | Implicit (block/method) | Explicit lock()/unlock() |
  | Interruptible waiting | ❌ No | ✅ `lockInterruptibly()` |
  | Timed lock attempt | ❌ No | ✅ `tryLock(timeout)` |
  | Fairness policy | ❌ No (unfair by default) | ✅ `new ReentrantLock(true)` |
  | Multiple conditions | ❌ One wait-set | ✅ Multiple `Condition` objects |
  | Debugging | Thread dump shows owner | Thread dump shows owner |

    * **Use `ReentrantLock` when:**
        * You need `tryLock(timeout)` — attempt to acquire without blocking forever (prevents deadlock under load).
        * You need `lockInterruptibly()` — cancel a waiting thread cleanly.
        * You need multiple `Condition` queues on the same lock (e.g., producer-consumer with separate "not full" and "not empty" conditions).
        * You need fairness to prevent thread starvation.
    ```java
    ReentrantLock lock = new ReentrantLock();
    try {
        if (lock.tryLock(200, TimeUnit.MILLISECONDS)) {
            try { criticalSection(); }
            finally { lock.unlock(); }
        } else {
            fallback(); // couldn't acquire — avoid blocking forever
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    ```

---
* [ ] **What is the Java Memory Model (JMM)? Explain visibility, ordering, and the happens-before rules.**
    * The JMM defines **which writes by one thread are guaranteed to be visible to reads by another thread**. Without JMM guarantees, CPU caches and compiler/JIT reordering can cause threads to see stale values.
    * **Visibility problem:** Each CPU core has its own cache. A write by Thread A may sit in Thread A's cache and never be flushed to main memory. Thread B reads from main memory — sees the old value.
    * **Ordering problem:** The compiler and JIT reorder instructions for optimization. What you write in source order may not execute in that order.
    * **Happens-Before rules** — if action A happens-before B, A's writes are guaranteed visible to B:
        1. **Monitor lock:** Unlock of a `synchronized` block happens-before any subsequent lock of the same monitor.
        2. **Volatile write:** A write to a `volatile` variable happens-before every subsequent read of that variable.
        3. **Thread start:** `Thread.start()` happens-before any action in the started thread.
        4. **Thread join:** All actions in a thread happen-before `Thread.join()` returns in the joining thread.
        5. **Transitivity:** If A HB B and B HB C, then A HB C.
    * **Practical rule:** If two threads share a variable and there's no synchronization, lock, or volatile between them — you have **no JMM guarantee**. The reader may see any value.

---
* [ ] **What is double-checked locking? Why was it broken without `volatile`, and how does it work correctly?**
    * Double-checked locking is an optimization for lazy singleton initialization — check outside the lock, check again inside, initialize once:
    ```java
    // ❌ Broken without volatile (Java < 5 behavior still illustrates the bug)
    private static Singleton instance;
    public static Singleton getInstance() {
        if (instance == null) {              // Check 1 — no lock
            synchronized (Singleton.class) {
                if (instance == null) {      // Check 2 — inside lock
                    instance = new Singleton(); // ← problem here
                }
            }
        }
        return instance;
    }
    ```
    * **Why it breaks:** `instance = new Singleton()` is NOT atomic. JVM executes it as: (1) allocate memory, (2) write reference to `instance`, (3) run constructor. Steps 2 and 3 can be **reordered by JIT**. Another thread may see a non-null `instance` reference (step 2 done) but an incompletely constructed object (step 3 not done yet) — and use it.
    * **Fix — `volatile` on the field:** `volatile` prevents the reordering. A write to a `volatile` field happens-before every subsequent read of it. The constructor call is guaranteed to complete before the reference is visible to other threads.
    ```java
    // ✅ Correct double-checked locking
    private static volatile Singleton instance;
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
    ```
    * **Better alternative:** Initialization-on-demand holder idiom — classloader guarantees thread-safe lazy initialization with no synchronization overhead:
    ```java
    public class Singleton {
        private static class Holder {
            static final Singleton INSTANCE = new Singleton(); // classloader-guaranteed
        }
        public static Singleton getInstance() { return Holder.INSTANCE; }
    }
    ```

---
* [ ] **How does `LongAdder` differ from `AtomicLong`? When should you use each?**
    * Both provide thread-safe incrementing, but they solve different performance problems:
    * **`AtomicLong`** uses a single CAS loop on one memory cell. Under low/moderate contention it's fast. Under **high contention** (many threads competing to increment the same cell), CAS failures pile up — threads spin-retry → CPU wasted on contention.
    * **`LongAdder`** maintains a **base cell + an array of `Cell` objects**, one per contending thread (lazy-created). Each thread mostly increments its own cell with low contention. `sum()` reads and sums all cells. Contention is distributed, not concentrated.
    * **`LongAdder` is faster under high contention**. `AtomicLong` is slightly faster when contention is low (no cell overhead) and provides a consistent current value (no `sum()` approximation).

  | | `AtomicLong` | `LongAdder` |
  |--|-------------|-------------|
  | Read current value | `get()` — exact | `sum()` — approximate under concurrent updates |
  | Throughput under contention | Degrades (CAS spin) | Scales linearly |
  | Use case | Compare-and-swap logic, exact reads | Counters, metrics, stats |

    * **Production rule:** Use `LongAdder` for metrics counters (request count, error count, ad impressions). Use `AtomicLong` when you need `compareAndSet()` logic or exact real-time reads.


### JVM & Memory Management

* [x] **Explain JVM architecture (Class Loader, Runtime Data Areas, Execution Engine)**
    * `.java` (source code) → `.class` (bytecode by `javac`) → ClassLoader (loads & verifies class) → Runtime Data
      Areas (heap, stack, metaspace, PC) → Execution Engine (Interpreter runs first, JIT compiles hot code) → Native CPU (executes optimized machine instructions)

---
* [x] **What are different memory areas in JVM? (Heap, Stack, Method Area, PC Register)**

![Image](https://media.geeksforgeeks.org/wp-content/uploads/20190614230114/JVM-Architecture-diagram.jpg)

![Image](https://miro.medium.com/1%2AsG2wIZg7SqyhKMKD1jxM9A.png)

**Heap**

* Memory where **objects and arrays are stored**.
* **Shared by all threads**.
* Managed by **Garbage Collector (GC)**.

**Example**

```java
User u = new User();
```

`User` object is created in **Heap**.

---

**Stack**

* Each **thread has its own stack**.
* Stores **method calls and local variables**.

**Example**

```java
void add() {
    int a = 10;
}
```

`a` and method frame are stored in the **Stack**.

---

**Method Area (Metaspace)**

* Stores **class-level information**.
* Includes:

    * class metadata
    * method definitions
    * static variables

**Example**

```java
class A {
    static int x = 10;
}
```

`x` and class structure are stored in **Metaspace**.

---

**PC Register (Program Counter)**

* Each **thread has its own PC register**.
* Stores **which instruction the thread is currently executing**.
* Helps resume execution after **context switch**.

**Example**

If a thread pauses while executing line 5, the **PC register remembers that position** so it can continue from there later.

---

### Quick Summary

| Memory      | Stores                           | Scope      |
| ----------- | -------------------------------- | ---------- |
| Heap        | Objects, arrays                  | Shared     |
| Stack       | Method calls, local variables    | Per thread |
| Metaspace   | Class metadata, static variables | Shared     |
| PC Register | Current instruction address      | Per thread |

---
* [x] **Explain Garbage Collection and types of GC (Serial, Parallel, CMS, G1, ZGC)**
  **Garbage Collection (GC):**

Automatic memory management - reclaims memory from unreachable objects.

**GC Types:**

**1. Serial GC** (`-XX:+UseSerialGC`):
- Single thread for GC
- Stops all application threads (Stop-The-World)
- Best for: Small apps, single CPU, <100MB heap
- Simple, low overhead

**2. Parallel GC** (`-XX:+UseParallelGC`):
- Multiple threads for GC
- Focus on throughput
- Best for: Multi-core systems, batch processing
- Default in Java 8

**3. CMS (Concurrent Mark Sweep)** (`-XX:+UseConcMarkSweepGC`):
- Runs concurrently with application
- Low pause times
- Best for: Applications needing low latency
- Deprecated in Java 9+, removed in Java 14

**4. G1 GC (Garbage First)** (`-XX:+UseG1GC`):
- Divides heap into regions
- Predictable pause times
- Best for: Large heaps (>4GB), balance throughput + latency
- Default from Java 9+

**5. ZGC** (`-XX:+UseZGC`):
- Ultra-low pause times (<10ms)
- Scales to multi-TB heaps
- Best for: Large heaps, latency-sensitive apps
- Production-ready from Java 15+

**Quick Comparison:**

| GC | Pause Time | Throughput | Heap Size | Use Case |
|---|---|---|---|---|
| Serial | High | Low | Small | Single CPU |
| Parallel | High | High | Medium | Batch jobs |
| CMS | Low | Medium | Medium | Low latency (deprecated) |
| G1 | Medium | Good | Large | General purpose |
| ZGC | Very Low | Good | Very Large | Ultra-low latency |

Here's the updated doc with the PermGen → Metaspace section added:

```markdown
Java heap is divided into **Young Generation** and **Old Generation**. Garbage collection works differently in each.

---

## 1. Eden Space (Object Creation)

New objects are created in **Eden**.

Example

```java
User u = new User();
```

Object first goes to **Eden memory**.

When Eden becomes **full → Minor GC runs**.

---

## 2. Minor GC (Young Generation Cleanup)

Young generation has:

* **Eden**
* **Survivor S0**
* **Survivor S1**

Process:

1. Objects created in **Eden**
2. When Eden fills → **Minor GC runs**
3. **Alive objects move to Survivor space (S0)**
4. Dead objects are **removed**

Next GC:

5. Objects move between **S0 ↔ S1**
6. Their **age increases**

Example flow

```
Eden → S0 → S1 → S0 → ...
```

---

## 3. Promotion to Old Generation

If an object **survives many Minor GCs**, it moves to **Old Generation (Tenured space)**.

These are **long-living objects**.

Example

* cache objects
* application singletons

---

## 4. Major GC (Old Generation Cleanup)

When **Old Generation becomes full**, **Major GC (Full GC)** runs.

Process:

1. GC scans old generation
2. Removes unreachable objects
3. May **compact memory**

Major GC is **slower** than Minor GC.

---

## 5. Method Area & GC (PermGen → Metaspace)

Method Area stores **class metadata, static variables, runtime constant pool**.

### PermGen (Before Java 8)

HotSpot implemented Method Area as **PermGen** — a fixed-size region inside GC-managed space.

**Class unloading conditions** (all 3 must be true):
1. All instances of the class are GC'd from heap
2. The `ClassLoader` that loaded it is no longer reachable
3. The `Class` object itself has no references

**Problems:**
- Fixed ceiling (`-XX:MaxPermSize`, default ~64–256MB) — had to predict upfront
- Class unloading only happened during **Full GC** — not minor GC
- Hot-reload frameworks (Tomcat redeploy, JRebel) constantly created new ClassLoaders → old class metadata accumulated → `OutOfMemoryError: PermGen space`
- Bumping `-XX:MaxPermSize` delayed the problem but didn't fix it — you were still guessing a static number

**GC behavior in PermGen vs Heap:**

| | Heap | PermGen (Method Area) |
|---|---|---|
| What gets collected | Dead object instances | Dead/unloaded classes |
| Frequency | Minor GC constantly, Full GC periodically | **Only during Full GC** |
| Cost | Minor GC is fast | Expensive — full heap scan required |

### Metaspace (Java 8+)

PermGen was replaced with **Metaspace** — still the Method Area implementation, just backed by **native OS memory**.

**Key differences:**

| | PermGen | Metaspace |
|---|---|---|
| Memory | GC-managed heap | Native OS memory |
| Size | Fixed ceiling | Grows dynamically |
| Class unloading | Only on Full GC | **Immediately when ClassLoader dies** |
| OOM risk | High in dynamic apps | Much lower |
| Cap | Forced (`MaxPermSize`) | Opt-in (`-XX:MaxMetaspaceSize`) |

**Bottom line:** The fix wasn't "make PermGen bigger" — it was "stop managing class metadata like heap objects." Metaspace frees class metadata as soon as the ClassLoader is gone, without waiting for Full GC.

---

## Simple Flow

```
New Object
   ↓
Eden
   ↓ (Minor GC)
Survivor S0 / S1
   ↓ (after multiple GC cycles)
Old Generation
   ↓ (Major GC)
Cleanup

Class Loading
   ↓
Method Area (Metaspace in Java 8+)
   ↓ (ClassLoader unreachable)
Immediate cleanup — no GC needed
```

---

## Quick Summary

| Memory Area         | Purpose                                      |
|---------------------|----------------------------------------------|
| Eden                | New objects created                          |
| Survivor (S0/S1)    | Short-lived surviving objects                |
| Old Generation      | Long-lived objects                           |
| Method Area/PermGen | Class metadata, static vars, constant pool (pre Java 8) |
| Metaspace           | Same as Method Area, native memory (Java 8+) |
| Minor GC            | Cleans Young Generation                      |
| Major GC            | Cleans Old Generation + PermGen (pre Java 8) |


---
* [x] **How would you identify and fix memory leaks?**
    * In Kubernetes, memory leaks are identified via **Prometheus/Grafana JVM metrics** — if **heap usage after GC keeps increasing and pods get OOMKilled**, it indicates a leak. Root cause is found using **JFR or async-profiler**, not by running `jmap` on live pods (heap dumps cause STW pauses that make the problem worse).
    * **Step-by-step production diagnosis:**
        1. **Confirm the leak:** Graph `jvm_memory_used_bytes{area="heap"}` after each GC. A leak shows a **sawtooth pattern with rising troughs** — heap never fully reclaims after GC.
        2. **Capture a heap dump:** Enable `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/` so the dump is automatic on OOM. Or trigger manually with `jcmd <pid> GC.heap_dump /tmp/dump.hprof` during elevated memory (not at OOM — process may die first).
        3. **Analyze with Eclipse MAT or JDK Mission Control:** Look for the "Dominator Tree" — the objects retaining the most memory. Common suspects: `ThreadLocal` not cleaned up, static `List`/`Map` growing unbounded, event listeners not deregistered, Hibernate session caches held too long.
        4. **Common root causes:**
            * `ThreadLocal` values not `remove()`d in thread pools — values accumulate per thread forever.
            * Static collections (caches, registries) without eviction.
            * `ClassLoader` leaks in hot-reload scenarios (Tomcat redeploy) — class metadata stays in Metaspace.
            * Closures capturing large objects inadvertently.
        5. **Fix and validate:** After the fix, monitor the GC trough baseline for 24h+ under production load — it must stabilize, not grow.

---
* [ ] **What are common causes of `OutOfMemoryError` and how do you distinguish between them?**
    * `OutOfMemoryError` has several distinct subtypes — each points to a different root cause:

  | OOM Message | Cause | Fix |
      |-------------|-------|-----|
  | `Java heap space` | Objects filling old gen, GC can't reclaim fast enough | Heap leak fix, increase `-Xmx`, reduce allocation rate |
  | `GC overhead limit exceeded` | GC spending >98% of time but reclaiming <2% heap | Same as above — heap is exhausted |
  | `Metaspace` | Too many classloaders / dynamic class generation | Increase `-XX:MaxMetaspaceSize`, fix classloader leaks |
  | `unable to create new native thread` | OS thread limit hit (usually `ulimit -u`) | Reduce thread count, use virtual threads, increase OS limit |
  | `Direct buffer memory` | Off-heap `ByteBuffer.allocateDirect()` exhausted | Increase `-XX:MaxDirectMemorySize`, ensure buffers are released |

    * **Production tip:** `-XX:+ExitOnOutOfMemoryError` — kills the JVM immediately on OOM instead of running as a zombie process in an inconsistent state. On Kubernetes, the pod restarts cleanly. Always combine with `-XX:+HeapDumpOnOutOfMemoryError` for post-mortem analysis.

---
* [x] **Explain JVM tuning parameters you've used in production**
    * In production (Java 17, Kubernetes), I tune JVM mainly for **GC latency and container awareness**: I set **heap sizing via `-Xms/-Xmx` aligned to pod limits**, use **G1GC (default) or ZGC for low latency**, tune **pause goals (`-XX:MaxGCPauseMillis`)**, control **Metaspace (`-XX:MaxMetaspaceSize`)**, enable **GC logs and JFR**, and rely on **Prometheus/Grafana metrics** to validate post-GC heap stability and pause times.”

---
* [x] **What is the difference between stack and heap memory?**
    * **Stack** stores method calls and local variables and is **thread-local and fast**, while **Heap** stores objects and is **shared across threads and managed by the Garbage Collector**.

---
* [x] **Explain PermGen vs Metaspace (Java 8+)**
    * PermGen (pre-Java 8) stored class metadata in a fixed-size, separate GC-managed region, often causing OutOfMemoryError. Metaspace (Java 8+) stores the same metadata in native OS memory, grows dynamically, and is more stable in production.

### Exception Handling

* [x] **Difference between checked and unchecked exceptions**
    * **Checked exceptions** are compile-time enforced — the compiler requires you to either `catch` them or declare them with `throws`. They represent **recoverable, anticipated failures** (file not found, network timeout). Examples: `IOException`, `SQLException`, `ClassNotFoundException`.
    * **Unchecked exceptions** (`RuntimeException` and its subclasses) represent **programming errors or unrecoverable conditions** — the compiler does not require handling. Examples: `NullPointerException`, `IllegalArgumentException`, `IndexOutOfBoundsException`.
    * **The design debate (important at senior level):** Many modern Java codebases (and frameworks like Spring) prefer unchecked exceptions everywhere. Checked exceptions pollute method signatures, make lambdas verbose (can't throw checked from `Function`/`Stream`), and often just get swallowed in `catch(Exception e) {}` blocks — providing false safety. Use checked exceptions only when the caller has a **realistic recovery path** (e.g., retry on `IOException`).
    * **`Error`** (e.g., `OutOfMemoryError`, `StackOverflowError`) is a third category — severe JVM-level failures. Never catch `Error` unless you're writing a framework that needs to log before dying. Never catch `Throwable` in normal business code.

---
* [x] **When to use throw vs throws?**
    * Use **`throw`** to **explicitly create and raise an exception inside a method**, while **`throws`** is used in a **method signature to declare exceptions that the method may propagate to the caller**.

---
* [x] **Best practices for exception handling in microservices**
    * Use **global exception handlers** to return **consistent error responses** (HTTP status + error code).
    * **Do not expose internal exceptions**; log detailed errors internally and return sanitized messages.
    * Use **checked exceptions for recoverable cases**, unchecked for programming errors.
    * **Fail fast**, use **timeouts, retries, and circuit breakers**, and correlate errors using **trace IDs** in logs.

---
* [x] **How do you handle exceptions in multithreaded applications?**
    * Thread → catch inside run() or use UncaughtExceptionHandler
    * ExecutorService → exceptions captured in Future.get()
    * Callable →  supports exception propagation
    * Production rule → always use thread pools + Futures, never raw threads


---
* [ ] **What is try-with-resources? How does it work internally?**
    * `try-with-resources` (Java 7+) automatically closes resources that implement `AutoCloseable` after the `try` block exits — whether normally or via exception. Eliminates the verbose and error-prone `finally { if(r != null) r.close(); }` pattern.
    ```java
    // ✅ Clean — connection always closed even if exception thrown
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, userId);
        return ps.executeQuery();
    }
    // Both ps and conn are closed in reverse declaration order automatically
    ```
    * **Internally:** The compiler generates the `finally` block for you. It also handles **suppressed exceptions**: if both the `try` body AND `close()` throw, the `close()` exception is added as a **suppressed exception** on the primary exception (accessible via `e.getSuppressed()`). Without try-with-resources, the `finally` exception would silently replace the original exception — losing the real cause.
    * **Production rule:** Every `Connection`, `InputStream`, `Session`, `HttpClient` — anything with a `close()` method — must be in a try-with-resources. Resource leaks under load (connection pool exhaustion) are among the most common production outages.

---
* [ ] **What is exception chaining and why does it matter in production?**
    * Exception chaining preserves the original cause when you catch an exception and rethrow a different one. Without it, you lose the root cause stack trace — debugging production failures becomes impossible.
    ```java
    // ❌ Bad — original cause lost
    try {
        userRepo.findById(id);
    } catch (SQLException e) {
        throw new ServiceException("User lookup failed"); // e is gone
    }

    // ✅ Good — original cause preserved as the "cause"
    try {
        userRepo.findById(id);
    } catch (SQLException e) {
        throw new ServiceException("User lookup failed", e); // e attached
    }
    ```
    * When you log `e.getCause()` or print the stack trace, the full chain is visible — from the business exception back to the `SQLException` back to the JDBC driver error. Without chaining, your `ServiceException` stack trace starts at the rethrow point and you have no idea why it failed.
    * **In microservices:** Combine exception chaining with correlation IDs in logs. When a downstream call fails, chain the downstream exception and include the trace ID in the exception message. Operations can then follow the failure chain across service boundaries.

### Java 8+ Features

* [x] **Explain Streams API and intermediate vs terminal operations**
    * Streams API enables functional-style, declarative processing of collections (filter, map, reduce) without modifying the source data. Streams are **lazy** — no processing happens until a terminal operation is called.
    * **Intermediate Operations** — lazy, return a new `Stream`, can be chained: `filter`, `map`, `flatMap`, `sorted`, `distinct`, `limit`, `skip`, `peek`.
    * **Terminal Operations** — trigger the pipeline, produce a result, close the stream (cannot reuse): `forEach`, `collect`, `reduce`, `count`, `findFirst`, `anyMatch`, `toList` (Java 16+).
    * **Short-circuit operations:** `findFirst()`, `anyMatch()`, `limit()` — stop processing as soon as the answer is known. A `filter().findFirst()` on a 1M-element stream may process only 1 element.
    * **Common production mistakes:**
        1. **Calling `stream()` inside a loop** — creates a new stream object per iteration. Build the stream once outside.
        2. **Using `peek()` for logic** — `peek()` is for debugging/logging only. It may not execute on all elements in short-circuit pipelines. Never put business logic in `peek()`.
        3. **Collecting to a list then streaming again** — breaks the lazy pipeline unnecessarily, forces full materialization mid-chain.
        4. **Parallel streams on small collections** — parallelism overhead (fork/join thread management) exceeds savings. Use parallel streams only for CPU-bound, large-dataset operations where the workload per element is significant.
    ```java
    // Parallel stream — correct use (large CPU-bound aggregation)
    double avgBidPrice = adInventory.parallelStream()
        .filter(ad -> ad.isActive())
        .mapToDouble(Ad::getBidPrice)
        .average()
        .orElse(0.0);
    // Wrong use: parallelStream() on a 10-element list — overhead exceeds benefit
    ```


---
* [x] **What are functional interfaces? Give examples**
    * A functional interface is an interface with exactly one abstract method, enabling lambda expressions and method references.
    * | Interface       | Abstract Method     | Use case         |
                | --------------- | ------------------- | ---------------- |
      | `Runnable`      | `void run()`        | Execute task     |
      | `Callable<T>`   | `T call()`          | Task with return |
      | `Function<T,R>` | `R apply(T t)`      | Transform data   |
      | `Predicate<T>`  | `boolean test(T t)` | Condition check  |
      | `Consumer<T>`   | `void accept(T t)`  | Consume data     |
      | `Supplier<T>`   | `T get()`           | Lazy creation    |

---
* [x] **Difference between map() and flatMap()**
    * `map()` transforms each element 1-to-1 — one input, one output. `flatMap()` transforms each element 1-to-many and **flattens** the resulting streams into one — one input, zero or more outputs merged into a single stream.
    ```java
    // map — wraps in Optional, result is Stream<Optional<User>>
    Stream<Optional<User>> = ids.stream().map(id -> userRepo.findById(id));

    // flatMap — unwraps Optional, result is Stream<User> (absent ones skipped)
    Stream<User> = ids.stream().flatMap(id -> userRepo.findById(id).stream());

    // flatMap on nested collections
    List<Order> orders = customers.stream()
        .flatMap(customer -> customer.getOrders().stream()) // flatten List<List<Order>>
        .collect(toList());
    ```
    * **`Optional.flatMap()` vs `Optional.map()`:** Same distinction — `map()` wraps the result in another `Optional`, `flatMap()` expects the function to return an `Optional` and flattens the nesting.
---
* [x] **Explain Optional and why it's useful**
    * `Optional<T>` is a container that either holds a non-null value or is empty. It makes the possibility of absence **explicit in the API contract** — callers cannot ignore it as easily as they ignore a `null` return value.
    ```java
    // ❌ Nullable return — caller may forget to null-check
    User findUser(long id);

    // ✅ Optional — absence is explicit in the signature
    Optional<User> findUser(long id);

    // Consuming Optional correctly
    findUser(id)
        .map(User::getEmail)                    // safe transform — skipped if empty
        .filter(email -> email.contains("@"))   // safe filter
        .ifPresent(email -> send(email));       // only runs if value present

    // With fallback
    User user = findUser(id).orElseGet(() -> User.guest());
    ```
    * **What NOT to do with Optional:**
        * **Don't use `Optional.get()` without `isPresent()` check** — throws `NoSuchElementException`, same as NPE. Use `orElse()`, `orElseGet()`, or `orElseThrow()`.
        * **Don't use `Optional` as a method parameter** — it's designed for return types. Use method overloading or `@Nullable` annotations instead.
        * **Don't use `Optional` for collections** — return an empty collection instead of `Optional<List<T>>`.
        * **Don't serialize `Optional`** — it's not `Serializable` by design.
---
* [x] **What are method references and lambda expressions?**
    * A **lambda expression** is an anonymous function used to implement a functional interface.
        * (a, b) -> a + b
    * A **method reference** is a shorter form of a lambda that only calls an existing method.
        * Static method
            * nums.forEach(System.out::println);
        * Instance method (object)
            * String prefix = "Hi ";
              nums.forEach(prefix::concat);
        * Instance method (class)
            * List.of("a", "bb").stream().map(String::length).toList();



---
* [x] **Explain default and static methods in interfaces (Java 8)**
    * **Default Methods**

A method with a body inside an interface.

```java
interface Vehicle {
    default void start() {
        System.out.println("Starting...");
    }
}
```

**Rules:**
- Implementing class **inherits** it automatically
- Can be **overridden** if needed
- **Class always wins** if class and interface define the same method — avoids ambiguity

**Real usage:** `Iterable.forEach()`, `Collection.stream()`, `List.sort()` — all added as default methods in Java 8.



* **Static Methods in Interfaces**

Belongs to the **interface itself**, not to any instance or implementing class.

```java
interface MathOps {
    static int square(int x) { return x * x; }
}

// Call as:
MathOps.square(5);  // not via instance
```

**Why useful:** Utility logic that naturally belongs to the interface lives right there — no need for a separate `Collections`-style helper class just to hold related utilities.

* **Why Both Were Added in Java 8**

Java needed to add `stream()`, `forEach()`, `sort()` etc. to core interfaces like `Collection`, `List`, `Iterable`.

Problem: Millions of existing classes already implemented these interfaces. Adding a new **abstract** method would break all of them — they'd all fail to compile.

**Default methods solved this** — existing implementations inherit the new method automatically, nothing breaks. This is the primary motivation: **evolve core interfaces without breaking existing code.**

---

---
* [ ] **What is `Collectors.groupingBy()` and how do you use downstream collectors?**
    * `groupingBy()` partitions stream elements into groups by a classifier function. The result is a `Map<K, List<T>>` by default. With a downstream collector, the value type can be anything:
    ```java
    // Basic grouping
    Map<String, List<Order>> byStatus = orders.stream()
        .collect(Collectors.groupingBy(Order::getStatus));

    // With downstream collector — count per status
    Map<String, Long> countByStatus = orders.stream()
        .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

    // With downstream — sum revenue per tenant
    Map<String, Double> revenueByTenant = orders.stream()
        .collect(Collectors.groupingBy(
            Order::getTenantId,
            Collectors.summingDouble(Order::getAmount)
        ));

    // Multi-level grouping — by tenant then by status
    Map<String, Map<String, List<Order>>> byTenantThenStatus = orders.stream()
        .collect(Collectors.groupingBy(Order::getTenantId,
                 Collectors.groupingBy(Order::getStatus)));
    ```
    * **`partitioningBy(predicate)`** — special case of `groupingBy` that produces exactly two groups: `true` and `false`. More efficient than `groupingBy` with a boolean classifier.

---
* [ ] **What are the key additions in Java 11, Java 17, and Java 21 that matter for backend development?**
    * **Java 11 (LTS):** `String` utility methods (`isBlank()`, `strip()`, `lines()`, `repeat()`). `HttpClient` (non-blocking, HTTP/2). `var` in lambda parameters. `Files.readString()` / `writeString()`. Removal of EE modules from JDK (no more bundled JAXB, Corba).
    * **Java 17 (LTS):**
        * **Records** — immutable data carriers with auto-generated boilerplate. Use for DTOs, value objects, query results.
        * **Sealed classes** — restrict class hierarchies for exhaustive pattern matching. Use for domain event types, result types, state machines.
        * **Pattern matching for `instanceof`** — eliminates the manual cast: `if (obj instanceof String s) { s.toUpperCase(); }`.
        * **Switch expressions** — arrow syntax, exhaustiveness checking, returns a value. Replaces verbose `if-else` chains.
        * **Text blocks** — multi-line strings for SQL, JSON, HTML without escaping. Cleaner test fixtures.
        * **Strong encapsulation of JDK internals** — `--illegal-access` removed. Libraries that used reflection on private JDK fields now break.
    * **Java 21 (LTS):**
        * **Virtual threads (stable)** — JVM-managed, heap-allocated threads. Create millions without OS thread limits. Blocking I/O no longer blocks carrier threads. Write synchronous code, get async throughput.
        * **Sequenced collections** — `SequencedCollection`, `SequencedMap` interfaces with `getFirst()`, `getLast()`, `reversed()` across all ordered collections.
        * **Pattern matching for switch (stable)** — fully exhaustive switch over sealed types, including null handling.
        * **Record patterns** — destructure records directly in pattern matching: `case Order(var id, var amount) when amount > 1000`.
        * **Structured concurrency (preview)** — parent-child task lifecycle management; subtask failure auto-cancels siblings.

* [x] **What's new in Java 17 that you've used?**
    * In Java 17, I actively use Records for DTOs, sealed classes for domain modeling, pattern matching and switch expressions for cleaner logic, text blocks for SQL/JSON, and benefit from GC and JVM encapsulation improvements.

---

* [x] **How would you design a producer-consumer system in Java?**

```java
// BlockingQueue decouples producers from consumers
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(1000);
 
// Producer
ExecutorService producers = Executors.newFixedThreadPool(4);
producers.submit(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        Task task = fetchNextTask();
        queue.put(task);           // blocks when full — backpressure built-in
    }
});
 
// Consumer
ExecutorService consumers = Executors.newFixedThreadPool(8);
consumers.submit(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        Task task = queue.poll(1, TimeUnit.SECONDS);
        if (task != null) process(task);
    }
});
```

**Key decisions a TL is expected to articulate:**
- `ArrayBlockingQueue` (bounded, backpressure) vs `LinkedBlockingQueue` (unbounded, OOM risk under load)
- Number of consumer threads = I/O bound → more threads OK; CPU bound → match core count
- Poison pill pattern for graceful shutdown: enqueue a sentinel `STOP` task per consumer thread
- For high throughput, evaluate `Disruptor` (LMAX) — lock-free ring buffer, avoids `BlockingQueue` contention

---
## CompletableFuture

### The Problem It Solves

Before CompletableFuture, if you wanted to run something in background and then do something with the result, you had to manually manage threads — messy and error-prone.

CompletableFuture lets you say: **"do this, then do that, then do that — all in background, and tell me if anything breaks."**

---

### Building Blocks

**`supplyAsync`** — start an async task that returns a value
```java
CompletableFuture<User> future = CompletableFuture
    .supplyAsync(() -> fetchUser(userId), executor);
```
Runs `fetchUser` in background on the given executor.

---

**`thenApply`** — transform the result (like `map`)
```java
.thenApply(user -> buildOrder(user))
```
Once `fetchUser` completes, take the `User` and build an `Order`. Runs synchronously on the same thread that completed the previous step.

---

**`thenCompose`** — chain another async task (like `flatMap`)
```java
.thenCompose(order -> saveOrderAsync(order))
```
Use this when the next step is itself async and returns a `CompletableFuture`. If you used `thenApply` here, you'd get a `CompletableFuture<CompletableFuture<Order>>` — nested and ugly. `thenCompose` flattens it.

---

**`exceptionally`** — recover when something goes wrong
```java
.exceptionally(ex -> {
    log.error("Order failed", ex);
    return fallbackOrder();
})
```
If any step in the chain throws, execution jumps here. You return a fallback value and the chain continues normally.

---

### Full Chain Together

```java
CompletableFuture<Order> future = CompletableFuture
    .supplyAsync(() -> fetchUser(userId), executor)   // step 1: fetch user async
    .thenApply(user -> buildOrder(user))              // step 2: build order (sync transform)
    .thenCompose(order -> saveOrderAsync(order))      // step 3: save async
    .exceptionally(ex -> {                            // if anything above fails
        log.error("Order failed", ex);
        return fallbackOrder();
    });
```

Think of it as a pipeline — each step hands its result to the next.

---

### Running Independent Tasks in Parallel

Sometimes two tasks don't depend on each other. Run both simultaneously, wait for both, then combine.

```java
CompletableFuture<UserProfile> profileFuture = fetchProfileAsync(userId);
CompletableFuture<List<Order>> ordersFuture  = fetchOrdersAsync(userId);

CompletableFuture.allOf(profileFuture, ordersFuture)
    .thenRun(() -> {
        UserProfile profile = profileFuture.join();
        List<Order> orders  = ordersFuture.join();
        render(profile, orders);
    });
```

`allOf` waits for all futures to complete. `.join()` here is safe because by the time `thenRun` executes, both futures are already done — no blocking.

---

### Common Pitfalls

**`thenApply` vs `thenApplyAsync`**

`thenApply` runs the callback on whichever thread completed the previous step. If that's a ForkJoinPool thread and your callback is slow, you're blocking a shared pool thread.

`thenApplyAsync` hands the callback off to the executor — keeps the completing thread free.

```java
.thenApply(x -> heavyWork(x))       // runs on completing thread — risky
.thenApplyAsync(x -> heavyWork(x), executor)  // hands off — safe
```

---

**`.join()` inside a chain = deadlock**

```java
// NEVER do this
.thenApply(x -> someOtherFuture.join())  // blocks the ForkJoinPool thread
                                          // other tasks can't run → deadlock
```

Use `thenCompose` instead when the next step is async.

---

**Always pass an explicit Executor for I/O**

```java
// Bad — I/O tasks on ForkJoinPool commonPool, which is sized for CPU work
CompletableFuture.supplyAsync(() -> callDatabase());

// Good
CompletableFuture.supplyAsync(() -> callDatabase(), ioThreadPoolExecutor);
```

ForkJoinPool is designed for CPU-bound tasks. Blocking I/O on it starves other tasks.

---

**`handle()` vs `exceptionally()`**

```java
.exceptionally(ex -> fallback())           // only called on failure, for recovery

.handle((result, ex) -> {                  // always called — inspect both
    if (ex != null) return fallback();
    return transform(result);
})
```

Use `handle` when you need to inspect both the result and the exception in one place.

---
* [x] **StampedLock vs ReentrantReadWriteLock — when and why?**
    - **ReentrantReadWriteLock**: Multiple concurrent readers, one exclusive writer. Good when reads heavily outnumber writes. Writers can starve under high read load.
    - **StampedLock** (Java 8+): Adds **optimistic reads** — read without acquiring a lock, then validate the stamp. If the stamp is invalid (a write happened), fall back to a full read lock. Much higher throughput for read-dominant workloads.
    - **StampedLock is not reentrant** — a thread cannot acquire it again while holding it. This is the #1 footgun. Never use it in recursive call chains.
    - **Use StampedLock** when: read-heavy, lock held briefly, no recursion.

```java
StampedLock lock = new StampedLock();
 
// Optimistic read — no lock acquired
long stamp = lock.tryOptimisticRead();
double x = this.x, y = this.y;
if (!lock.validate(stamp)) {             // check if a write raced us
    stamp = lock.readLock();             // fall back to real read lock
    try { x = this.x; y = this.y; }
    finally { lock.unlockRead(stamp); }
}
```
 
---
* [x] **What is the actor model and when would you use it over threads?**
    - Actors are lightweight concurrent entities that communicate only via **message passing** — no shared mutable state, so no locks.
    - Each actor processes one message at a time from its mailbox, maintaining its own private state.
    - **Use when**: you have many long-lived, stateful concurrent entities (e.g., per-user session state, game entities, IoT device state machines) where shared-memory locking becomes unmanageable.
    - **Java options**: Akka (full actor framework), or Java 21 virtual threads + channels as a lighter alternative.
    - **Don't reach for actors** when standard `ExecutorService` + `CompletableFuture` is sufficient. Actors add significant conceptual overhead.

---
* [x] **Explain virtual threads (Project Loom) and structured concurrency (Java 21)**

  **Virtual threads:**
    - Lightweight threads managed by the JVM, not the OS. You can create **millions** of them without exhausting OS thread limits.
    - Blocking a virtual thread (e.g., waiting on I/O) does not block the underlying OS carrier thread — the JVM parks the virtual thread and reuses the carrier.
    - **Key consequence**: You no longer need reactive/async programming purely to avoid blocking. Write synchronous code; get the concurrency benefits of async.
    - `Thread.ofVirtual().start(task)` or `Executors.newVirtualThreadPerTaskExecutor()`

  **What changes for a TL:**
    - Thread pools are no longer the primary tool for I/O-bound workloads — virtual thread per task is simpler.
    - `ThreadLocal` is safe but can cause memory bloat with millions of virtual threads. Prefer `ScopedValue` (preview in Java 21).
    - CPU-bound tasks still need platform threads (virtual threads don't give you more CPU cores).
    - Libraries that use `synchronized` internally (JDBC drivers, some Netty paths) can pin virtual threads to their carrier — monitor with JFR event `jdk.VirtualThreadPinned`.

  **Structured concurrency (Java 21 preview):**
    - Enforces a parent-child relationship between concurrent tasks. If a subtask fails, siblings are cancelled automatically. If the parent scope exits, all subtasks are done.
    - Eliminates the class of bugs where a fire-and-forget task outlives its logical context.

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<User>  user  = scope.fork(() -> fetchUser(id));
    Future<Order> order = scope.fork(() -> fetchOrder(id));
    scope.join().throwIfFailed();
    return new Dashboard(user.resultNow(), order.resultNow());
}
// If either subtask throws, the other is cancelled automatically
```
 
---


### Performance Profiling & JVM Deep Dives

* [x] **Walk me through diagnosing a latency spike in production**
    1. **Correlate first**: Check GC logs / JFR — is the latency spike at the same time as a Full GC or long GC pause? If yes, the fix is GC tuning (heap size, GC algorithm, allocation rate).
    2. **Thread state**: take a thread dump (`jstack` or `kill -3`) during the spike. Are threads `BLOCKED`? On what lock? This points to lock contention.
    3. **CPU profile**: use async-profiler (`-e cpu`) attached to the running JVM. Flame graph shows where CPU time is actually spent — often reveals unexpected serialisation, regex, or reflection overhead.
    4. **Allocation profile**: async-profiler `-e alloc`. High allocation rate → frequent GC → latency. Find what's allocating (often string concatenation in hot paths, or unnecessary object creation in mappers).
    5. **I/O and external calls**: if the above are clear, the bottleneck is usually a downstream service. Check connection pool saturation (`HikariPool` metrics), slow queries (slow query log), or missing timeouts causing thread pile-up.
    6. **JFR continuous recording**: in production, always have JFR running with low overhead settings. You can retrieve a recording after the fact and inspect it in JDK Mission Control.

---
* [x] **What JVM flags do you audit before a service goes to production?**

```bash
# Container-aware heap sizing (Java 11+)
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0          # leave headroom for off-heap, Metaspace, stack
 
# GC — G1 for most services, ZGC for latency-sensitive
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
 
# Prevent Metaspace growth surprises
-XX:MaxMetaspaceSize=256m
 
# Startup: skip class verification for known-good JARs
-XX:TieredStopAtLevel=1            # use only for fast startup (Lambda) — disables JIT
 
# Observability — always on in production
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=5,filesize=20m
-XX:StartFlightRecording=settings=default,filename=/tmp/recording.jfr,dumponexit=true
 
# OOM diagnostics
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof
-XX:+ExitOnOutOfMemoryError        # fail fast — zombie processes are worse than restarts
```

**Flag you should remove from old configs:**
- `-XX:+UseConcMarkSweepGC` — removed in Java 14
- `-XX:PermSize` / `-XX:MaxPermSize` — PermGen is gone since Java 8

---

### Code Review & Technical Leadership

* [x] **What do you look for in a PR beyond correctness?**
    - **Readability first**: code is read far more than it is written. Variable names, method length, comment quality (why, not what).
    - **Test quality**: are tests testing behaviour or implementation? Brittle tests (mocking private methods, testing internal state) are worse than no tests — they resist refactoring.
    - **Error handling**: what happens on the unhappy path? Are exceptions handled at the right level, or swallowed silently?
    - **Concurrency**: is shared mutable state properly guarded? Are thread-pool sizes hardcoded or configurable?
    - **Observability**: does new code emit metrics, structured logs with trace IDs, and meaningful health indicators?
    - **Security**: is user input validated? Are secrets hardcoded? Are SQL queries parameterised?
    - **Backward compatibility**: for API changes, is the contract additive or breaking? Is there a migration path?

---
* [x] **How do you enforce architectural boundaries in a large Java codebase?**
    - **ArchUnit** — write architecture tests that run in CI:
    ```java
    @AnalyzeClasses(packages = "com.myapp")
    class ArchitectureTest {
        @ArchTest
        ArchRule layerRule = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");
    }
    ```

    - **Package-private visibility**: classes that shouldn't leave a module are `package-private` by default, not `public`. Access modifier is your first enforcement layer.
    - **JPMS module boundaries**: in greenfield code, `module-info.java` enforces at compiler level what ArchUnit enforces at test level.
    - **ADRs (Architecture Decision Records)**: document decisions and their rationale in the repo. New team members understand *why* a boundary exists, not just that it does.

---
* [x] **How do you handle a situation where a senior engineer on your team disagrees with your technical decision?**
    - First, assume they might be right. Ask them to walk you through their concern — often they have context you're missing.
    - Separate **preference** from **principle**. Disagreements on code style or library choice should go to team standards and be resolved once, not relitigated per PR. Disagreements on architecture or correctness warrant a proper design discussion.
    - Use **decision criteria agreed upfront**: performance benchmarks, operational complexity, onboarding cost. When the decision is framed against criteria both parties accept, it becomes less personal.
    - If still unresolved: **time-box an experiment** (spike), or escalate to a design review with broader team input. Avoid "because I said so" — it destroys trust and you lose the signal that the disagreement carries.
    - **Document the outcome** in an ADR so the decision isn't revisited at every subsequent PR.

---

### Design Patterns in Java (Product Company Favourites)

* [ ] **Explain the Singleton pattern. What are all the ways to implement it safely in Java?**
    * Singleton ensures only one instance of a class exists in the JVM. There are 4 ways to implement it, each with different thread-safety and lazy-init tradeoffs:
    * **1. Eager initialization (simplest, always correct):**
    ```java
    public class Singleton {
        private static final Singleton INSTANCE = new Singleton(); // created at class load
        private Singleton() {}
        public static Singleton getInstance() { return INSTANCE; }
    }
    ```
    * **2. Initialization-on-demand holder (lazy, thread-safe, no synchronization overhead — recommended):**
    ```java
    public class Singleton {
        private static class Holder {
            static final Singleton INSTANCE = new Singleton(); // lazy — loaded when Holder is first accessed
        }
        private Singleton() {}
        public static Singleton getInstance() { return Holder.INSTANCE; }
    }
    // Classloader guarantees single initialization — no synchronized keyword needed
    ```
    * **3. Double-checked locking with `volatile`:** (covered in multithreading section — works but more complex than holder idiom)
    * **4. Enum singleton (serialization-safe, reflection-proof — Effective Java recommendation):**
    ```java
    public enum Singleton {
        INSTANCE;
        public void doSomething() { ... }
    }
    // Enum guarantees single instance, even through serialization and reflection attacks
    ```
    * **Testability problem:** Singletons make unit testing hard — you can't easily swap the instance with a mock. In Spring Boot, prefer Spring-managed singletons (`@Bean` scope = singleton by default) over manual Singleton pattern — Spring's DI makes them injectable and mockable.

---
* [ ] **Explain the Builder pattern. When is it essential vs overkill?**
    * Builder separates object construction from its representation — used when an object has many optional parameters, making constructors with many arguments (telescoping constructors) unreadable and error-prone.
    ```java
    // ❌ Telescoping constructor — positional args, easy to swap two params silently
    new AdConfig("banner", 300, 250, true, false, "jpg", null, null);

    // ✅ Builder — self-documenting, validation in build(), immutable result
    AdConfig config = AdConfig.builder()
        .format("banner")
        .width(300)
        .height(250)
        .trackable(true)
        .mimeType("jpg")
        .build(); // validate all required fields here
    ```
    * **Lombok `@Builder`** generates the builder automatically — use it for DTOs and domain objects.
    * **Essential when:** ≥4 parameters, many optional fields, immutable object desired, want validation at construction time.
    * **Overkill when:** 2–3 parameters, all required, simple data class — use a plain constructor or record.

---
* [ ] **Explain the Strategy, Observer, and Factory patterns with real production examples.**
    * **Strategy** — defines a family of algorithms, encapsulates each one, makes them interchangeable. Eliminates `if-else`/`switch` on type.
    ```java
    // Ad pricing strategy — different algorithms per customer tier
    interface PricingStrategy {
        double calculate(Ad ad, Context ctx);
    }
    class CpmPricing implements PricingStrategy { ... }
    class CpcPricing implements PricingStrategy { ... }
    class FlatRatePricing implements PricingStrategy { ... }

    class AdPricer {
        private final PricingStrategy strategy;
        AdPricer(PricingStrategy strategy) { this.strategy = strategy; }
        double price(Ad ad, Context ctx) { return strategy.calculate(ad, ctx); }
    }
    // Add new pricing model: write a new class, no changes to AdPricer or callers — Open/Closed Principle
    ```
    * **Observer** — subject notifies multiple observers when its state changes. Used in event systems, reactive streams, Spring's `ApplicationEvent`.
    ```java
    // Decoupled post-order processing — order service doesn't know about inventory, email, analytics
    @Component
    public class OrderPlacedEventHandler {
        @EventListener
        public void onOrderPlaced(OrderPlacedEvent event) {
            inventoryService.reserve(event.getItems());
            emailService.sendConfirmation(event.getCustomerId());
            analyticsService.record(event);
        }
    }
    ```
    * **Factory** — delegates object creation to a factory method or class, hiding the concrete type. Use when the exact type to instantiate depends on runtime conditions.
    ```java
    interface NotificationSender { void send(String message, String target); }
    class EmailSender implements NotificationSender { ... }
    class SmsSender implements NotificationSender { ... }
    class PushSender implements NotificationSender { ... }

    class NotificationFactory {
        static NotificationSender create(String channel) {
            return switch (channel) {
                case "email" -> new EmailSender();
                case "sms"   -> new SmsSender();
                case "push"  -> new PushSender();
                default -> throw new IllegalArgumentException("Unknown channel: " + channel);
            };
        }
    }
    ```

---

### Object-Oriented Design (OOP Depth)

* [ ] **Explain SOLID principles with concrete Java examples. Where have you applied them?**
    * **S — Single Responsibility:** A class has one reason to change. `OrderService` handles order business logic. `OrderRepository` handles persistence. `OrderEmailNotifier` handles emails. Don't mix them — when email templates change, only `OrderEmailNotifier` changes.
    * **O — Open/Closed:** Open for extension, closed for modification. Strategy pattern: add new pricing algorithm by writing a new class (`CpmPricing`), not by modifying `AdPricer`. Use `interface` + DI over `switch` on type.
    * **L — Liskov Substitution:** Subtypes must be substitutable for their base types without altering program correctness. A `Square extends Rectangle` that overrides `setWidth()` to also change height violates LSP — code that accepts `Rectangle` and calls `setWidth(5); setHeight(10)` gets unexpected area. Prefer composition over inheritance when IS-A doesn't hold perfectly.
    * **I — Interface Segregation:** Clients should not depend on interfaces they don't use. One fat `UserService` interface with 20 methods forces every implementor to implement all 20. Split into `UserReader`, `UserWriter`, `UserAuthenticator` — implementors and consumers only depend on what they need.
    * **D — Dependency Inversion:** High-level modules should not depend on low-level modules; both should depend on abstractions. `OrderService` depends on `OrderRepository` interface, not `JpaOrderRepository` concrete class. You can swap the DB or mock the repo in tests without touching `OrderService`.

---
* [ ] **What is the difference between composition and inheritance? When do you use each?**
    * **Inheritance (IS-A):** Use when the subclass truly IS a specialization of the base class and needs to be substitutable for it (Liskov). `Dog extends Animal`. Good for polymorphism through base type references.
    * **Composition (HAS-A):** The class contains an instance of another class and delegates to it. `Car HAS-A Engine`. Preferred over inheritance in most cases because:
        * No tight coupling to parent's implementation details — if the parent changes an internal method, the child may break silently.
        * You can compose multiple behaviors; you can only extend one class.
        * Easier to unit test — inject mock collaborators via constructor.
    * **"Favor composition over inheritance"** (Effective Java Item 18): Use inheritance only when the IS-A relationship is genuine and you need polymorphism. Otherwise compose.
    ```java
    // ❌ Inheritance for code reuse — wrong reason
    class InstrumentedHashSet<E> extends HashSet<E> {
        // Overriding addAll() and counting elements breaks because HashSet.addAll() calls add() internally
        // Your override gets called twice per element — classic inheritance pitfall
    }

    // ✅ Composition — delegate, don't extend
    class InstrumentedSet<E> implements Set<E> {
        private final Set<E> delegate;
        private int addCount = 0;
        InstrumentedSet(Set<E> s) { this.delegate = s; }
        public boolean add(E e) { addCount++; return delegate.add(e); }
        public boolean addAll(Collection<? extends E> c) { addCount += c.size(); return delegate.addAll(c); }
        // delegate all other methods to this.delegate
    }
    ```

---

### Production Scenarios & System Design in Java

* [ ] **You have a service processing 5M requests/hour. A memory usage spike occurs every 4 hours and the pod restarts. Walk through your diagnosis.**
    * **Step 1 — Rule out GC behavior:** Check if the spike aligns with a Full GC. If heap was previously stable and then Full GC starts failing to reclaim, it's a leak. If heap was always near capacity and Full GC triggers OOM — it's undersized heap, not a leak. Different fixes.
    * **Step 2 — Check Metaspace:** If `-XX:MaxMetaspaceSize` is not set and the service hot-reloads classes (custom ClassLoaders, reflection-heavy frameworks), Metaspace grows unbounded until OOM. Check `jvm_memory_used_bytes{area="nonheap"}` trend.
    * **Step 3 — Capture heap dump at spike, not at OOM:** Set `-XX:+HeapDumpOnOutOfMemoryError`. Better: trigger `jcmd <pid> GC.heap_dump` when memory is at 85% (alert threshold) — the process is still alive and the dump is analyzable.
    * **Step 4 — Analyze with Eclipse MAT:** Look at "Dominator Tree". 4-hour cycle suggests a **batch job or scheduled task accumulating data** — a `List` or `Map` that grows per cycle and is never cleared. Common in report generation, audit log batching, or Kafka consumer offset maps.
    * **Step 5 — Check ThreadLocal:** Scheduled tasks running in a thread pool may set `ThreadLocal` values and not clean them. At 4-hour intervals, pool threads accumulate stale values.
    * **Fix pattern:** Add memory trend alerting at 75% heap. Fix the root cause. Validate by watching GC trough baseline stay flat over 24h+ under production load.

---
* [ ] **How would you implement a thread-safe, fixed-size, bounded LRU cache in Java without using any external library?**
    * Combine `LinkedHashMap` (access-ordered) + `ReentrantReadWriteLock` for concurrency:
    ```java
    public class BoundedLRUCache<K, V> {
        private final int capacity;
        private final LinkedHashMap<K, V> map;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        public BoundedLRUCache(int capacity) {
            this.capacity = capacity;
            this.map = new LinkedHashMap<>(capacity, 0.75f, true) { // accessOrder=true
                @Override
                protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
                    return size() > capacity;
                }
            };
        }

        public V get(K key) {
            lock.readLock().lock();  // reads can be concurrent
            try { return map.get(key); }
            finally { lock.readLock().unlock(); }
        }

        public void put(K key, V value) {
            lock.writeLock().lock(); // writes are exclusive
            try { map.put(key, value); }
            finally { lock.writeLock().unlock(); }
        }
    }
    ```
    * **Interview follow-up:** Why not `Collections.synchronizedMap()`? It uses a single lock for all operations — `get()` and `put()` cannot be concurrent. `ReadWriteLock` allows concurrent reads. For production use, `Caffeine` cache is far better — non-blocking, highly concurrent, configurable TTL and size, Micrometer metrics built in.