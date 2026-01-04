
# Interview Questions for 10+ Years Experience - Staff/Tech Lead/Architect Role

## JAVA

### Core Java Fundamentals
* [x] **Difference between == and equals(), and when to override hashCode()?**
* [x] **Explain immutability and why String is immutable**
* [x] **What are marker interfaces? Give examples**
  * **Marker interfaces** are empty interfaces (no methods) used to **mark a class** so that JVM or libraries apply **special behavior**.
    They are checked using `instanceof` or reflection.
    **Examples:** `Serializable` (allows object serialization), `Cloneable` (enables `clone()`), `RandomAccess` (fast indexed access in lists).
    
* [x] **Difference between abstract class and interface in Java 8+**
  1. An **abstract class** can have instance variables and constructors; an **interface cannot have instance state or constructors**.
  2. A class can **extend only one abstract class** but **implement multiple interfaces**.
  2. Abstract class methods can have **any access level**; interface methods are **public by default** (except private helper methods).
  3. Interfaces (Java 8–17) can have **default, static, and private methods**.
  4. Use **abstract class** for shared state and base logic, **interface** for contracts and multiple inheritance.

### Collections Framework
* [x] **Internal working of HashMap - how does it handle collisions?**
* [x] **Difference between HashMap, ConcurrentHashMap, and Hashtable**
  1. **HashMap**: Not thread-safe, fastest, allows **one null key and multiple null values**.
  2. **Hashtable**: Thread-safe using **method-level synchronization**, slower, **no null key/value** (legacy).
  2. **ConcurrentHashMap**: Thread-safe with **fine-grained locking / lock-free reads**, high concurrency, **no null key/value**.
  3. **Concurrency**: HashMap → none, Hashtable → full lock, ConcurrentHashMap → scalable concurrency.
  4. **Use case**: HashMap (single thread), ConcurrentHashMap (multi-threaded), Hashtable (avoid; legacy).
* [x] **HashSet vs LinkedHashSet vs CopyOnWriteArraySet**
   1. **HashSet**: No ordering, fastest for add/remove/contains; use when **order doesn’t matter**.
   2. **LinkedHashSet**: Maintains **insertion order** with slight overhead; use when **iteration order matters**.
   3. **CopyOnWriteArraySet**: **Thread-safe**, iteration without locks; very slow writes, fast reads.
   4. Use **HashSet** for single-threaded performance, **LinkedHashSet** for ordered sets,
   5. **CopyOnWriteArraySet** only for **read-heavy, rarely-updated concurrent** scenarios.

* [x] **When to use ArrayList vs LinkedList?**
  1. **ArrayList**: Use when you need **fast random access (O(1))**, frequent reads, and appends at the end; most use 
  cases fit this.
  2. **LinkedList**: Use when you frequently **insert/remove via iterator in the middle** or need **Deque operations** (`addFirst`, `removeLast`).
  3. **Avoid LinkedList** for random access (`get(i)` is O(n)) and cache-inefficient.
  4. **Rule of thumb**: Choose **ArrayList by default**; use **LinkedList** only for specific deque or iterator-heavy insert/remove needs.

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
* [x] **How does TreeMap maintain sorting?**
  1. **TreeMap is sorted because it is implemented as a Red-Black Tree**, not because of hashing.
  2. **TreeMap implements `NavigableMap` → `SortedMap`**, whose **contract requires keys to be kept in sorted order** (natural or via `Comparator`).
  3. **HashMap / ConcurrentHashMap** use **hashing**, so they have **no concept of order** at all.
  4. **LinkedHashMap** maintains **insertion or access order**, but **not sorted order** (it uses a linked list, not comparisons). 
  5. So, **only TreeMap is sorted** because it uses **comparison-based tree structure** and explicitly follows the `SortedMap` contract.

  **In short:**
  
  > TreeMap is sorted due to its **tree-based implementation + SortedMap/NavigableMap contract**, others are not because they are **hash-based or list-ordered**, not comparison-based.

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

* [x] **Explain thread lifecycle and thread states**
  > NEW → RUNNABLE → (BLOCKED / WAITING / TIMED_WAITING) → RUNNABLE → TERMINATED
* [x] **What is the difference between wait(), sleep(), and yield()?**
  > `wait()` releases the lock and waits for notification, `sleep()` pauses the thread for a fixed time without 
  > releasing the lock, and `yield()` only hints the scheduler to give CPU to other runnable threads without blocking.

* [x] **Explain synchronized keyword and its types (method level, block level)**
* [x] What are volatile, atomic variables, and when to use them?
  ```text
  Visibility only → volatile 
  Atomic update → Atomic*
  Complex logic / multiple vars → synchronized or Lock
  ```
* [x] **Explain ThreadLocal and its use cases**
* [x] **Difference between Callable and Runnable**
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
 
* [x] **Explain CountDownLatch, CyclicBarrier, and Semaphore**
  * CountDownLatch → wait until tasks finish 
  * CyclicBarrier → threads wait for each other repeatedly 
  * Semaphore → limit concurrent access to resources
* [x] **What are deadlock, livelock, and starvation? How to prevent them?**
  * Deadlock: threads wait on each other forever 
  * Livelock: threads run but never progress 
  * Starvation: thread never gets resources
* [x] **Explain happens-before relationship in Java Memory Model**
  * The happens-before relationship defines when one thread’s actions are guaranteed to be visible and ordered before another thread’s actions in Java.

### JVM & Memory Management
* [x] **Explain JVM architecture (Class Loader, Runtime Data Areas, Execution Engine)**
  * `.java` (source code) → `.class` (bytecode by `javac`) → ClassLoader (loads & verifies class) → Runtime Data 
    Areas (heap, stack, metaspace, PC) → Execution Engine (Interpreter runs first, JIT compiles hot code) → Native CPU (executes optimized machine instructions)

* [x] **What are different memory areas in JVM? (Heap, Stack, Method Area, PC Register)**

![Image](https://media.geeksforgeeks.org/wp-content/uploads/20190614230114/JVM-Architecture-diagram.jpg)

![Image](https://miro.medium.com/1%2AsG2wIZg7SqyhKMKD1jxM9A.png)

    Heap: Stores objects and arrays; shared across threads; managed by GC.
    Stack: Stores method frames (local variables, calls); one stack per thread.
    Method Area (Metaspace): Stores class metadata, methods, static variables; uses native memory.
    PC Register: Stores current bytecode instruction address for each thread; helps resume execution after context switch.

* [x] **Explain Garbage Collection and types of GC (Serial, Parallel, CMS, G1, ZGC)**
* [x] **How would you identify and fix memory leaks?**
   * In Kubernetes, memory leaks are identified via **Prometheus/Grafana JVM metrics**—if **heap usage after GC 
     keeps increasing and pods get OOMKilled**, it indicates a leak. Root cause is found using **JFR or async-profiler**, not by running `jmap` on pods.

* [x] **Explain JVM tuning parameters you've used in production**
  * In production (Java 17, Kubernetes), I tune JVM mainly for **GC latency and container awareness**: I set **heap sizing via `-Xms/-Xmx` aligned to pod limits**, use **G1GC (default) or ZGC for low latency**, tune **pause goals (`-XX:MaxGCPauseMillis`)**, control **Metaspace (`-XX:MaxMetaspaceSize`)**, enable **GC logs and JFR**, and rely on **Prometheus/Grafana metrics** to validate post-GC heap stability and pause times.”

* [x] **What is the difference between stack and heap memory?**
  * **Stack** stores method calls and local variables and is **thread-local and fast**, while **Heap** stores objects and is **shared across threads and managed by the Garbage Collector**.

* [x] **Explain PermGen vs Metaspace (Java 8+)**
  * **PermGen** (pre-Java 8) stored class metadata in a **fixed-size JVM heap space**, often causing `OutOfMemoryError`.
    **Metaspace** (Java 8+) stores the same metadata in **native memory**, grows dynamically, and is more stable in production.


### Exception Handling
* [x] **Difference between checked and unchecked exceptions**
  * **Checked exceptions** are **compile-time enforced** and must be caught or declared (e.g., `IOException`), while **unchecked exceptions** occur at **runtime** and are not mandatory to handle (e.g., `NullPointerException`).

* [x] **When to use throw vs throws?**
  * Use **`throw`** to **explicitly create and raise an exception inside a method**, while **`throws`** is used in a **method signature to declare exceptions that the method may propagate to the caller**.

* [x] **Best practices for exception handling in microservices**
  * Use **global exception handlers** to return **consistent error responses** (HTTP status + error code).
  * **Do not expose internal exceptions**; log detailed errors internally and return sanitized messages.
  * Use **checked exceptions for recoverable cases**, unchecked for programming errors.
  * **Fail fast**, use **timeouts, retries, and circuit breakers**, and correlate errors using **trace IDs** in logs.

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

* [x] **Difference between map() and flatMap()**
  * Use `map()` for simple transformations, `flatMap()` when dealing with nested collections or streams.
* [x] **Explain Optional and why it's useful**
  * Optional makes absence explicit and forces safe handling instead of silent null
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
* [x] **What's new in Java 17 that you've used?**
  * In Java 17, I actively use Records for DTOs, sealed classes for domain modeling, pattern matching and switch expressions for cleaner logic, text blocks for SQL/JSON, and benefit from GC and JVM encapsulation improvements.

### Design Patterns
* [x] **Singleton pattern - different ways to implement, thread-safe singleton**
* [x] **Why Spring Singleton ≠ Gang of Four(GoF) Singleton pattern.**
  * GoF Singleton guarantees one instance per JVM, while Spring Singleton guarantees one instance per container. Spring deliberately avoids JVM singletons to preserve testability, DI, and lifecycle management.
* [x] **Factory vs Abstract Factory pattern**
  * Factory creates a single object, Abstract Factory creates a family of related objects ensuring consistency.
* [x] **Why Spring uses Factory internally?**
  * Spring uses Factory because only a Factory can decide what object to create, when to create it, how to wire it, and what to return instead of it (proxy).
* [x] **When to use Builder pattern?**
  * Use Builder when object construction is complex, has many optional parameters, or must be immutable and readable.
* [x] **Explain Strategy, Observer, and Decorator patterns**
  * Strategy selects behavior, Observer notifies changes, Decorator enhances behavior dynamically.
* [x] **What is Dependency Injection?**
  * **Dependency Injection** is a design pattern where an object's dependencies are provided (injected) by an external framework rather than the object creating them itself. This promotes loose coupling, makes code more testable, and allows easy swapping of implementations. In Spring, dependencies are typically injected via constructor, setter, or field injection using @Autowired.

## SPRING BOOT

### Core Spring Concepts
* [x] **Explain IoC (Inversion of Control) and Dependency Injection**
  * **IoC (Inversion of Control)** is a principle where the control of object creation and lifecycle management is inverted from the application code to a framework/container. Instead of your code creating objects with new, the framework controls this.
  * **Dependency Injection** is a specific implementation of IoC. It's how the container actually provides (injects) the dependencies your objects need.


* [x] **Difference between @Component, @Service, @Repository, @Controller**
  * All four create Spring beans; @Component is generic, @Service is for business logic, @Repository is for data access (with exception translation), and @Controller is for handling web requests.


* [x] **What is spring AOP?**
  * Spring AOP is a programming approach where cross-cutting concerns (like logging, transactions, security) are 
    applied around business methods using proxies, without changing the business code. 
  * **How Spring AOP works** 
    * Spring creates a proxy object 
    * Calls go through proxy 
    * Advice runs before/after method 
    * Actual method invoked
      * **Proxy types:**
        * JDK Dynamic Proxy → interface-based 
        * CGLIB Proxy → class-based
  * **Key limitations (important)**
    * ❌ Only public methods 
    * ❌ Only Spring-managed beans 
    * ❌ Internal method calls not intercepted


* [x] **What is ApplicationContext vs BeanFactory?**
  * BeanFactory is the basic IoC container providing lazy bean initialization, while ApplicationContext is an advanced container that extends BeanFactory with eager initialization, internationalization, event propagation, and AOP support - it's the preferred choice for enterprise applications.


* [x] **Explain bean scopes (Singleton, Prototype, Request, Session)**
  * **Singleton (default)** - One instance per Spring container, shared across the application.
  * **Prototype** - New instance created every time the bean is requested.
  * **Request** - One instance per HTTP request (web applications only).
  * **Session** - One instance per HTTP session (web applications only).


* [x] **What is the Spring Bean lifecycle?**
  * Spring Bean lifecycle: Instantiation → Populate Properties → setBeanName() → setBeanFactory() → setApplicationContext() → @PostConstruct / afterPropertiesSet() → Bean Ready → @PreDestroy / destroy() → Bean Destroyed.


* [x] **Difference between @Autowired, @Inject, and @Resource**
  * **@Autowired** - Spring-specific, injects by type, requires @Qualifier for name-based injection, has required attribute. 
  * **@Inject** - JSR-330 standard, injects by type, uses @Named for disambiguation, no required attribute. 
  * **@Resource** - JSR-250 standard, injects by name first (via name attribute) then type, more concise for name-based injection.
  
### Spring Boot Specifics
* [x] **What is auto-configuration in Spring Boot?**
  * Spring Boot Auto-configuration automatically configures beans based on classpath dependencies, application properties, and existing beans, so you don’t need manual configuration.
  * For example, if **spring-boot-starter-data-jpa** is on the classpath, Boot auto-configures DataSource, EntityManager, and JpaTransactionManager automatically.
* [x] **Explain @SpringBootApplication annotation**
  * @SpringBootApplication enables auto-configuration, component scanning, and Java-based configuration to start a 
    Spring Boot application with minimal setup.
* [x] **How does Spring Boot differ from Spring Framework?**
  * **Spring Framework** is a core framework that provides DI, AOP, MVC, and transaction management, but requires manual configuration.
* [x] **What are Spring Boot Starters?**
  * Spring Boot Starters are dependency descriptors that bundle commonly used libraries together. Instead of adding multiple individual dependencies, you add one starter that includes everything needed for a specific functionality.
* [x] **Explain application.properties vs application.yml**
  * Both are Spring Boot configuration files, but differ in format. Use `.yml` for complex configurations with deep 
    nesting, `.properties` for simple configs or when team prefers it. If both exist, `.properties` takes precedence

### REST APIs
* [x] **Difference between @RestController and @Controller**
  * @Controller - Used for traditional MVC applications that return views (HTML pages). Requires @ResponseBody on methods to return data directly.
  *  @RestController - Combination of @Controller + @ResponseBody. Used for REST APIs that return data (JSON/XML), 
     not views.
* [x] **What are @PathVariable, @RequestParam, @RequestBody?**
  * @PathVariable → URL path, 
  * @RequestParam → query parameters, 
  * @RequestBody → request payload.
* [x] **How to handle exceptions globally? (@ControllerAdvice, @ExceptionHandler)**
  * @ControllerAdvice provides centralized exception handling, and @ExceptionHandler maps exceptions to HTTP responses globally.
  ```
  @ControllerAdvice
  class GlobalExceptionHandler {
  
      @ExceptionHandler(ResourceNotFoundException.class)
      public ResponseEntity<String> handleNotFound(Exception ex) {
          return ResponseEntity.status(404).body(ex.getMessage());
      }
  }

  ```

* [x] **Explain HTTP methods and their idempotency**
  * **Idempotent** means: making the same request multiple times results in the same server state.
  * **Key Interview Points** 
    * Idempotent ≠ Safe (DELETE is idempotent but not safe). 
    * GET, HEAD are safe and idempotent. 
    * PUT is idempotent because it replaces state. 
    * POST is non-idempotent by design (multiple creates).
    * PATCH is partial update
  
### Spring WebFlux (Reactive)

* [x] **Difference between blocking vs non-blocking I/O**
  * Blocking I/O ties up threads while waiting; non-blocking I/O frees threads and scales better.
* [x] **When to use WebFlux vs Spring MVC?**
  * Use MVC for simplicity and WebFlux for massive concurrency with non-blocking I/O.
* [x] **Explain Mono and Flux**
  * Mono is for one result, Flux is for many results—both are lazy and non-blocking.
* [x] **What is backpressure in reactive programming?**
  * Backpressure prevents fast producers from overwhelming slow consumers by controlling data flow.
  * Reactive pipelines handle backpressure automatically; use onBackpressureX only when the producer cannot slow down.
* [x] **How did you handle error handling in reactive streams?**
  * Reactive error handling is done via operators like onErrorResume and retry, not try–catch.
  ```
  1️⃣ onErrorReturn
  Fallback to a default value.
  
  Mono<User> user =
  userService.findById(id)
             .onErrorReturn(User.EMPTY);

  2️⃣ onErrorResume (most used)

  Switch to another reactive path.
  
  Mono<User> user =
  userService.findById(id)
  .onErrorResume(ex -> Mono.empty());
  
  3️⃣ onErrorMap
  
  Transform exception type.
  
  .onErrorMap(e -> new CustomException(e))
  
  4️⃣ doOnError
  
  Side effects (logging, metrics).
  
  .doOnError(log::error)
  
  5️⃣ Retry (for transient errors)
  .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
  ```

### Spring Data & ORM

* [x] **What is JpaRepository vs CrudRepository?**
  * CrudRepository gives basic CRUD, JpaRepository adds pagination, sorting, and JPA power.
* [x] **Explain @Transactional and transaction propagation levels**
  * @Transactional manages DB consistency, and propagation controls how methods join or create transactions.
  
| Propagation | Called with transaction | Called without transaction |
|------------|------------------------|---------------------------|
| REQUIRED | Uses existing | Creates new |
| REQUIRES_NEW | Creates new | Creates new |
| MANDATORY | Uses existing | **FAILS** ✗ |
| SUPPORTS | Uses existing | Runs without |
| NOT_SUPPORTED | Suspends, runs without | Runs without |
| NEVER | **FAILS** ✗ | Runs without |
| NESTED | Creates savepoint | **FAILS** ✗ |
  
* [x] **What is N+1 query problem and how to solve it?**
  * N+1 is a performance issue caused by lazy loading; solve it using fetch join, entity graphs, or DTO projections.
  * Ways to solve the issue.
    * Fetch Join (Preferred)
      * Load parent and child in single query 
      * JOIN FETCH in JPQL 
      *  ```
         @Query("SELECT o FROM Order o JOIN FETCH o.items")
         List<Order> findAllWithItems();
         ```
    * EntityGraph 
      * Declarative fetch plan 
      * Avoids query changes 
      * ```
        @EntityGraph(attributePaths = "items")
        List<Order> findAll();
         ```
    * DTO / Projection Query 
      * Fetch only required fields 
      * Best for read-only APIs 
      * ```
        SELECT new OrderDTO(o.id, i.name)
        FROM Order o JOIN o.items i
        ```
    * Batch Fetching 
      * Configure Hibernate batch size 
      * Reduces N queries to N/batch 
      * ```
        hibernate.default_batch_fetch_size=20
        ```



* [x] **Difference between save() and saveAndFlush()**
  * **save()** - Persists entity to the persistence context (Hibernate cache) but doesn't immediately write to the database. The actual INSERT/UPDATE happens when the transaction commits or flush() is called. 
  * **saveAndFlush()** - Persists entity AND immediately executes the SQL statement to the database, bypassing the normal flush timing.

###  Spring Security
* [x] **How does Spring Security work internally?**
  * **HTTP Request** 
    * SecurityFilterChain (15+ filters)
    * UsernamePasswordAuthenticationFilter : Intercepts login requests, extracts credentials
      * ```
        // Captures username/password from request
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(username, password);
        ```
    * AuthenticationManager : Delegates authentication to providers
      * ```
        Authentication auth = authenticationManager.authenticate(token);
        ```
    * AuthenticationProvider : Does actual authentication logic
      * ```
        @Override
        public Authentication authenticate(Authentication auth) {
        String username = auth.getName();
        String password = auth.getCredentials().toString();
  
        UserDetails user = userDetailsService.loadUserByUsername(username);
      
        if (passwordEncoder.matches(password, user.getPassword())) {
            return new UsernamePasswordAuthenticationToken(
                user, password, user.getAuthorities()
            );
          }
         throw new BadCredentialsException("Invalid credentials");
        }
        ```
    * UserDetailsService : Loads user from database
      * ```
        @Override
        public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username);
        return new org.springframework.security.core.userdetails.User(
        user.getUsername(),
        user.getPassword(),
        user.getAuthorities()
        );
        }
        ```
    * Your Controller
    * Spring Security = Filter Chain → Authentication → Authorization 
      * Filters intercept requests before controllers 
      * Authentication verifies identity (username/password)
      * Authorization checks permissions (roles/authorities)
      * SecurityContext stores authenticated user throughout request 
      * Customizable via SecurityFilterChain configuration
* [x] **Why Custom Privileges/Scopes Instead of Spring's hasRole()?**
  * Spring's hasRole() is limited to simple role-based access (ADMIN, USER). Our application needs fine-grained 
    permissions at feature level (like 'marketing:read', 'marketing:write') and subscription-based access (Basic, 
    Pro, Enterprise). Custom implementation gives us flexibility to combine multiple conditions and support complex 
    business rules that Spring's built-in annotations can't handle.
* [x] **How to implement JWT authentication in Spring Boot?**
  * For JWT Token Validation (extract, validate, set context):
    * MVC: Extend OncePerRequestFilter 
    * WebFlux: Implement WebFilter
    ```
    @Component
    public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        // Extract JWT, validate, set SecurityContext
        filterChain.doFilter(request, response);
      }
    }
    ```

  * For Authorization Logic (check privileges, roles, scopes):
    * MVC: Implement AuthorizationManager<RequestAuthorizationContext>
    * WebFlux: Implement ReactiveAuthorizationManager<AuthorizationContext>
    ```
    @Component
    public class CustomAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
  
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, 
                                      RequestAuthorizationContext context) {
        // Your authorization logic
        return new AuthorizationDecision(true/false);
     }
    }
    ```

  
### Microservices with Spring

* [ ] How to implement service discovery? (Eureka, Consul)
* [ ] What is API Gateway pattern?
* [ ] Explain Circuit Breaker pattern (Resilience4j, Hystrix)
* [ ] How to handle distributed tracing? (Sleuth, Zipkin)
* [ ] What is distributed configuration? (Spring Cloud Config)

## MYSQL

### SQL Fundamentals

* [ ] Difference between INNER JOIN, LEFT JOIN, RIGHT JOIN, FULL OUTER JOIN
* [ ] What is self-join? Give an example
* [ ] Explain GROUP BY and HAVING clause
* [ ] Difference between WHERE and HAVING
* [ ] What are aggregate functions?

###  Indexing & Performance
* [ ] What is an index? Types of indexes (B-Tree, Hash, Full-Text)
* [ ] When should you create an index?
* [ ] What is covering index?
* [ ] Explain the difference between clustered and non-clustered index
* [ ] How does EXPLAIN plan work?
* [ ] What are slow query logs and how to analyze them?

### Transactions & Isolation
* [ ] Explain ACID properties
* [ ] What are transaction isolation levels? (Read Uncommitted, Read Committed, Repeatable Read, Serializable)
* [ ] What are dirty read, non-repeatable read, and phantom read?
* [ ] Difference between COMMIT and ROLLBACK
* [ ] What is deadlock in database and how to prevent it?

### Database Design
* [ ] What is normalization? Explain 1NF, 2NF, 3NF, BCNF
* [ ] When to denormalize?
* [ ] What is ER diagram?
* [ ] Primary key vs Foreign key vs Unique key
* [ ] What is composite key?

### Advanced Concepts
* [ ] What are stored procedures and functions?
* [ ] Difference between stored procedure and function
* [ ] What are triggers? When to use them?
* [ ] Explain views - materialized vs regular views
* [ ] What is partitioning? Types of partitioning
* [ ] How to handle schema migrations in production?

### Replication & Scaling
* [ ] Explain master-slave replication
* [ ] What is read replica?
* [ ] How to scale MySQL databases?
* [ ] Difference between vertical and horizontal scaling

## KAFKA

### Core Concepts
* [ ] What is Kafka and why is it used?
* [ ] Explain Kafka architecture (Broker, Topic, Partition, Producer, Consumer)
* [ ] What is a Kafka topic and partition?
* [ ] What is replication factor and how does it work?
* [ ] Explain the role of ZooKeeper in Kafka (and KRaft mode in new versions)

### Producers
* [ ] How does a producer publish messages to Kafka?
* [ ] What are producer acknowledgments (acks=0, 1, all)?
* [ ] Explain idempotent producer
* [ ] What is producer batching and compression?
* [ ] How to ensure message ordering in Kafka?

### Consumers
* [ ] What is a consumer group?
* [ ] How does partition assignment work in consumer groups?
* [ ] Explain offset management - auto-commit vs manual commit
* [ ] What is consumer lag and how to monitor it?
* [ ] What happens when a consumer fails?
* [ ] Difference between poll() and subscribe()

### Performance & Reliability
* [ ] How to achieve exactly-once semantics in Kafka?
* [ ] What is log compaction?
* [ ] How to handle message retries?
* [ ] Explain back pressure handling in Kafka
* [ ] How did you tune Kafka for high throughput in your projects?

### Operations & Monitoring
* [ ] How to monitor Kafka cluster health?
* [ ] What metrics do you track? (Throughput, latency, consumer lag)
* [ ] How to handle rebalancing in consumer groups?
* [ ] How to add/remove brokers from a cluster?
* [ ] What is ISR (In-Sync Replicas)?

### Integration
* [ ] How did you integrate Kafka with Spring Boot?
* [ ] What is Kafka Streams vs Kafka Connect?
* [ ] How to implement dead letter queue in Kafka?

## REDIS
### Core Concepts
* [ ] What is Redis and why is it used?
* [ ] Explain Redis data structures (String, List, Set, Sorted Set, Hash, Bitmap, HyperLogLog, Streams)
* [ ] What is the difference between Redis and Memcached?
* [ ] Is Redis single-threaded or multi-threaded?

### Data Structures & Commands
* [ ] When to use List vs Set vs Sorted Set?
* [ ] How to implement a queue using Redis?
* [ ] How to implement rate limiting using Redis?
* [ ] Explain INCR, INCRBY, and their atomic nature
* [ ] What are Pub/Sub in Redis?

### Persistence
* [ ] What are RDB and AOF persistence?
* [ ] Difference between RDB and AOF
* [ ] Which persistence mechanism did you use and why?
* [ ] What happens during Redis restart?

### Cluster & High Availability
* [ ] Explain Redis Cluster architecture
* [ ] What is Redis Sentinel?
* [ ] Difference between Redis Cluster and Redis Sentinel
* [ ] How does sharding work in Redis Cluster?
* [ ] How did you set up Redis cluster at Convonest?
* [ ] What is hash slot in Redis Cluster?
* [ ] How does failover work in Redis?

### Caching Strategies
* [ ] Explain cache-aside, write-through, write-behind patterns
* [ ] What is cache invalidation strategy?
* [ ] How to handle cache stampede problem?
* [ ] What is cache warming?
* [ ] Explain TTL and expiration policies

### Performance & Best Practices
* [ ] How to handle memory limits in Redis?
* [ ] What are eviction policies in Redis?
* [ ] How to monitor Redis performance?
* [ ] What is pipelining in Redis?
* [ ] How to handle large keys in Redis?

## Elasticsearch
### Architecture & Core Concepts
* [ ] Explain the internal architecture of Elasticsearch. How do primary and replica shards work together?
* [ ] What is the difference between inverted index and forward index? How does Elasticsearch leverage inverted indices?
* [ ] Describe the lifecycle of a document from indexing to search in Elasticsearch.
* [ ] How does Elasticsearch achieve near real-time search? Explain the role of refresh interval, translog, and flush operations.
* [ ] What are segments in Lucene? How does segment merging work and why is it important?
### Cluster Management & Scalability
* [ ] How would you design an Elasticsearch cluster for high availability and fault tolerance?
* [ ] Explain the master election process. What happens during a split-brain scenario and how do you prevent it?
* [ ] How do you handle cluster scaling (both vertical and horizontal)? What are the considerations?
* [ ] Describe shard allocation strategies. When would you use awareness attributes?
* [ ] How do you perform zero-downtime reindexing for a large index?
### Performance Optimization
* [ ] What strategies would you use to optimize search performance for a 500TB cluster?
* [ ] Explain query vs filter context. How does caching work in each?
* [ ] How would you troubleshoot slow queries? Walk through your debugging approach.
* [ ] What is the impact of having too many shards? How do you determine the optimal shard size?
* [ ] Describe techniques to optimize indexing throughput for high-volume data ingestion.
### Query DSL & Search
* [ ] Explain the difference between match, match_phrase, and term queries. When would you use each?
* [ ] How do bool queries work? Explain must, should, filter, and must_not clauses with scoring implications.
* [ ] What are function_score and script_score queries? Provide use cases.
* [ ] How would you implement fuzzy search and autocomplete functionality at scale?
### Data Modeling & Index Management
* [ ] How do you design index mapping for a multi-tenant SaaS application?
* [ ] Explain index templates, dynamic templates, and when to use each.
* [ ] What are the pros and cons of nested vs parent-child relationships?
* [ ] How would you implement time-series data storage? Discuss rollover, ILM policies, and data tiers.
* [ ] When would you use alias vs reindex? Explain with scenarios.
### Monitoring & Production Issues
* [ ] What metrics do you monitor in production? How do you set up alerts?
* [ ] How would you troubleshoot circuit breaker exceptions and memory pressure?
* [ ] Explain the impact of heap size on Elasticsearch performance. How do you tune JVM settings?
* [ ] Describe your approach to handling unassigned shards in production.
* [ ] How do you perform disaster recovery? What's your backup and restore strategy?
### Advanced Topics
* [ ] Explain cross-cluster search and cross-cluster replication. What are the use cases?
* [ ] How does Elasticsearch handle distributed consistency? Discuss write and read consistency models.
* [ ] What is adaptive replica selection? How does it improve search performance?
* [ ] Describe your experience with machine learning features in Elasticsearch (anomaly detection, inference).
* [ ] How would you implement custom analyzers and tokenizers for specialized text processing?
### Security & Compliance
* [ ] How do you implement role-based access control (RBAC) in Elasticsearch?
* [ ] Explain field-level and document-level security. Provide implementation examples.
* [ ] How do you secure data at rest and in transit?
* [ ] What are your strategies for audit logging and compliance requirements (GDPR, SOC2)?
### Integration & Ecosystem
* [ ] Describe your experience integrating Elasticsearch with Kafka/streaming platforms.
* [ ] How do you design a logging/monitoring pipeline using the ELK/Elastic stack?
* [ ] What are the differences between Elasticsearch, Solr, and newer alternatives like OpenSearch?
* [ ] How would you migrate from a relational database to Elasticsearch?
### Scenario-Based Questions
* [ ] Your cluster is showing RED status in production. Walk me through your troubleshooting steps.
* [ ] You need to redesign an index with 2 billion documents. What's your approach with minimal downtime?
* [ ] A query that previously took 100ms now takes 5 seconds. How do you diagnose and fix this?
* [ ] Design an Elasticsearch architecture for a global e-commerce search system handling 100K QPS.


## DOCKER
### Core Concepts
* [ ] What is Docker and containerization?
* [ ] Difference between virtualization and containerization
* [ ] Explain Docker architecture (Docker Engine, Docker Daemon, Docker CLI)
* [ ] What is a Docker image vs Docker container?
* [ ] What is Docker Hub and Docker Registry?
### Dockerfile & Images
* [ ] Explain Dockerfile instructions (FROM, RUN, CMD, ENTRYPOINT, COPY, ADD, ENV, EXPOSE, WORKDIR)
* [ ] Difference between CMD and ENTRYPOINT
* [ ] Difference between COPY and ADD
* [ ] What is multi-stage build and why use it?
* [ ] How to optimize Docker image size?
* [ ] What are layers in Docker images?
### Container Management
* [ ] How to run, stop, and remove containers?
* [ ] What are Docker volumes and why use them?
* [ ] Difference between bind mount and volume
* [ ] What is Docker network? Types of networks
* [ ] How to link multiple containers?
* [ ] What is docker-compose and when to use it?
### Best Practices
* [ ] How to handle secrets in Docker?
* [ ] How to minimize Docker image size?
* [ ] What is .dockerignore file?
* [ ] How to debug issues in Docker containers?
* [ ] How did you implement Docker in your CI/CD pipeline?

## KUBERNETES
### Core Concepts
* [ ] What is Kubernetes and why is it used?
* [ ] Explain Kubernetes architecture (Master node, Worker node, Control Plane)
* [ ] What are Pods, Nodes, and Clusters?
* [ ] What is the smallest deployable unit in Kubernetes?
* [ ] Explain the role of etcd, kube-apiserver, kube-scheduler, kube-controller-manager, kubelet, kube-proxy
### Workload Resources
* [ ] What is a Pod? Can a Pod contain multiple containers?
* [ ] Difference between Deployment, StatefulSet, DaemonSet, Job, CronJob
* [ ] When to use Deployment vs StatefulSet?
* [ ] How does rolling update work in Kubernetes?
* [ ] What is ReplicaSet and how it differs from Deployment?
### Networking
* [ ] How does networking work in Kubernetes?
* [ ] What is a Service? Types of Services (ClusterIP, NodePort, LoadBalancer, ExternalName)
* [ ] What is Ingress and Ingress Controller?
* [ ] Explain how DNS works in Kubernetes
### Storage
* [ ] What are Volumes in Kubernetes?
* [ ] Difference between PersistentVolume (PV) and PersistentVolumeClaim (PVC)
* [ ] What are StorageClasses?
* [ ] How did you handle stateful applications in Kubernetes?
### Configuration & Secrets
* [ ] What are ConfigMaps and Secrets?
* [ ] How to inject environment variables in Pods?
* [ ] Best practices for managing secrets in Kubernetes
### Scaling & Autoscaling
* [ ] What is Horizontal Pod Autoscaler (HPA)?
* [ ] What is Vertical Pod Autoscaler (VPA)?
* [ ] What is Cluster Autoscaler?
* [ ] How to manually scale deployments?
### Health & Monitoring
* [ ] Difference between Liveness probe, Readiness probe, and Startup probe
* [ ] How to monitor Kubernetes cluster?
* [ ] What is kubectl and common commands you use?
### Operations
* [ ] How to debug a failing Pod?
* [ ] What are labels and selectors?
* [ ] What are namespaces and their use?
* [ ] How to perform zero-downtime deployment?
* [ ] How did you set up Kubernetes on AKS at Convonest?

## HELM
### Core Concepts
* [ ] What is Helm and why is it called "package manager for Kubernetes"?
* [ ] What are Helm Charts?
* [ ] Explain Helm architecture (Helm CLI, Tiller in Helm 2 vs Helm 3)
* [ ] What is the difference between Helm 2 and Helm 3?
### Chart Structure
* [ ] Explain the structure of a Helm Chart (Chart.yaml, values.yaml, templates/)
* [ ] What is values.yaml and how to use it?
* [ ] What are Helm templates and how do they work?
* [ ] How to override default values in Helm?
### Commands & Operations
* [ ] Common Helm commands (install, upgrade, rollback, uninstall, list)
* [ ] How to debug Helm charts? (helm template, helm lint, --dry-run)
* [ ] What is Helm release?
* [ ] How to manage multiple environments with Helm?
### Best Practices
* [ ] How did you create custom Helm charts at Convonest and Cisco?
* [ ] How to version Helm charts?
* [ ] How to manage dependencies in Helm?
* [ ] What is Helm repository?

## OAUTH2 & KEYCLOAK
### OAuth2 Core Concepts
* [ ] What is OAuth2?
* [ ] Difference between authentication and authorization
* [ ] What are OAuth2 grant types you've used? (Authorization Code, Client Credentials, Refresh Token)
* [ ] When to use which grant type?
### Tokens
* [ ] What is Access Token vs Refresh Token?
* [ ] What is JWT structure? (Header, Payload, Signature)
* [ ] How to validate JWT?
* [ ] What is token introspection?
### OAuth2 Implementation
* [ ] How did you implement OAuth2 at Convonest with Google and Outlook?
* [ ] How to store tokens securely?
* [ ] You mentioned AES-256 encryption and SHA-256 hashing - explain your approach
* [ ] Where did you store tokens? (Azure Key Vault)
### Keycloak Basics
* [ ] What is Keycloak and why did you use it?
* [ ] What is a Realm in Keycloak?
* [ ] How did you register microservices as clients in Keycloak?
* [ ] What are client types in Keycloak? (confidential, public, bearer-only)
* [ ] Difference between realm roles and client roles?
### Service Account Tokens
* [ ] What are service account tokens?
* [ ] How did microservices authenticate with each other using service account tokens?
* [ ] Explain the flow: Service A needs to call Service B - what happens?
* [ ] How to generate service account token programmatically?
* [ ] What is Client Credentials grant flow?
### JWT Token Handling
* [ ] What information is present in Keycloak JWT token? (roles, scopes, exp, sub)
* [ ] Why did you implement custom JWT decoder instead of using Spring Security's default?
* [ ] How did you validate JWT signature?
* [ ] How to extract roles and scopes from JWT?
* [ ] Where did you place JWT validation logic? (Filter, Interceptor, Controller level)
### Controller-Level Authorization
* [ ] How did you implement role and scope checking at controller level?
* [ ] Did you use custom annotations? Show example
* [ ] How to extract user roles from JWT in controller?
* [ ] How did you handle unauthorized access (403)?
###  Integration with Spring Boot
* [ ] What dependencies did you use for Keycloak integration?
* [ ] How did you configure Keycloak in application.yml?
* [ ] How to get service account token in Spring Boot?
* [ ] How did you add token to outgoing HTTP requests (RestTemplate/WebClient)?
###   Token Management
* [ ] How did you handle token expiration?
* [ ] How long was token validity in your setup?
* [ ] How to refresh expired tokens?
###  Microservices Communication
* [ ] When Service A calls Service B, did you propagate user token or use service account token?
* [ ] Why did you choose that approach?
* [ ] How to add Authorization header in inter-service calls?
* [ ] How did you handle token caching?
###   Security Best Practices
* [ ] Where did you store client secrets?
* [ ] How did you secure Keycloak admin console?
* [ ] Should token validation happen at API Gateway or service level? What did you choose?
###  Troubleshooting
* [ ] How to debug "401 Unauthorized" between microservices?
* [ ] What to check when JWT validation fails?
* [ ] How to verify if client is properly configured in Keycloak?
* [ ] How to check token validity in Keycloak admin console?

## WEBSOCKET
###  Core Concepts
* [ ] What is WebSocket and how does it differ from HTTP?
* [ ] When to use WebSocket vs REST API?
* [ ] Explain WebSocket handshake process
* [ ] What is full-duplex communication?
###    Implementation
* [ ] How did you implement WebSocket for live chat at Convonest?
* [ ] What is STOMP protocol?
* [ ] How to handle connection failures and reconnection?
* [ ] How to broadcast messages to multiple clients?
###    Scaling & Performance
* [ ] How to scale WebSocket connections horizontally?
* [ ] How to handle WebSocket with load balancers?
* [ ] How to implement authentication in WebSocket?
* [ ] How did you integrate WebSocket with Spring Boot?

## AI/ML SPECIFIC QUESTIONS
###  DeBERTa Model
* [ ] Why did you choose DeBERTa over BERT or RoBERTa for email classification?
* [ ] What is the difference between BERT and DeBERTa?
* [ ] Explain disentangled attention mechanism in DeBERTa
* [ ] What are the advantages of DeBERTa?
###  Email Classification
* [ ] What approach did you use to train the email classification model?
* [ ] How did you prepare training data for email classification?
* [ ] What evaluation metrics did you use? (Accuracy, Precision, Recall, F1-Score)
* [ ] How did you handle imbalanced datasets?
* [ ] Did you use transfer learning or fine-tuning?
* [ ] What was your dataset size and how did you split it?
* [ ] How did you handle multilingual emails?
###  RAG (Retrieval-Augmented Generation)
* [ ] What is RAG and why did you use it for automated email responses?
* [ ] How did you implement RAG architecture?
* [ ] What vector database or search engine did you use for retrieval?
* [ ] How did you create embeddings?
* [ ] How to handle context window limitations?
* [ ] What LLM did you use with RAG? (Mistral, Azure OpenAI, Ollama)
###  NLP & Model Training
* [ ] What NLP preprocessing steps did you apply?
* [ ] How did you handle tokenization?
* [ ] What is the difference between training from scratch vs fine-tuning?
* [ ] How did you evaluate model performance in production?
* [ ] How to handle model drift?
* [ ] What frameworks did you use? (Hugging Face, TensorFlow, PyTorch)
###  ML Operations
* [ ] How did you deploy ML models in production?
* [ ] How did you version ML models?
* [ ] How to monitor ML model performance?
* [ ] How did you handle model retraining?
* [ ] What is A/B testing for ML models?

## CLUSTER SETUP & DEVOPS
###  Redis Cluster Setup
* [ ] Walk through your Redis cluster setup on Azure VMs
* [ ] How many master and replica nodes did you configure?
* [ ] How did you configure Redis cluster using Helm charts?
* [ ] What monitoring did you set up for Redis?
* [ ] How to handle Redis cluster failover?
* [ ] What backup strategy did you implement?
###  Kafka Cluster Setup
* [ ] Explain your Kafka cluster architecture
* [ ] How many brokers and what replication factor?
* [ ] How did you deploy Kafka on Kubernetes?
* [ ] What monitoring tools did you use for Kafka? (Confluent Control Center, Kafka Manager)
* [ ] How did you handle Kafka upgrades?
###   Elasticsearch Cluster Setup
* [ ] Describe your Elasticsearch cluster setup at Convonest
* [ ] How many master, data, and client nodes?
* [ ] What index management strategy did you implement?
* [ ] How did you configure index sharding and replicas?
* [ ] What monitoring stack did you use? (Kibana, Filebeat, Grafana)
* [ ] How did you handle Elasticsearch snapshot and restore?
###  Azure Infrastructure
* [ ] How did you design the Azure infrastructure at Convonest?
* [ ] What services did you use? (AKS, VMs, Key Vault, SQL, Monitor)
* [ ] How did you manage infrastructure as code?
* [ ] What networking setup did you implement?
* [ ] How did you handle security and access control?

## CI/CD & DEPLOYMENT
###  Docker Image Deployment from GitHub
* [ ] How did you automate Docker image builds from GitHub repo?
* [ ] What CI/CD tool did you use? (Jenkins, GitHub Actions, Azure DevOps)
* [ ] Explain your complete CI/CD pipeline
* [ ] How to push Docker images to container registry? (Docker Hub, Azure ACR, AWS ECR)
* [ ] How did you implement blue-green deployment at Convonest?
* [ ] What is the difference between blue-green and canary deployment?
###  Kubernetes Deployment
* [ ] How to deploy Docker images to Kubernetes?
* [ ] How did you manage Kubernetes manifests?
* [ ] What is GitOps approach?
* [ ] How to implement zero-downtime deployment?
* [ ] How did you handle rollback in case of failures?
###  Automation & Scripting
* [ ] You mentioned automated deployment scripts - what did they include?
* [ ] How did you use Bash scripting for automation?
* [ ] How to automate Helm chart deployments?

## SYSTEM DESIGN & ARCHITECTURE
###  Architecture Design
* [ ] Design a scalable architecture for Convonest customer engagement platform
* [ ] How would you design a real-time ad decisioning engine (from Zee5 experience)?
* [ ] Design a ticket management system with SLA tracking
* [ ] How to design a system for millions of concurrent WebSocket connections?
###  Scalability
* [ ] How did you ensure horizontal scalability in your systems?
* [ ] What strategies did you use for database scaling?
* [ ] How to handle peak traffic loads?
* [ ] What is the difference between stateful and stateless applications?
###  Reliability & Monitoring
* [ ] What monitoring stack did you implement? (Prometheus, Grafana, Kibana, Azure Monitor)
* [ ] How to implement distributed tracing?
* [ ] What SLAs did you define and how to monitor them?
* [ ] How to implement circuit breaker pattern?
* [ ] What alerting mechanisms did you set up?
###    Security
* [ ] How did you secure microservices communication?
* [ ] What encryption strategies did you implement? (AES-256, SHA-256 at Convonest)
* [ ] How to manage secrets in production? (Azure Key Vault)
* [ ] What authentication/authorization mechanisms did you implement?

## BEHAVIORAL & LEADERSHIP (For Tech Lead/Architect Role)
###  Technical Leadership
* [ ] You co-founded Convonest - how did you make technology decisions?
* [ ] How do you evaluate build vs buy decisions?
* [ ] Describe a time when you had to refactor a legacy system
* [ ] How do you mentor junior developers?
* [ ] How do you handle technical debt?
###    Project Management
* [ ] How do you estimate engineering resources for a project?
* [ ] Describe your SDLC experience
* [ ] How do you prioritize features vs technical improvements?
* [ ] How do you handle conflicts in technical decisions within the team?
###    Architecture Decisions
* [ ] Why did you choose microservices over monolith at Convonest?
* [ ] How do you evaluate technology stack for a new project?
* [ ] Describe a complex technical challenge you faced and how you solved it
* [ ] You worked as Technical Advisor - what due diligence process did you follow?
###    Cross-Functional Experience
* [ ] You've worked across AdTech, Creative platforms, SaaS - how do you adapt to different domains?
* [ ] How did you handle the transition from Backend Engineer to Co-Founder?
* [ ] Describe your experience with on-site international code reviews (China)

## SCENARIO-BASED QUESTIONS
* [ ] Scenario 1: Your Kafka cluster is experiencing high consumer lag. How would you debug and resolve it?
* [ ] Scenario 2: A microservice is experiencing intermittent 500 errors. How would you troubleshoot using your monitoring stack?
* [ ] Scenario 3: Your Redis cluster is running out of memory. What steps would you take?
* [ ] Scenario 4: You need to migrate from monolith to microservices. What's your approach?
* [ ] Scenario 5: How would you design a system to handle 10 million email classifications per day?
* [ ] Scenario 6: Your Kubernetes pods are in CrashLoopBackOff. How do you debug?
* [ ] Scenario 7: A deployment caused downtime. How would you implement safeguards?
* [ ] Scenario 8: How would you optimize a slow-performing API endpoint?
* [ ] Scenario 9: Design a disaster recovery plan for the Convonest platform
* [ ] Scenario 10: How would you handle database schema migration in production with zero downtime?
* [ ] Scenario 11: A microservice is getting 401 Unauthorized when calling another service with Keycloak. How would you debug?
* [ ] Scenario 12: Service A needs to call Service B - walk through the complete authentication flow with Keycloak service account tokens
* [ ] Scenario 13: Your JWT tokens are expiring too frequently causing issues. What would you do?
* [ ] Scenario 14: How would you handle a security incident where a service account token is compromised?

Interview Preparation Tips:
* ✓ Be ready to explain "why" you made specific technology choices
* ✓ Prepare real examples from your projects (Convonest, Zee5, Cisco, Picsart, Reliance)
* ✓ Be ready to draw architecture diagrams
* ✓ Know the numbers from your systems (throughput, latency, scale)
* ✓ Be honest about what you know and don't know
* ✓ For questions you haven't directly worked on, explain your approach to learn/solve it
* ✓ Practice explaining your custom JWT decoder implementation
* ✓ Be ready to code on whiteboard or shared editor
* ✓ Review your Keycloak setup and service-to-service authentication flow
