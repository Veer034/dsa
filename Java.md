## JAVA

### Core Java Fundamentals

* [x] **Difference between == and equals(), and when to override hashCode()?**
---
* [x] **Explain immutability and why String is immutable**
---
* [x] **What are marker interfaces? Give examples**
    * **Marker interfaces** are empty interfaces (no methods) used to **mark a class** so that JVM or libraries apply **special behavior**.
      They are checked using `instanceof` or reflection.
      **Examples:** `Serializable` (allows object serialization), `Cloneable` (enables `clone()`), `RandomAccess` (fast indexed access in lists).

---
* [x] **Difference between abstract class and interface in Java 8+**
    1. An **abstract class** can have instance variables and constructors; an **interface cannot have instance state or constructors**.
    2. A class can **extend only one abstract class** but **implement multiple interfaces**.
    2. Abstract class methods can have **any access level**; interface methods are **public by default** (except private helper methods).
    3. Interfaces (Java 8–17) can have **default, static, and private methods**.
    4. Use **abstract class** for shared state and base logic, **interface** for contracts and multiple inheritance.

### Collections Framework

* [x] **Internal working of HashMap - how does it handle collisions?**
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
* [x] **What is the time complexity of various operations in different collections?**

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
---
* [x] **What is the difference between wait(), sleep(), and yield()?**
  > `wait()` releases the lock and waits for notification, `sleep()` pauses the thread for a fixed time without
  > releasing the lock, and `yield()` only hints the scheduler to give CPU to other runnable threads without blocking.

---
* [x] **Explain synchronized keyword and its types (method level, block level)**
---
* [x] What are volatile, atomic variables, and when to use them?
  ```text
  Visibility only → volatile 
  Atomic update → Atomic*
  Complex logic / multiple vars → synchronized or Lock
  ```
---
* [x] **Explain ThreadLocal and its use cases**
---
* [x] **Difference between Callable and Runnable**
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
        * ExecutorService es = new ThreadPoolExecutor(
          10, 10,
          0L, TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(1000), // bounded
          new ThreadPoolExecutor.CallerRunsPolicy()
          );

---
* [x] **Explain CountDownLatch, CyclicBarrier, and Semaphore**
    * CountDownLatch → wait until tasks finish
    * CyclicBarrier → threads wait for each other repeatedly
    * Semaphore → limit concurrent access to resources
---
* [x] **What are deadlock, livelock, and starvation? How to prevent them?**
    * Deadlock: threads wait on each other forever
    * Livelock: threads run but never progress
    * Starvation: thread never gets resources
---
* [x] **Explain happens-before relationship in Java Memory Model**
    * The happens-before relationship defines when one thread’s actions are guaranteed to be visible and ordered before another thread’s actions in Java.

### JVM & Memory Management

* [x] **Explain JVM architecture (Class Loader, Runtime Data Areas, Execution Engine)**
    * `.java` (source code) → `.class` (bytecode by `javac`) → ClassLoader (loads & verifies class) → Runtime Data
      Areas (heap, stack, metaspace, PC) → Execution Engine (Interpreter runs first, JIT compiles hot code) → Native CPU (executes optimized machine instructions)

---
* [x] **What are different memory areas in JVM? (Heap, Stack, Method Area, PC Register)**

![Image](https://media.geeksforgeeks.org/wp-content/uploads/20190614230114/JVM-Architecture-diagram.jpg)

![Image](https://miro.medium.com/1%2AsG2wIZg7SqyhKMKD1jxM9A.png)

    Heap: Stores objects and arrays; shared across threads; managed by GC.
    Stack: Stores method frames (local variables, calls); one stack per thread.
    Method Area (Metaspace): Stores class metadata, methods, static variables; uses native memory.
    PC Register: Stores current bytecode instruction address for each thread; helps resume execution after context switch.

---
* [x] **Explain Garbage Collection and types of GC (Serial, Parallel, CMS, G1, ZGC)**
---
* [x] **How would you identify and fix memory leaks?**
    * In Kubernetes, memory leaks are identified via **Prometheus/Grafana JVM metrics**—if **heap usage after GC
      keeps increasing and pods get OOMKilled**, it indicates a leak. Root cause is found using **JFR or async-profiler**, not by running `jmap` on pods.

---
* [x] **Explain JVM tuning parameters you've used in production**
    * In production (Java 17, Kubernetes), I tune JVM mainly for **GC latency and container awareness**: I set **heap sizing via `-Xms/-Xmx` aligned to pod limits**, use **G1GC (default) or ZGC for low latency**, tune **pause goals (`-XX:MaxGCPauseMillis`)**, control **Metaspace (`-XX:MaxMetaspaceSize`)**, enable **GC logs and JFR**, and rely on **Prometheus/Grafana metrics** to validate post-GC heap stability and pause times.”

---
* [x] **What is the difference between stack and heap memory?**
    * **Stack** stores method calls and local variables and is **thread-local and fast**, while **Heap** stores objects and is **shared across threads and managed by the Garbage Collector**.

---
* [x] **Explain PermGen vs Metaspace (Java 8+)**
    * **PermGen** (pre-Java 8) stored class metadata in a **fixed-size JVM heap space**, often causing `OutOfMemoryError`.
      **Metaspace** (Java 8+) stores the same metadata in **native memory**, grows dynamically, and is more stable in production.


### Exception Handling

* [x] **Difference between checked and unchecked exceptions**
    * **Checked exceptions** are **compile-time enforced** and must be caught or declared (e.g., `IOException`), while **unchecked exceptions** occur at **runtime** and are not mandatory to handle (e.g., `NullPointerException`).

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

### Java 8+ Features

* [x] **Explain Streams API and intermediate vs terminal operations**
    * Streams API enables functional-style, declarative processing of collections (filter, map, reduce) without modifying the source data.
    * **Intermediate Operations**
        * Lazy (not executed immediately)
        * Return another Stream
        * Can be chained
        * Executed only when a terminal operation is called
        * **Examples:** filter, map, sorted, distinct, limit
    * **Terminal Operations**
        * Trigger execution
        * Produce a result or side-effect
        * Close the stream (cannot reuse)
        * **Examples:** forEach, collect, reduce, count, findFirst


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
    * Use `map()` for simple transformations, `flatMap()` when dealing with nested collections or streams.
---
* [x] **Explain Optional and why it's useful**
    * Optional makes absence explicit and forces safe handling instead of silent null
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
    * A **default method** is a method with implementation inside an interface.
        * Can be overridden
        * Inherited by implementing classes
        * If class + interface have same method → class wins
        * Used for backward compatibility:  Iterable.forEach(), Collection.stream(), List.sort()
    * A **static method** in an interface belongs to the interface itself, not to instances.
        * Utility methods logically belong to the interface
        * Avoids dumping everything into helper classes like Collections
    * **Default** and **static methods** were added so Java could evolve core interfaces (Streams, Lambdas) without breaking millions of existing implementations.
---
* [x] **What's new in Java 17 that you've used?**
    * In Java 17, I actively use Records for DTOs, sealed classes for domain modeling, pattern matching and switch expressions for cleaner logic, text blocks for SQL/JSON, and benefit from GC and JVM encapsulation improvements.
