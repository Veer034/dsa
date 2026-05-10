## SPRING BOOT

### Core Spring Concepts

* [x] **Explain IoC (Inversion of Control) and Dependency Injection**
    * **IoC (Inversion of Control)** is a principle where the control of object creation and lifecycle management is inverted from the application code to a framework/container. Instead of your code creating objects with new, the framework controls this.
    * **Dependency Injection** is a specific implementation of IoC. It's how the container actually provides (injects) the dependencies your objects need.

    * **Follow-up (Senior): Why is constructor injection preferred over field injection in production codebases? What specific problems does field injection cause?**
        * Field injection hides dependencies — you can't tell what a class needs without reading its internals.
        * It breaks immutability — fields can't be `final`, meaning they can be mutated at runtime.
        * Unit testing requires Spring context or reflection hacks (e.g., `ReflectionTestUtils`) to inject mocks. With constructor injection, you simply call `new MyService(mockDep)`.
        * It allows the object to be created in an invalid state (missing dependencies cause NPE at runtime, not at startup).
        * **Constructor injection fails fast** — if a required bean is missing, context startup throws `NoSuchBeanDefinitionException` immediately.
        ```java
        // ❌ Field injection — hidden deps, not testable without Spring
        @Service
        public class AdService {
            @Autowired
            private TargetingEngine engine;
        }

        // ✅ Constructor injection — explicit, immutable, easily testable
        @Service
        @RequiredArgsConstructor
        public class AdService {
            private final TargetingEngine engine;
        }
        ```

    * **Follow-up (Senior): How does Spring's `@Autowired` resolution order work when there are multiple candidates? Walk through the exact tie-breaking algorithm Spring uses internally.**
        * Spring resolves `@Autowired` in this exact order:
            1. **By type** — finds all beans matching the declared type. If exactly one → inject it.
            2. **By `@Primary`** — if multiple candidates, the one marked `@Primary` wins.
            3. **By `@Qualifier`** — if `@Qualifier("beanName")` is present at the injection point, Spring matches it against bean names/qualifier values.
            4. **By field/parameter name** — as a last resort, Spring uses the variable name as the bean name to disambiguate.
        * **If still ambiguous after all steps:** `NoUniqueBeanDefinitionException` is thrown at startup.
        * **Production trap:** Relying on field-name matching (step 4) is fragile — renaming the variable changes which bean is injected. Always use `@Qualifier` explicitly when disambiguation is needed.

    * **Follow-up (Senior): What is a circular dependency in Spring? When does it fail and when does it silently work — and why is the silent case dangerous?**
        * A circular dependency is when Bean A depends on Bean B, and Bean B depends on Bean A.
        * **With constructor injection:** Spring detects the cycle at startup and throws `BeanCurrentlyInCreationException`. Safe — fails fast.
        * **With field/setter injection:** Spring resolves it using a partially constructed proxy (creates A first as an incomplete object, injects it into B, then finishes A). The app starts — but you can end up calling methods on a bean whose dependencies aren't yet initialized.
        * **Why it's dangerous:** No exception, no warning. NPE or unexpected behavior appears only under specific runtime conditions, hard to reproduce.
        * **Fix:** Refactor to break the cycle — usually a design flaw (e.g., extract a third shared service). Avoid `@Lazy` as a hack; it hides the architectural problem.


---
* [x] **Difference between @Component, @Service, @Repository, @Controller**
    * All four create Spring beans; @Component is generic, @Service is for business logic, @Repository is for data access (with exception translation), and @Controller is for handling web requests.

    * **Follow-up (Senior): @Repository adds `PersistenceExceptionTranslation`. What exactly does that mean and when does it actually matter?**
        * Spring wraps `@Repository` classes with a `PersistenceExceptionTranslationPostProcessor`. It intercepts vendor-specific exceptions (`SQLException`, `HibernateException`, `JpaSystemException`) and translates them into Spring's unified `DataAccessException` hierarchy.
        * **Why it matters:** Without it, your `@Service` must catch `SQLException` — it is now coupled to your persistence technology. Swap Hibernate for JDBC and you must change service-layer catch blocks.
        * With translation, `@Service` only catches `DataAccessException` — persistence technology becomes swappable with zero service-layer changes.
        * **Important:** This translation only happens if you have a `PersistenceExceptionTranslationPostProcessor` bean registered (Spring Boot auto-configures this for you).

    * **Follow-up (Senior): Can you put business logic in @Repository or persistence calls in @Service? What actually breaks?**
        * Technically nothing breaks — Spring doesn't enforce it. But `@Repository` has exception translation applied; if you put business logic there and it throws a business exception, Spring may accidentally wrap it in a `DataAccessException`, confusing the caller.
        * It also destroys testability — mocking layers becomes harder, and transaction boundaries get muddled.
        * In code reviews at product companies, this is a structural red flag.

    * **Follow-up (Senior): In a large microservice with 50+ beans, startup time becomes a concern. What strategies do you use to optimize Spring context startup time in production at companies like Flipkart or Razorpay?**
        * **Lazy initialization globally:** `spring.main.lazy-initialization=true` — beans are only created when first needed, not at startup. Reduces startup time significantly but moves failures from startup to first-use.
        * **`@Lazy` on specific heavy beans:** Apply selectively to beans with expensive constructors (DB connection pools, large caches) to defer their creation.
        * **Spring Native / GraalVM AOT compilation:** Compiles the entire context ahead-of-time. Startup goes from seconds to milliseconds — critical for serverless/Lambda deployments.
        * **`spring-context-indexer`:** Generates a `META-INF/spring.components` index at compile time — avoids classpath scanning at runtime. Add `spring-context-indexer` to the build, all `@Component`-annotated classes are pre-indexed.
        * **Profiling startup:** Use `spring.jmx.enabled=false`, and `SpringApplicationRunListener` to measure per-phase startup time.
        * **Production benchmark target:** Containerized microservices should start under 3s (Spring MVC) or under 800ms (Spring Native) for auto-scaling responsiveness.


---
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

    * **Follow-up (Senior): The self-invocation problem — you have a @Transactional method that internally calls another @Transactional method in the same class. What happens and what are your options?**
        * Spring AOP works through a proxy. When `methodA()` calls `methodB()` on `this`, it bypasses the proxy entirely — `methodB()`'s `@Transactional` advice is **never triggered**. The second method silently runs inside the first method's transaction regardless of its own propagation setting.
        * This causes silent bugs — especially when `methodB` is `REQUIRES_NEW` (meant to be isolated) but actually runs inside the parent transaction.
        * **Solutions in order of preference:**
            1. **Refactor** — move `methodB()` to a separate Spring bean. Cleanest architectural fix.
            2. **Self-injection** — inject the bean into itself via `@Autowired` and call via the proxy reference.
            3. **AspectJ weaving** — compile-time/load-time weaving that works on self-calls. Heavy setup but correct.
        ```java
        // Self-injection fix (pragmatic)
        @Service
        public class OrderService {
            @Autowired
            private OrderService self; // proxy reference, not 'this'

            @Transactional
            public void placeOrder() {
                self.auditLog(); // ✅ goes through proxy, REQUIRES_NEW respected
            }

            @Transactional(propagation = Propagation.REQUIRES_NEW)
            public void auditLog() { ... }
        }
        ```

    * **Follow-up (Senior): When does Spring choose JDK Dynamic Proxy vs CGLIB, and what breaks if your bean class is `final`?**
        * Spring uses JDK Dynamic Proxy when the bean implements at least one interface. It uses CGLIB when the bean has no interface or when `proxyTargetClass = true` is forced.
        * CGLIB creates a subclass of your bean at runtime. If the class is `final`, CGLIB cannot subclass it → `Cannot subclass final class` exception at startup.
        * **Practical rule:** Don't make Spring-managed `@Service`/`@Component` classes or their AOP-advised methods `final`. Kotlin's `all-open` plugin exists specifically for this reason.

    * **Follow-up (Senior): You need to implement distributed tracing (adding a correlation/trace ID to every log line) across 13 microservices without touching every service's business code. How do you implement this with Spring AOP + MDC, and what are the limitations?**
        * Define a `@Around` aspect on all controllers (or use a `OncePerRequestFilter`) that: extracts the `X-Correlation-ID` header (or generates a UUID if absent), puts it into `MDC.put("traceId", id)`, proceeds with the method, then clears MDC in a `finally` block.
        * Spring AOP propagates the advice to all matching beans automatically — no business code changes needed.
        ```java
        @Aspect
        @Component
        public class CorrelationIdAspect {
            @Around("execution(* com.company.*.controller.*.*(..))")
            public Object injectTraceId(ProceedingJoinPoint pjp) throws Throwable {
                String traceId = UUID.randomUUID().toString();
                MDC.put("traceId", traceId);
                try {
                    return pjp.proceed();
                } finally {
                    MDC.clear(); // critical — prevent MDC leakage to pooled threads
                }
            }
        }
        ```
        * **Limitations:** MDC uses `ThreadLocal` — breaks with async (`@Async`) and reactive (WebFlux). For WebFlux, use Reactor Context and `Hooks.onEachOperator` to propagate MDC. For async, use `DelegatingSecurityContextAsyncTaskExecutor` pattern with MDC copy.
        * **At scale:** Use Spring Cloud Sleuth (or Micrometer Tracing in Boot 3.x) which handles all these propagation scenarios automatically and integrates with Zipkin/Jaeger.

    * **Follow-up (Senior): How do you write a custom `@Around` advice that measures method execution time and logs a warning if it exceeds an SLA threshold — without hardcoding the threshold per method?**
        * Create a custom annotation that carries the SLA as metadata:
        ```java
        @Target(ElementType.METHOD)
        @Retention(RetentionPolicy.RUNTIME)
        public @interface SlaMonitor {
            long warnThresholdMs() default 200;
        }

        @Aspect
        @Component
        public class SlaAspect {
            @Around("@annotation(sla)")
            public Object monitor(ProceedingJoinPoint pjp, SlaMonitor sla) throws Throwable {
                long start = System.currentTimeMillis();
                try {
                    return pjp.proceed();
                } finally {
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed > sla.warnThresholdMs()) {
                        log.warn("SLA breach: {}.{}() took {}ms (threshold: {}ms)",
                            pjp.getTarget().getClass().getSimpleName(),
                            pjp.getSignature().getName(), elapsed, sla.warnThresholdMs());
                    }
                }
            }
        }
        ```
        * Usage: `@SlaMonitor(warnThresholdMs = 100)` on any service method. Zero business code change. Threshold is configurable per method.
        * **Enhancement:** Export the metric to Micrometer (`Timer.record(elapsed)`) tagged with `class` and `method` — then alert on p99 in Grafana instead of individual log lines.


---
* [x] **What is ApplicationContext vs BeanFactory?**
    * BeanFactory is the basic IoC container providing lazy bean initialization, while ApplicationContext is an advanced container that extends BeanFactory with eager initialization, internationalization, event propagation, and AOP support - it's the preferred choice for enterprise applications.

    * **Follow-up (Senior): ApplicationContext supports `ApplicationEvent`. What is `@TransactionalEventListener` and why is it safer than a regular `@EventListener` for post-save notifications?**
        * `@EventListener` fires the moment `publishEvent()` is called — which could be mid-transaction. If the transaction later rolls back, your listener already executed (e.g., sent an email, pushed to a queue) — no way to undo it.
        * `@TransactionalEventListener(phase = AFTER_COMMIT)` fires **only if the surrounding transaction commits successfully**. No commit = no event fired. This guarantees eventual consistency between DB writes and downstream side effects.
        ```java
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onUserCreated(UserCreatedEvent event) {
            // Safely send welcome email — only fires if DB row was committed
            emailService.sendWelcome(event.getUserId());
        }
        ```
        * **Important:** If there is no active transaction when the event is published, `AFTER_COMMIT` events are dropped by default. Set `fallbackExecution = true` to fire them anyway without a transaction.


---
* [x] **Explain bean scopes (Singleton, Prototype, Request, Session)**
    * **Singleton (default)** - One instance per Spring container, shared across the application.
    * **Prototype** - New instance created every time the bean is requested.
    * **Request** - One instance per HTTP request (web applications only).
    * **Session** - One instance per HTTP session (web applications only).

    * **Follow-up (Senior): What happens when you inject a Prototype-scoped bean into a Singleton? What is the bug and how do you fix it?**
        * The singleton is created once. Spring injects the prototype at construction time — that same instance is reused forever. The "prototype" effectively becomes a singleton. This is a scope mismatch bug.
        * **Fixes:**
            1. **`ObjectProvider<T>`** — inject `ObjectProvider<MyPrototype>` and call `.getObject()` each time. Modern and clean.
            2. **`@Lookup`** — Spring overrides the method via CGLIB to return a fresh prototype per call. Declarative and Spring-idiomatic.
            3. **`ApplicationContext.getBean()`** — programmatic lookup. Works but couples your code to the Spring API.
        ```java
        // ✅ Using ObjectProvider (recommended)
        @Service
        @RequiredArgsConstructor
        public class ReportService {
            private final ObjectProvider<ReportContext> contextProvider;

            public void generate() {
                ReportContext ctx = contextProvider.getObject(); // fresh instance every call
            }
        }
        ```

    * **Follow-up (Senior): In a high-traffic system (e.g., 5M requests/hour like Zee5), using Prototype scope carelessly can cause a GC pressure problem. Why?**
        * Every injection/call creates a new object. If the prototype holds heavy resources (large buffers, DB connections) and lives beyond the request, GC must collect all of them. At 5M requests/hour, thousands of unreferenced prototype instances pile up per second, causing frequent minor GC pauses and eventually major GC events, increasing p99 latency.
        * **Rule:** Keep prototypes small and stateless. For heavy stateful objects, use a pool (e.g., Apache Commons Pool) instead of prototype scope.


---
* [x] **What is the Spring Bean lifecycle?**
  ```
    Constructor
    ↓
    Dependency Injection
    ↓
    setBeanName
    ↓
    setBeanFactory
    ↓
    setApplicationContext
    ↓
    @PostConstruct
    ↓
    afterPropertiesSet
    ↓
    Bean Ready
    ↓
    @PreDestroy
    ↓
    destroy()
  
  ------------
    @Component
    public class OrderService implements
    BeanNameAware,
    BeanFactoryAware,
    ApplicationContextAware,
    InitializingBean,
    DisposableBean {

    private String beanName;

    // 1. Instantiation
    public OrderService() {
        System.out.println("1. Constructor called");
    }

    // 2. Populate properties
    @Autowired
    public void setDependency(PaymentService ps) {
        System.out.println("2. Properties populated");
    }

    // 3. setBeanName
    @Override
    public void setBeanName(String name) {
        this.beanName = name;
        System.out.println("3. setBeanName: " + name);
    }

    // 4. setBeanFactory
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        System.out.println("4. setBeanFactory");
    }

    // 5. setApplicationContext
    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        System.out.println("5. setApplicationContext");
    }

    // 6a. @PostConstruct
    @PostConstruct
    public void postConstruct() {
        System.out.println("6. @PostConstruct");
    }

    // 6b. afterPropertiesSet
    @Override
    public void afterPropertiesSet() {
        System.out.println("7. afterPropertiesSet");
    }

    // ---- Bean Ready ----

    // 8a. @PreDestroy
    @PreDestroy
    public void preDestroy() {
        System.out.println("8. @PreDestroy");
    }

    // 8b. destroy
    @Override
    public void destroy() {
        System.out.println("9. destroy()");
    }
  }

  ```
  | Aspect                            | `BeanFactory`           | `ApplicationContext`          |
      | --------------------------------- | ----------------------- | ----------------------------- |
  | Purpose                           | Basic DI container      | Full application runtime      |
  | Bean creation                     | Lazy (on demand)        | Eager by default              |
  | Get a bean                        | ✅ Yes                   | ✅ Yes                         |
  | Publish events                    | ❌ No                    | ✅ Yes                         |
  | Listen to events                  | ❌ No                    | ✅ Yes                         |
  | Environment / profiles            | ❌ No                    | ✅ Yes                         |
  | Property resolution               | ❌ Limited               | ✅ Full (`@Value`, properties) |
  | Resource loading (file/classpath) | ❌ No                    | ✅ Yes                         |
  | Runtime strategy discovery        | ❌ No                    | ✅ `getBeansOfType()`          |
  | Internationalization (i18n)       | ❌ No                    | ✅ Yes                         |
  | AOP, scheduling support           | ❌ No                    | ✅ Yes                         |
  | Typical usage                     | Internal framework code | Real production apps          |
  | Should business code use it?      | Rare                    | Rare (only when needed)       |

    * **Follow-up (Senior): `@PostConstruct` vs `ApplicationRunner` vs `CommandLineRunner` — when does each run and which one is safe to use for tasks that require the HTTP server to already be accepting requests?**
        * **`@PostConstruct`** runs during bean initialization, before the full `ApplicationContext` finishes starting. The embedded Tomcat/Netty server is **not yet started**. Use it for bean-local setup (validate config, warm in-memory caches from a field already set).
        * **`CommandLineRunner` / `ApplicationRunner`** run after the entire context is refreshed and the server is ready. Use them for startup tasks that need the full application available (pre-loading DB data, registering with service discovery, verifying external connections).
        * **If you throw inside `@PostConstruct`**, the context fails to start — useful for fail-fast validation. If you throw inside a Runner, the app exits — same effect but at a later, safer stage.
        * **Multiple runners** — implement `Ordered` or use `@Order(1)` to control execution sequence.

    * **Follow-up (Senior): What is a `BeanPostProcessor` and how does Spring use it internally for features like `@Autowired`, AOP proxying, and `@Scheduled`?**
        * `BeanPostProcessor` has two callback methods: `postProcessBeforeInitialization` (called before `@PostConstruct`) and `postProcessAfterInitialization` (called after). Spring's own infrastructure hooks in here.
        * `AutowiredAnnotationBeanPostProcessor` — scans `@Autowired` fields and injects dependencies.
        * `AnnotationAwareAspectJAutoProxyCreator` — wraps beans in CGLIB/JDK proxies to apply AOP advice (`@Transactional`, `@Cacheable`, `@CircuitBreaker`).
        * `ScheduledAnnotationBeanPostProcessor` — detects `@Scheduled` methods and registers them with the task scheduler.
        * **Why it matters in interviews:** Understanding this explains *why* `@Transactional` on a `private` method doesn't work — the `BeanPostProcessor` creates a proxy of the class, and private methods are not visible to subclasses/proxies.

    * **Follow-up (Senior): You have a `@Scheduled` task that runs every minute and makes a DB call. In production under Kubernetes with 5 replicas, the task runs 5 times per minute — causing duplicate processing. How do you solve distributed scheduling in Spring Boot?**
        * The root problem: Spring's `@Scheduled` is JVM-local — every instance runs independently with no coordination.
        * **Solution 1 — ShedLock:** Annotate the method with `@SchedulerLock(name = "myTask", lockAtLeastFor = "50s", lockAtMostFor = "1m")`. ShedLock creates a lock record in a shared DB table (or Redis). Only the pod that acquires the lock executes; others skip.
        ```java
        @Scheduled(fixedRate = 60_000)
        @SchedulerLock(name = "dailyReportTask", lockAtMostFor = "55s")
        public void generateDailyReport() { ... }
        ```
        * **Solution 2 — Quartz Clustered Scheduler:** Replace `@Scheduled` with Quartz Jobs backed by a shared DB (JDBC JobStore). Quartz handles leader election natively. Heavier setup but feature-rich (job history, misfire handling, pausing jobs).
        * **Solution 3 — Kubernetes CronJob:** Move the scheduled task to a separate K8s CronJob that spins up exactly one pod. No leader election needed — architectural separation instead.
        * **Preferred for most teams:** ShedLock with Redis — minimal code change, no extra infrastructure, works with existing Spring `@Scheduled`.

    * **Follow-up (Senior): A `@PreDestroy` method in your service is supposed to drain an in-flight queue before shutdown. In Kubernetes, it sometimes doesn't execute. Why, and how do you guarantee graceful shutdown?**
        * Kubernetes sends `SIGTERM` to the container. The JVM receives it and begins shutdown — `@PreDestroy` hooks fire. **But** Kubernetes also stops sending traffic to the pod (removes from Service endpoints) ~2-3 seconds *after* `SIGTERM`, not before. So requests in-flight when `SIGTERM` arrives get `Connection Refused`.
        * **Fix — `preStop` hook:** Configure a Kubernetes `lifecycle.preStop` sleep (e.g., `sleep 10`) — this delays `SIGTERM` by 10s after the pod is removed from the load balancer, allowing in-flight requests to drain.
        * **Spring Boot side:** Set `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=20s` — Spring waits for active requests to complete before closing the context and firing `@PreDestroy`.
        * **Complete pattern:** K8s removes pod from endpoints → `preStop` sleep (5-10s) → `SIGTERM` → Spring graceful shutdown (20s) → `@PreDestroy` executes → process exits.


---
* [x] **Difference between @Autowired, @Inject, and @Resource**
    * **@Autowired** - Spring-specific, injects by type, requires @Qualifier for name-based injection, has required attribute.
    * **@Inject** - JSR-330 standard, injects by type, uses @Named for disambiguation, no required attribute.
    * **@Resource** - JSR-250 standard, injects by name first (via name attribute) then type, more concise for name-based injection.

    * **Follow-up (Senior): You have two implementations of the same interface (e.g., `CacheService` with `RedisCacheService` and `InMemoryCacheService`). Spring throws `NoUniqueBeanDefinitionException`. What are all your options to resolve it and which is preferred in a production multi-environment setup?**
        * **`@Qualifier("beanName")`** — hardcode the bean name at the injection point. Simple but brittle if bean names change.
        * **`@Primary`** — mark one implementation as the default. Works for most injection points but overrides globally.
        * **`@Profile("prod")`** on each implementation — one bean exists per profile. Best for environment-specific switching (dev uses in-memory, prod uses Redis).
        * **`@ConditionalOnProperty`** (Spring Boot) — activate based on a config property. Most flexible for runtime switching without changing code.
        * **`ObjectProvider<CacheService>`** — inject a lazy provider and pick the implementation at runtime based on logic.
        * **Preferred in production:** `@ConditionalOnProperty` + `@Profile` combo — gives you environment-based control with no code changes between deploys.

### Spring Boot Specifics

* [x] **What is auto-configuration in Spring Boot?**
    * Spring Boot Auto-configuration automatically configures beans based on classpath dependencies, application properties, and existing beans, so you don’t need manual configuration.
    * For example, if **spring-boot-starter-data-jpa** is on the classpath, Boot auto-configures DataSource, EntityManager, and JpaTransactionManager automatically.

    * **Follow-up (Senior): Walk through the internal mechanism — how does Spring Boot decide which auto-configurations to apply at startup?**
        * `@EnableAutoConfiguration` triggers `AutoConfigurationImportSelector`.
        * It reads all candidates from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
        * Each candidate class has `@ConditionalOn*` guards evaluated — only those whose conditions pass get registered as beans.
        * **Debugging:** Run with `--debug` or `logging.level.org.springframework.boot.autoconfigure=DEBUG` to print the full **CONDITIONS EVALUATION REPORT** showing which configs matched, skipped, and why.

    * **Follow-up (Senior): How do you exclude a specific auto-configuration that is causing a conflict?**
        * Via annotation: `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})`
        * Via properties: `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`
        * **Scenario:** A message-consumer microservice with no DB that pulls in a starter auto-configuring DataSource — excluding it prevents a startup failure when no DB URL is configured.

    * **Follow-up (Senior): You are building a shared internal library used by 20 Spring Boot microservices. How do you write a custom auto-configuration so your library's beans are automatically registered in every service that adds your JAR — without requiring any `@Import` or `@ComponentScan` changes in the consuming service?**
        * Create a configuration class annotated with `@AutoConfiguration` (Boot 3.x) with appropriate `@ConditionalOn*` guards.
        * Register it in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
        ```
        com.company.shared.MyLibraryAutoConfiguration
        ```
        * The consuming service adds your JAR to `pom.xml`. Spring Boot's `AutoConfigurationImportSelector` reads the imports file and conditionally registers your beans automatically.
        ```java
        @AutoConfiguration
        @ConditionalOnClass(MyLibraryClient.class)          // only if JAR is present
        @ConditionalOnMissingBean(MyLibraryClient.class)    // don't override user's custom bean
        @EnableConfigurationProperties(MyLibraryProperties.class)
        public class MyLibraryAutoConfiguration {
            @Bean
            public MyLibraryClient myLibraryClient(MyLibraryProperties props) {
                return new MyLibraryClient(props.getBaseUrl(), props.getApiKey());
            }
        }
        ```
        * **`@ConditionalOnMissingBean` is critical:** Allows consuming services to override your default bean with their own — the auto-configuration backs off gracefully. This is the exact pattern Spring Boot uses for `DataSource`, `ObjectMapper`, etc.
        * **Testing your auto-configuration:** Use `ApplicationContextRunner` in unit tests to assert beans are registered/excluded under different conditions without starting a full Spring context.

---
* [x] **Explain @SpringBootApplication annotation**
    * @SpringBootApplication enables auto-configuration, component scanning, and Java-based configuration to start a
      Spring Boot application with minimal setup.

    * **Follow-up (Senior): @ComponentScan scans from the package of the annotated class. What is the production risk of placing it in the wrong package?**
        * Beans in packages outside the scan root are silently never registered — no startup error. You get `NullPointerException` or `NoSuchBeanDefinitionException` at runtime under specific code paths.
        * **Rule:** Always place the main class at the top-level root package (`com.company.app`) so all sub-packages are automatically scanned.
        * To include additional external packages: `@ComponentScan(basePackages = {"com.company.app", "com.company.shared"})`.
---
* [x] **How does Spring Boot differ from Spring Framework?**
    * **Spring Framework** is a core framework that provides DI, AOP, MVC, and transaction management, but requires manual configuration.

    * **Follow-up (Senior): Which Spring Boot opinionated defaults are actively dangerous at production scale and must always be overridden?**
        * `spring.jpa.open-in-view=true` (default ON) — holds a DB connection open for the entire HTTP request lifecycle including view rendering. At scale this exhausts the connection pool. **Always set to `false`.**
        * HikariCP `maximum-pool-size=10` (default) — far too small for meaningful concurrent traffic. Size it to your actual expected concurrent DB operations.
        * `connection-timeout=30000ms` — 30s wait for a pool connection causes request pile-ups during spikes. Reduce to 2–5s to fail fast.
        * Tomcat default thread pool of 200 — may need tuning depending on whether handlers are blocking or fast-returning.
---
* [x] **What are Spring Boot Starters?**
    * Spring Boot Starters are dependency descriptors that bundle commonly used libraries together. Instead of adding multiple individual dependencies, you add one starter that includes everything needed for a specific functionality.

    * **Follow-up (Senior): What happens if both `spring-boot-starter-web` and `spring-boot-starter-webflux` are on the classpath?**
        * Spring Boot defaults to Spring MVC (Tomcat). WebFlux is not activated unless you explicitly set `spring.main.web-application-type=reactive`.
        * **Valid reason to have both:** Using `WebClient` (from WebFlux) for non-blocking outbound HTTP calls inside a Spring MVC application. MVC handles inbound; `WebClient` handles outbound reactive calls. A common and correct production pattern.
---
* [x] **Explain application.properties vs application.yml**
    * Both are Spring Boot configuration files, but differ in format. Use `.yml` for complex configurations with deep
      nesting, `.properties` for simple configs or when team prefers it. If both exist, `.properties` takes precedence.

    * **Follow-up (Senior): How does Spring Boot's property resolution priority work? What wins among a yml file, an env variable, and a JVM system property?**
        * Spring Boot has a 17-level priority chain. From highest to lowest:
            1. Command-line args (`--server.port=9090`) — **highest**
            2. JVM system properties (`-Dserver.port=9090`)
            3. OS environment variables (`SERVER_PORT=9090`)
            4. `application-{profile}.yml`
            5. `application.yml` — lowest among file-based sources
        * **Relaxed binding:** `SERVER_PORT` (env var) automatically maps to `server.port`. `MY_APP_DB_URL` maps to `my.app.db.url`. This lets Kubernetes ConfigMaps/Secrets override file config without rebuilding the JAR.

### REST APIs

* [x] **Difference between @RestController and @Controller**
    * @Controller - Used for traditional MVC applications that return views (HTML pages). Requires @ResponseBody on methods to return data directly.
    *  @RestController - Combination of @Controller + @ResponseBody. Used for REST APIs that return data (JSON/XML),
       not views.

    * **Follow-up (Senior): `@RestController` returns JSON by default. What determines how the object is serialized — and how do you control the serialization behavior (e.g., ignore nulls, custom date format) at the global level vs per-field level?**
        * Spring uses `HttpMessageConverter`. For JSON, it defaults to `MappingJackson2HttpMessageConverter` (backed by Jackson's `ObjectMapper`).
        * **Global control:** Configure a `Jackson2ObjectMapperBuilderCustomizer` bean or define your own `ObjectMapper` bean.
        * **Per-field control:** Use Jackson annotations directly on the model — `@JsonIgnore`, `@JsonProperty("customName")`, `@JsonFormat(pattern = "yyyy-MM-dd")`, `@JsonInclude(NON_NULL)` to suppress null fields.
        * **Production rule:** Never let null fields leak into API responses — they confuse clients. Set `spring.jackson.default-property-inclusion=non_null` globally, then opt-in to nullable fields where explicitly needed.
---
* [x] **What are @PathVariable, @RequestParam, @RequestBody?**
    * @PathVariable → URL path,
    * @RequestParam → query parameters,
    * @RequestBody → request payload.

    * **Follow-up (Senior): `@RequestBody` uses Jackson to deserialize. What happens if the incoming JSON has an unknown field, or is missing a required field? How do you control this behaviour globally?**
        * By default, Jackson **ignores unknown fields** (safe). Missing fields that map to primitive Java types cause a `HttpMessageNotReadableException` at deserialization time, returned as a 400 Bad Request by Spring's default error handling.
        * **Strict unknown field rejection:** Set `spring.jackson.deserialization.fail-on-unknown-properties=true` — rejects requests with unexpected fields. Useful for strict API contracts but breaks forward compatibility.
        * **Required field validation:** Don't rely on Jackson for this. Use Bean Validation (`@NotNull`, `@NotBlank` on DTO fields) + `@Valid` on the `@RequestBody` parameter → Spring throws `MethodArgumentNotValidException` which you handle in `@ControllerAdvice`.
        ```java
        @PostMapping("/ads")
        public ResponseEntity<Ad> create(@Valid @RequestBody AdRequest req) { ... }
        // @NotNull on AdRequest fields triggers validation before the method body runs
        ```
---
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

    * **Follow-up (Senior): `@ControllerAdvice` catches exceptions after they leave the controller. What happens to exceptions thrown inside a Spring Security filter — does `@ControllerAdvice` catch those?**
        * **No.** Security filters run before the DispatcherServlet — exceptions thrown there never reach `@ControllerAdvice`.
        * To handle security filter exceptions (e.g., invalid JWT, expired token), you must configure a custom `AuthenticationEntryPoint` (for authentication failures) and `AccessDeniedHandler` (for authorization failures) in your `SecurityFilterChain`.
        ```java
        http.exceptionHandling(ex -> ex
            .authenticationEntryPoint((req, res, e) -> {
                res.setStatus(401);
                res.getWriter().write("{"error": "Unauthorized"}");
            })
            .accessDeniedHandler((req, res, e) -> {
                res.setStatus(403);
                res.getWriter().write("{"error": "Forbidden"}");
            })
        );
        ```

    * **Follow-up (Senior): In a high-traffic API (e.g., Zee5 at 300K events/second), how do you design a standardized error response format across all microservices, and how do you prevent accidentally leaking internal stack traces or DB error messages to API consumers?**
        * **Standardized error contract:** Define a shared `ErrorResponse` DTO used across all services:
        ```java
        public record ErrorResponse(
            String errorCode,      // machine-readable: "PAYMENT_DECLINED"
            String message,        // human-readable: "Payment was declined"
            String traceId,        // from MDC for debugging without leaking internals
            Instant timestamp
        ) {}
        ```
        * **`@ControllerAdvice` as the single translation layer:** All exceptions are caught here and mapped to `ErrorResponse`. Stack traces, Hibernate messages, SQL errors NEVER reach the response body.
        ```java
        @ExceptionHandler(Exception.class)  // catch-all for unhandled exceptions
        public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
            log.error("Unexpected error on {}", req.getRequestURI(), ex); // full trace in logs only
            return ResponseEntity.status(500).body(new ErrorResponse(
                "INTERNAL_ERROR", "An unexpected error occurred",
                MDC.get("traceId"), Instant.now()
            ));
        }
        ```
        * **Never expose:** SQL state codes, Hibernate entity class names, internal service names, stack frames. These are reconnaissance data for attackers.
        * **Validation errors:** Return field-level details from `MethodArgumentNotValidException` — these are safe, client-facing:
        ```java
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
            Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(toMap(FieldError::getField, FieldError::getDefaultMessage));
            // Include fieldErrors in response — safe, helpful to API consumers
        }
        ```

---
* [x] **Explain HTTP methods and their idempotency**
    * **Idempotent** means: making the same request multiple times results in the same server state.
    * **Key Interview Points**
        * Idempotent ≠ Safe (DELETE is idempotent but not safe).
        * GET, HEAD are safe and idempotent.
        * PUT is idempotent because it replaces state.
        * POST is non-idempotent by design (multiple creates).
        * PATCH is partial update

    * **Follow-up (Senior): In high-throughput systems (e.g., 2M+ shipments/month at Reliance Retail), how do you make POST endpoints idempotent to safely handle client retries without creating duplicate records?**
        * Use an **idempotency key** — client sends a unique request ID (UUID) in a header (e.g., `Idempotency-Key`).
        * Server stores `(idempotency_key → response)` in Redis with a TTL.
        * On duplicate request: return cached response without re-executing business logic.
        ```java
        @PostMapping("/shipments")
        public ResponseEntity<Shipment> create(
                @RequestHeader("Idempotency-Key") String key,
                @RequestBody ShipmentRequest req) {

            Shipment cached = redis.get("idem:" + key);
            if (cached != null) return ResponseEntity.ok(cached);

            Shipment created = shipmentService.create(req);
            redis.set("idem:" + key, created, Duration.ofHours(24));
            return ResponseEntity.status(201).body(created);
        }
        ```
        * **Important:** The lookup + save must be atomic (use Redis `SET NX`) or wrapped in a distributed lock to prevent race conditions under simultaneous duplicate requests.

### Spring WebFlux (Reactive)

---
* [x] **Difference between blocking vs non-blocking I/O**
    * Blocking I/O ties up threads while waiting; non-blocking I/O frees threads and scales better.

    * **Follow-up (Senior): With Spring WebFlux on Netty, what happens if a single blocking JDBC call leaks into a reactive pipeline — and how do you detect it?**
        * A blocking call on a Netty event-loop thread freezes that thread. It can no longer handle other requests. Under load, enough of these calls deadlock the entire event loop — you see massive latency spikes with no obvious error.
        * **Detection in tests:** Enable **BlockHound** — it detects blocking calls on reactive threads and throws an error, catching the bug before production.
        * **Fix:** Always offload blocking calls to a dedicated scheduler:
        ```java
        Mono.fromCallable(() -> jdbcRepo.findById(id))  // blocking
            .subscribeOn(Schedulers.boundedElastic())    // offload to bounded thread pool
            .flatMap(this::processReactively);
        ```
        * **Rule:** `Schedulers.boundedElastic()` for blocking I/O, `Schedulers.parallel()` for CPU-bound, never block on event loop threads.
---
* [x] **When to use WebFlux vs Spring MVC?**
    * Use MVC for simplicity and WebFlux for massive concurrency with non-blocking I/O.

    * **Follow-up (Senior): You used both Spring WebFlux (Convonest) and Vert.x (Zee5). If you're starting a new high-throughput service today, which would you choose and why?**
        * **Choose WebFlux** when you need full Spring ecosystem integration (Spring Security, Spring Data reactive, Actuator, Config) and your team is Java/Spring fluent. The operator model (Mono/Flux) provides composable, readable pipelines. Backpressure is built-in.
        * **Choose Vert.x** when you need maximum raw throughput and lowest p99 latency, have strict event-loop discipline across the team, and don't need the Spring ecosystem. Vert.x has lower overhead per message and stricter event-loop enforcement — no accidental blocking is possible (it throws immediately).
        * **Key tradeoff:** WebFlux's `Schedulers` allow safely offloading blocking code; Vert.x does not tolerate it at all. WebFlux is more forgiving but slightly slower. Vert.x is faster but operationally stricter.
---
* [x] **Explain Mono and Flux**
    * Mono is for one result, Flux is for many results—both are lazy and non-blocking.

    * **Follow-up (Senior): `Mono` and `Flux` are lazy — nothing runs until subscribed. What is the most common production mistake developers make with this laziness?**
        * Calling a `Mono`-returning method and not subscribing to it — the operation is **never executed**. No error, no log, just silent no-op.
        * Common example: calling `mongoRepo.save(entity)` (returns `Mono`) inside a reactive pipeline without chaining it — the save never happens.
        * **How to catch it:** Use `BlockHound` + write integration tests that assert the side effect occurred (record in DB, message on queue), not just that no exception was thrown.
        * **Another trap:** Mixing `Mono.just(blockingCall())` — the blocking call inside `just()` executes **eagerly at construction time**, on whatever thread is calling it. Use `Mono.fromCallable(() -> blockingCall())` + `subscribeOn` instead.
---
* [x] **What is backpressure in reactive programming?**
    * Backpressure prevents fast producers from overwhelming slow consumers by controlling data flow.
    * Reactive pipelines handle backpressure automatically; use onBackpressureX only when the producer cannot slow down.

    * **Follow-up (Senior): In your Convonest WebSocket live chat (100+ concurrent connections) with Kafka as the event source — how would you apply backpressure between the Kafka consumer and WebSocket emitter to prevent OOM under burst load?**
        * Kafka produces faster than WebSocket clients can consume during bursts. Without backpressure, the in-memory buffer grows until OOM.
        * Use `onBackpressureBuffer` with a bounded buffer and an explicit drop or error strategy:
        ```java
        Flux<String> kafkaStream = kafkaReceiver.receive()
            .map(ReceiverRecord::value)
            .onBackpressureBuffer(
                1000,
                dropped -> log.warn("Message dropped under backpressure: {}", dropped),
                BufferOverflowStrategy.DROP_LATEST
            );
        session.send(kafkaStream.map(session::textMessage));
        ```
        * **Monitor:** Alert when drop rate exceeds threshold (Micrometer counter on the drop callback) — sustained drops mean you need to either increase consumer throughput or add more WebSocket partitions.
---
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

    * **Follow-up (Senior): `retry()` vs `retryWhen()` — why would plain `retry(3)` in a high-throughput pipeline (300K+/second) cause a thundering herd problem?**
        * `retry(3)` retries immediately with zero delay. If a downstream service goes down and 300K events/second are all retrying instantly, you multiply the load 3× simultaneously — a classic retry storm that prevents the downstream from recovering.
        * **Fix:** Always use `retryWhen` with exponential backoff + jitter:
        ```java
        .retryWhen(
            Retry.backoff(3, Duration.ofMillis(100))
                 .maxBackoff(Duration.ofSeconds(5))
                 .jitter(0.5)  // randomize to spread retries across time
                 .filter(ex -> ex instanceof TransientException)
        )
        ```
        * Even 50% jitter on 300K events/second prevents the synchronized spike that would re-trigger the circuit breaker.

    * **Follow-up (Senior): You have a reactive pipeline that calls 3 downstream services. You want to call all 3 in parallel, collect results, and if any one fails, still return results from the successful ones (partial success). How do you implement this with Reactor?**
        * Use `Mono.zip` for parallel calls that must all succeed, or `Flux.merge` + `onErrorResume` per stream for partial success:
        ```java
        Mono<UserProfile> profile = userService.getProfile(userId)
            .onErrorResume(ex -> Mono.just(UserProfile.EMPTY));

        Mono<List<Order>> orders = orderService.getOrders(userId)
            .onErrorResume(ex -> Mono.just(Collections.emptyList()));

        Mono<CreditScore> credit = creditService.getScore(userId)
            .onErrorResume(ex -> Mono.just(CreditScore.UNAVAILABLE));

        // All 3 execute in parallel — subscriber gets partial results even if some fail
        return Mono.zip(profile, orders, credit)
            .map(tuple -> new DashboardResponse(tuple.getT1(), tuple.getT2(), tuple.getT3()));
        ```
        * **Key insight:** `onErrorResume` per sub-stream provides fallback values *before* `zip` sees them. `zip` still completes as long as all 3 Monos complete (even with fallback values).
        * **`Mono.zipDelayError`:** If you want all 3 to execute even if the first fails (vs `zip` which cancels siblings on first error), use `zipDelayError` — collects all errors and emits a `CompositeException` if multiple fail.
        * **Timeout per call:** Combine with `.timeout(Duration.ofMillis(500))` per service call so one slow service doesn't block the entire parallel set.
----
* [x] **Compare Webflux reactive streams vs Vert.x Eventloop?**

| Aspect                 | **Spring WebFlux**                       | **Vert.x**                  |
| ---------------------- | ---------------------------------------- | --------------------------- |
| Core model             | Reactive Streams (Reactor `Mono/Flux`)   | Event-loop, callback/async  |
| Threading              | Netty EL + schedulers (soft EL)          | Strict single EL per core   |
| Context switching      | Possible (scheduler boundaries)          | None unless you offload     |
| Blocking tolerance     | Safer (offloads blocking)                | Not allowed (fails fast)    |
| Backpressure           | Built-in (`request(n)`)                  | Manual / queue-based        |
| Abstraction cost       | Higher (operators, bookkeeping)          | Lower (direct handlers)     |
| Raw performance        | Very good                                | Excellent (lower p99)       |
| Latency predictability | Good                                     | Very high                   |
| Memory / GC            | Higher                                   | Lower                       |
| Ecosystem              | Full Spring (Security, Actuator, Config) | Minimal, lightweight        |
| Dev productivity       | High (Spring style)                      | Medium (event-loop mindset) |
| Best scale range       | Low → high                               | High → extreme              |
| Failure mode           | Degrades gracefully                      | Fails fast                  |

* [x] **Reactive streams vs Eventloop?**
  Here are **clean, exact one-liners** (interview-perfect):

    * **Event loop:**

  >   *A single-threaded execution loop that handles many concurrent I/O events without blocking.*

    * **Reactive stream:**

  >   *A demand-driven data pipeline where consumers control how much data producers emit using backpressure.*

That’s all you need.


### Spring Data & ORM


* [x] **What is JpaRepository vs CrudRepository?**
    * CrudRepository gives basic CRUD, JpaRepository adds pagination, sorting, and JPA power.

    * **Follow-up (Senior): `JpaRepository.findAll()` with no pagination on a table with millions of rows — what is the production risk and how do you fix it?**
        * It loads every row into JVM heap memory in one shot. At millions of rows this causes OOM or major GC pauses, and holds a DB cursor open for the entire duration.
        * **Fix 1 — Pagination:** Use `findAll(Pageable)` and process page by page.
        * **Fix 2 — Stream:** Use a `@Query` returning `Stream<Entity>` — Hibernate streams results lazily from the cursor. Must be inside a `@Transactional(readOnly = true)` and the stream must be closed after use.
        * **Fix 3 — Slice instead of Page:** `Slice<T>` avoids the costly `COUNT(*)` query that `Page<T>` executes. Prefer it for infinite scroll / cursor-based pagination.
        ```java
        @Transactional(readOnly = true)
        @Query("SELECT e FROM Entity e")
        Stream<Entity> streamAll(); // lazy, cursor-based, must close stream
        ```
---
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

    * **Follow-up (Senior): `REQUIRES_NEW` suspends the current transaction and opens a new DB connection. What production problem does this cause with connection pools, and how do you tune around it?**
        * `REQUIRES_NEW` holds **two connections simultaneously** per thread: the suspended one and the new one. If your pool size is 10 and 10 threads each hit a `REQUIRES_NEW` method, you need 20 connections — pool exhaustion → deadlock with all threads waiting for connections that are held by other waiting threads.
        * **Rule:** Use `REQUIRES_NEW` sparingly. Monitor HikariCP's `hikaricp.connections.active` metric. Pool size must account for the max simultaneous connections per thread.
        * **Better pattern for audit isolation:** Publish an event with `@TransactionalEventListener(AFTER_COMMIT)` instead — the audit write happens after the parent commits, in a fresh transaction, with no double-connection holding.

    * **Follow-up (Senior): `@Transactional(readOnly = true)` — what does it actually do internally, and why should all read-only service methods use it?**
        * Spring passes `readOnly = true` to the JDBC connection. Hibernate then: skips dirty checking (no snapshot comparison at flush time), disables first-level cache writes, and may route to a read replica if you have a routing `DataSource`.
        * **Performance benefit at scale:** Skipping dirty checking alone saves significant CPU on large result sets. At Cisco's 500M+ document scale, this is meaningful.
        * It also signals intent clearly in code — reviewers immediately know this method does not write.

    * **Follow-up (Senior): `@Transactional` is not applied to your method even though it's annotated correctly. Walk through the 5 most common reasons this silently fails in production.**
        1. **Self-invocation (same class):** Calling the `@Transactional` method from within the same bean bypasses the proxy. The annotation is ignored.
        2. **`private` or `final` method:** Spring's CGLIB proxy cannot override `private`/`final` methods. Annotation silently ignored. Must be `public` and non-final.
        3. **Exception type not rolling back:** By default, Spring only rolls back on unchecked exceptions (`RuntimeException`). A checked exception (e.g., `IOException`) commits the transaction unless you add `rollbackFor = Exception.class`.
        4. **Wrong `@Transactional` import:** Using `javax.transaction.Transactional` instead of `org.springframework.transaction.annotation.Transactional` — behavior subtly differs, and `rollbackOn` semantics change.
        5. **Bean not managed by Spring:** If the class is instantiated with `new` instead of injected, no proxy wraps it — `@Transactional` does nothing.
        ```java
        // ❌ Silent failure — checked exception, transaction commits
        @Transactional
        public void process() throws IOException { throw new IOException(); }

        // ✅ Explicit rollback on all exceptions
        @Transactional(rollbackFor = Exception.class)
        public void process() throws IOException { throw new IOException(); }
        ```

    * **Follow-up (Senior): In a payment processing service, how do you implement an outbox pattern using Spring `@Transactional` to guarantee at-least-once delivery of domain events to Kafka — even if Kafka is temporarily down?**
        * **The problem:** Writing to DB and publishing to Kafka in a single `@Transactional` block doesn't work — Kafka is not a transactional participant in the JDBC transaction. If Kafka publish fails after the DB commits, the event is lost.
        * **Outbox pattern:**
            1. In the same `@Transactional` DB write, also insert a row into an `outbox_events` table (`event_type`, `payload`, `created_at`, `processed = false`).
            2. A separate `@Scheduled` poller (or Debezium CDC) reads unprocessed outbox rows, publishes to Kafka, then marks them `processed = true`.
            3. If Kafka is down, events accumulate safely in the outbox table. When Kafka recovers, the poller drains the backlog.
        ```java
        @Transactional
        public void processPayment(PaymentRequest req) {
            paymentRepo.save(new Payment(req));
            outboxRepo.save(new OutboxEvent("PAYMENT_PROCESSED", toJson(req)));
            // Both writes in same ACID transaction — atomicity guaranteed
        }
        ```
        * **Deduplication on consumer side:** Since it's at-least-once, use the event's `id` (idempotency key) in the Kafka consumer to skip already-processed events.


---
* [x] **What is N+1 query problem and how to solve it?**
    * N+1 is a performance issue caused by lazy loading; solve it using fetch join, entity graphs, or DTO projections.
    * Ways to solve the issue.
        * Fetch Join (Preferred)
            * Load parent and child in single query
            * JOIN FETCH in JPQL
             ```
               @Query("SELECT o FROM Order o JOIN FETCH o.items")
               List<Order> findAllWithItems();
            ```
        * EntityGraph
            * Declarative fetch plan
            * Avoids query changes
          ```
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

    * **Follow-up (Senior): The N+1 problem also manifests with `@ManyToMany` — what specific Hibernate pitfall makes it worse than `@OneToMany`, and what is the fix?**
        * With `@ManyToMany` and `JOIN FETCH`, if two collections are fetched simultaneously Hibernate throws `MultipleBagFetchException` ("cannot simultaneously fetch multiple bags"). This forces developers to either use `Set` instead of `List` (which enables simultaneous fetches but loses ordering), or split into separate queries.
        * **Best practice:** Use `@EntityGraph` with `attributePaths` for one collection, then a separate query for the second. Or use DTO projections with a custom `@Query` that fetches only what the endpoint actually needs — avoids the problem entirely.
        * **Root cause understanding:** "Bags" in Hibernate are unordered, non-unique collections (`List`). Hibernate can't merge two bags from a Cartesian join correctly. Using `Set` (unique, unordered) resolves it.

    * **Follow-up (Senior): You discover your service is generating 200 queries per API call due to N+1 on a deeply nested entity graph (Orders → Items → Products → Categories). How do you diagnose this in production without adding log statements everywhere, and what's your systematic fix strategy?**
        * **Diagnosis without code changes:**
            * Enable `spring.jpa.show-sql=true` + `spring.jpa.properties.hibernate.format_sql=true` in dev to see all queries.
            * Add `p6spy` or `datasource-proxy` to intercept and count SQL at the JDBC layer — logs each query with call stack. No code changes.
            * In staging: use `Hibernate Statistics` (`hibernate.generate_statistics=true`) and expose via Actuator — shows `QueryExecutionCount`, `EntityLoadCount` per request.
            * In production: Prometheus `hikaricp_connections_active` spike correlates with N+1 storms.
        * **Systematic fix strategy:**
            1. Identify the aggregate root and all lazy associations accessed in the use case.
            2. For read endpoints: use DTO projections with a single `@Query` — fetch only the columns the API actually returns. No entity graph needed.
            3. For write endpoints that need the full entity: use `@EntityGraph` or `JOIN FETCH` with `DISTINCT`.
            4. For batch jobs: use `hibernate.default_batch_fetch_size=50` to reduce N queries to N/50.
        * **Golden rule for product companies:** Every repository method used in a read API should have a corresponding DTO projection query. Loading full entities for read-only endpoints is an anti-pattern at scale.


---
* [x] **Difference between save() and saveAndFlush()**
    * **save()** - Persists entity to the persistence context (Hibernate cache) but doesn't immediately write to the database. The actual INSERT/UPDATE happens when the transaction commits or flush() is called.
    * **saveAndFlush()** - Persists entity AND immediately executes the SQL statement to the database, bypassing the normal flush timing.

    * **Follow-up (Senior): Why would calling `save()` followed immediately by a `findById()` in the same transaction return stale data from Hibernate's first-level cache — and how do you force a fresh DB read?**
        * After `save()`, the entity lives in the first-level cache (session cache). A subsequent `findById()` returns the cached version directly without hitting the DB — even if another concurrent transaction modified the same row in between.
        * To force a fresh read: call `entityManager.refresh(entity)` — discards the cache entry and re-fetches from DB.
        * Alternatively, call `saveAndFlush()` first (writes to DB), then `entityManager.clear()` (clears session cache), then `findById()` — guaranteed fresh DB read.
        * **Second-level cache (shared across sessions):** For 150+ tenant scenarios, use `@CacheEvict` or `evict()` on the region after writes to prevent cross-session stale reads.

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
          user.getAuthorities());
          }
           ```
        * Your Controller
            * Spring Security = Filter Chain → Authentication → Authorization
                * Filters intercept requests before controllers
                * Authentication verifies identity (username/password)
                * Authorization checks permissions (roles/authorities)
                * SecurityContext stores authenticated user throughout request
                * Customizable via SecurityFilterChain configuration

    * **Follow-up (Senior): How does Spring Security store and propagate the authenticated user across threads — and why does this break in reactive WebFlux or async `@Async` methods?**
        * Spring Security uses `SecurityContextHolder` which defaults to `ThreadLocal` storage. The authenticated principal is available anywhere on the same thread.
        * **Breaks with async:** When `@Async` hands work to a thread pool thread, that thread has an empty `SecurityContext`. The principal is lost.
        * **Fix for @Async:** Configure `DelegatingSecurityContextAsyncTaskExecutor` — it copies the `SecurityContext` from the calling thread to the async thread.
        * **Fix for WebFlux:** `ThreadLocal` is entirely incompatible with reactive. Use `ReactiveSecurityContextHolder` and propagate via **Reactor Context** (`contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))`). Spring Security's WebFlux support does this automatically for you when using `SecurityWebFilterChain`.

    * **Follow-up (Senior): You need to implement a rate limiter at the Spring Security filter level — 100 requests per minute per user, returning 429 before even reaching the controller. How do you implement this without a third-party gateway?**
        * Create a custom `OncePerRequestFilter` that runs early in the `SecurityFilterChain` (before authentication filters):
        ```java
        @Component
        public class RateLimitFilter extends OncePerRequestFilter {
            private final RateLimiter rateLimiter; // backed by Redis (Redisson or Lettuce)

            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String key = extractKey(req); // IP or authenticated userId if available
                if (!rateLimiter.tryAcquire(key, 100, Duration.ofMinutes(1))) {
                    res.setStatus(429);
                    res.setHeader("Retry-After", "60");
                    res.getWriter().write("{\"error\": \"Too Many Requests\"}");
                    return; // short-circuit — don't call chain.doFilter
                }
                chain.doFilter(req, res);
            }
        }
        ```
        * Register it with `http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)`.
        * **Redis backing:** Use sliding window counter (`INCR` + `EXPIRE` with Lua script for atomicity) or token bucket via Redisson's `RRateLimiter`. Sliding window is more accurate under burst traffic.
        * **Distributed consideration:** Redis-backed rate limiter works across all pods. In-memory (`ConcurrentHashMap`) only rate-limits per pod — ineffective in a multi-replica deployment.

    * **Follow-up (Senior): Your Spring Security `@PreAuthorize` annotation is being ignored on a method. Walk through exactly why this happens and all possible fixes.**
        * `@PreAuthorize` is applied via AOP (`MethodSecurityInterceptor`). It only works when the method is called **through a Spring proxy** — same self-invocation restriction as `@Transactional`.
        * **Common failure modes:**
            1. **Method security not enabled:** Missing `@EnableMethodSecurity` on a `@Configuration` class. (In older code: `@EnableGlobalMethodSecurity(prePostEnabled = true)`.)
            2. **Self-invocation:** `this.securedMethod()` bypasses the proxy. Same fix as `@Transactional` — extract to another bean.
            3. **Method is `private`:** CGLIB can't proxy private methods. Annotating a private method has zero effect.
            4. **Bean created outside Spring context:** `new MyService()` has no proxy.
            5. **Wrong bean proxy mode:** The `@Configuration` class containing `@EnableMethodSecurity` must be processed before the target bean's `BeanPostProcessor`.
        * **Debugging:** Add `logging.level.org.springframework.security=DEBUG` — security decisions are logged with reasons.


---
* [x] **Why Custom Privileges/Scopes Instead of Spring's hasRole()?**
    * Spring's hasRole() is limited to simple role-based access (ADMIN, USER). Our application needs fine-grained
      permissions at feature level (like 'marketing:read', 'marketing:write') and subscription-based access (Basic,
      Pro, Enterprise). Custom implementation gives us flexibility to combine multiple conditions and support complex
      business rules that Spring's built-in annotations can't handle.

    * **Follow-up (Senior): In a multi-tenant SaaS (like Convonest), how do you implement row-level data isolation using Spring Security so that tenant A can never access tenant B's data — even if they have the same role?**
        * After authentication, extract the `tenantId` from the JWT claims and store it in a custom `Authentication` object or as an additional attribute in `SecurityContext`.
        * In the `@Service` layer, always apply a tenant filter automatically:
            * **Option 1 — Hibernate Filter:** Define a `@FilterDef` on entities with a `tenantId` parameter and enable it via `entityManager.enableFilter("tenantFilter").setParameter("tenantId", currentTenantId)` in a request-scoped interceptor.
            * **Option 2 — JPA Specification:** Always `AND tenantId = :current` via a `Specification<T>` combined with every query.
            * **Option 3 — Spring Data Method Security:** Use `@PostFilter` — but this filters in memory after the DB query, not efficient at scale.
        * **Most scalable:** Hibernate multi-tenancy with separate schemas or tenant-discriminator column + filter. Prevents any accidental cross-tenant query at the ORM layer, not just the service layer.
---
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

    * **Follow-up (Senior): JWT tokens are stateless — you can't invalidate them before expiry. In your Convonest OAuth2 implementation, how do you handle token revocation (e.g., user logs out or token is stolen)?**
        * Pure JWT has no server-side state, so true revocation isn't possible by default.
        * **Solution 1 — Short-lived access tokens + refresh token rotation:** Access token TTL of 5–15 minutes. On logout, delete the refresh token from the server (DB/Redis). The access token stays valid until expiry, but damage window is small.
        * **Solution 2 — Token denylist in Redis:** On logout, store `(jti claim → expiry)` in Redis. In the `JwtAuthenticationFilter`, after validating the signature, check if the `jti` is in the denylist. Reject if found. Redis TTL matches token expiry — denylist self-cleans.
        * **Solution 3 — Refresh token reuse detection (security):** Track each refresh token use. If an already-used refresh token is presented again (theft indicator), immediately revoke the entire token family.
        * **Your AES-256 + Azure Key Vault setup at Convonest is correct** for storing refresh tokens server-side — the key insight is that the stateless access token can expire naturally while the stateful refresh token is what you revoke.

    * **Follow-up (Senior): In a microservices architecture, every service validates the JWT independently. What are the security and operational risks of this model, and how does an internal token exchange (service-to-service) work securely without exposing user JWTs between services?**
        * **Risks of every service validating independently:**
            * Each service must fetch and cache the public key (JWKS endpoint) — if the auth server rotates keys, there's a window where services have stale keys.
            * If a bug exists in JWT validation logic, it must be patched across all services.
            * Services see the full user JWT — scope leakage risk if a compromised service forwards the token to unintended upstreams.
        * **Preferred pattern — Token exchange at gateway:**
            * API Gateway validates the external user JWT once (public key from JWKS).
            * For internal service-to-service calls, the gateway or calling service issues a short-lived **internal token** (signed with an internal key, limited scope) — the downstream service trusts this internal token, not the user JWT.
            * Downstream services never see the original user JWT — only internal tokens.
        * **Service-to-service authentication alternatives:**
            * **mTLS (mutual TLS):** Each service has a certificate. Istio/Envoy handles this transparently in a service mesh — no JWT propagation at all.
            * **OAuth2 Client Credentials:** Service A gets its own access token from the auth server (machine-to-machine) with scopes specific to service B. Service B validates it.
        * **Spring implementation:** Use Spring Security's `OAuth2AuthorizedClientManager` with `ClientCredentialsOAuth2AuthorizedClientProvider` to auto-manage token acquisition and refresh for service-to-service calls via `WebClient`.


### Microservices with Spring


* [x] **What is API Gateway pattern?**
    * API Gateway is a pattern where a single gateway handles all client requests and routes them to appropriate microservices, providing cross-cutting concerns like security, routing, and aggregation.

    * **Follow-up (Senior): In your 13-microservice Convonest architecture, what specific responsibilities did your API Gateway handle, and what should NOT be done in the gateway?**
        * **Should do in Gateway:** Request routing, SSL termination, authentication token validation (JWT signature check), rate limiting per client/IP, request/response logging for audit, correlation ID injection, load balancing between service instances.
        * **Should NOT do in Gateway:** Business logic (gateway becomes a bottleneck and a God service), complex data aggregation (use a BFF — Backend For Frontend pattern instead), DB calls, fine-grained authorization (push to the individual services).
        * **Spring Cloud Gateway specifics:** Uses WebFlux internally (non-blocking). `GlobalFilter` for cross-cutting concerns. `RouteLocator` for programmatic routing. `RequestRateLimiter` with Redis for distributed rate limiting. Predicates for path/header/method-based routing.

    * **Follow-up (Senior): In a microservices setup, how do you implement request tracing end-to-end across 13 services so that a single user request can be traced through every service hop in Grafana/Jaeger? What Spring libraries enable this, and what does the instrumentation look like in code?**
        * **Micrometer Tracing (Spring Boot 3.x)** replaces Spring Cloud Sleuth. Auto-instruments Spring MVC, WebClient, RestTemplate, Kafka, and DB queries — zero code changes in most cases.
        * Each service propagates `traceparent` / `X-B3-TraceId` headers automatically via `WebClient` or `RestTemplate` (when using the `ObservationRegistry`-aware variants).
        * **Setup:**
        ```yaml
        management.tracing.sampling.probability: 1.0  # 100% in dev, 0.1 in prod
        management.zipkin.tracing.endpoint: http://zipkin:9411/api/v2/spans
        ```
        * **Custom span for a critical business operation:**
        ```java
        @Autowired Tracer tracer;

        public Payment processPayment(PaymentRequest req) {
            Span span = tracer.nextSpan().name("payment.process").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                span.tag("payment.amount", req.getAmount().toString());
                return paymentGateway.charge(req);
            } finally {
                span.end();
            }
        }
        ```
        * **Kafka propagation:** Add `spring-kafka` + Micrometer instrumentation — trace ID is injected into Kafka message headers and extracted on the consumer side, maintaining the trace chain across async messaging.
        * **Production sampling:** 100% sampling is too expensive at scale. Use tail-based sampling (sample only requests that errored or exceeded SLA) via OpenTelemetry Collector with a filtering processor.

---
* [x] **Explain Circuit Breaker pattern (Resilience4j, Hystrix)**
    * Circuit Breaker prevents repeated calls to a failing service by opening the circuit after failures and providing fallback until recovery.
    * Resilience4j combines Circuit Breaker for fault isolation, Rate Limiter for traffic control, and Time Limiter for latency protection, ensuring system stability under failures.
  ```yaml
    resilience4j:
      circuitbreaker:
        instances:
          paymentCB:
             failureRateThreshold: 50        # open if >50% failures
             slidingWindowSize: 10
             minimumNumberOfCalls: 5
             waitDurationInOpenState: 5s     # OPEN → HALF-OPEN after 5s
             permittedNumberOfCallsInHalfOpenState: 2

    ratelimiter:
      instances:
        paymentRL:
          limitForPeriod: 5               # 5 requests
          limitRefreshPeriod: 1s          # per second

    timelimiter:
      instances:
        paymentTL:
          timeoutDuration: 2s             # timeout after 2s
  ```
  ```java
      @Service
      public class PaymentService {
  
      @CircuitBreaker(name = "paymentCB", fallbackMethod = "fallback")
      @RateLimiter(name = "paymentRL")
      @TimeLimiter(name = "paymentTL")
      public CompletableFuture<String> pay() {
  
          return CompletableFuture.supplyAsync(() -> {
              simulateRemoteCall();  // external service
              return "PAYMENT_SUCCESS";
          });
      }
  
      private void simulateRemoteCall() {
          try {
              Thread.sleep(3000); // >2s → TimeLimiter triggers
          } catch (InterruptedException ignored) {}
          throw new RuntimeException("Payment service down");
      }
  
      private CompletableFuture<String> fallback(Exception ex) {
          return CompletableFuture.completedFuture("PAYMENT_TEMPORARILY_UNAVAILABLE");
        }
      }
  ```
        * What happens internally (state-wise)
            * CLOSED → calls flow normally
            * Failures/timeouts increase → threshold crossed
            * OPEN → calls blocked immediately, fallback executed
            * After 5s → HALF-OPEN
            * 2 test calls:
                * success → CLOSED
                * failure → OPEN

        * RateLimiter
            * 5 calls/sec → request rejected immediately
        * TimeLimiter
            * Call >2s → timeout counted as failure

    * **Follow-up (Senior): The Circuit Breaker is OPEN and blocking calls. Your fallback returns a cached/default response. How do you distinguish between a "real" response and a fallback in the caller, and what metrics should alert your on-call engineer?**
        * Add a response header or field in the response body to mark fallback responses (e.g., `X-Fallback: true`). Callers can decide to retry later or show a degraded UI.
        * **Key Resilience4j metrics to export via Micrometer → Grafana:**
            * `resilience4j.circuitbreaker.state` — alert when state becomes `OPEN`
            * `resilience4j.circuitbreaker.failure.rate` — alert when failure rate trends above 40% (before threshold)
            * `resilience4j.circuitbreaker.calls` tagged by `kind=failed` — sudden spike indicates downstream degradation
            * `resilience4j.ratelimiter.available.permissions` — approaching 0 means rate limit is about to be hit
        * **Production pattern:** Page on OPEN state; only notify (not page) on elevated failure rate. OPEN state means a user-facing degradation is active.

    * **Follow-up (Senior): How do you test Circuit Breaker behaviour in a Spring Boot integration test without taking down a real downstream service?**
        * Use **WireMock** to stub the downstream service and simulate fault scenarios (500s, timeouts, connection refused).
        * Configure Resilience4j with a small `slidingWindowSize` (e.g., 3) and `minimumNumberOfCalls` (e.g., 2) for tests so the circuit opens quickly without needing hundreds of calls.
        * Assert on the `CircuitBreaker.getState()` after triggering failures, and assert that the fallback method's return value is what the test receives.
        ```java
        @Test
        void circuitShouldOpenAfterFailures() {
            wireMock.stubFor(get("/payment").willReturn(serverError()));
            
            IntStream.range(0, 5).forEach(i -> paymentService.pay());
            
            assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        }
        ```

    * **Follow-up (Senior): In a microservice mesh with 8 downstream dependencies, each having its own circuit breaker, how do you prevent cascading failures where a slow dependency causes thread pool exhaustion in the calling service — even with circuit breakers open?**
        * **The problem:** Even with a circuit breaker OPEN, if threads are blocked waiting for slow HTTP responses (before the breaker opens), those threads are consumed and unavailable for other calls. Thread pool exhaustion causes failures cascade to unrelated endpoints.
        * **Solution — Bulkhead pattern:** Isolate each downstream client into a separate thread pool (or semaphore). A slow Payment service only exhausts the Payment thread pool, not the global Tomcat pool.
        ```java
        resilience4j:
          bulkhead:
            instances:
              paymentService:
                maxConcurrentCalls: 10      # max 10 simultaneous in-flight calls
                maxWaitDuration: 50ms        # if all 10 busy, new callers wait 50ms then fail
              inventoryService:
                maxConcurrentCalls: 20
        ```
        * **Thread pool bulkhead vs semaphore:** Semaphore bulkhead limits concurrency but uses the calling thread (still vulnerable to thread blocking). Thread pool bulkhead uses a dedicated pool — calling thread returns immediately, downstream call runs in the pool thread. Prefer thread pool for truly non-blocking isolation.
        * **Combined stack:** `@CircuitBreaker` + `@Bulkhead` + `@TimeLimiter` on every external call. This is the production-hardened Resilience4j stack used at companies like Razorpay for payment service isolation.