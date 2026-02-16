## SPRING BOOT

### Core Spring Concepts

* [x] **Explain IoC (Inversion of Control) and Dependency Injection**
    * **IoC (Inversion of Control)** is a principle where the control of object creation and lifecycle management is inverted from the application code to a framework/container. Instead of your code creating objects with new, the framework controls this.
    * **Dependency Injection** is a specific implementation of IoC. It's how the container actually provides (injects) the dependencies your objects need.


---
* [x] **Difference between @Component, @Service, @Repository, @Controller**
    * All four create Spring beans; @Component is generic, @Service is for business logic, @Repository is for data access (with exception translation), and @Controller is for handling web requests.


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


---
* [x] **What is ApplicationContext vs BeanFactory?**
    * BeanFactory is the basic IoC container providing lazy bean initialization, while ApplicationContext is an advanced container that extends BeanFactory with eager initialization, internationalization, event propagation, and AOP support - it's the preferred choice for enterprise applications.


---
* [x] **Explain bean scopes (Singleton, Prototype, Request, Session)**
    * **Singleton (default)** - One instance per Spring container, shared across the application.
    * **Prototype** - New instance created every time the bean is requested.
    * **Request** - One instance per HTTP request (web applications only).
    * **Session** - One instance per HTTP session (web applications only).


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

---
* [x] **Difference between @Autowired, @Inject, and @Resource**
    * **@Autowired** - Spring-specific, injects by type, requires @Qualifier for name-based injection, has required attribute.
    * **@Inject** - JSR-330 standard, injects by type, uses @Named for disambiguation, no required attribute.
    * **@Resource** - JSR-250 standard, injects by name first (via name attribute) then type, more concise for name-based injection.

### Spring Boot Specifics

* [x] **What is auto-configuration in Spring Boot?**
    * Spring Boot Auto-configuration automatically configures beans based on classpath dependencies, application properties, and existing beans, so you don’t need manual configuration.
    * For example, if **spring-boot-starter-data-jpa** is on the classpath, Boot auto-configures DataSource, EntityManager, and JpaTransactionManager automatically.
---
* [x] **Explain @SpringBootApplication annotation**
    * @SpringBootApplication enables auto-configuration, component scanning, and Java-based configuration to start a
      Spring Boot application with minimal setup.
---
* [x] **How does Spring Boot differ from Spring Framework?**
    * **Spring Framework** is a core framework that provides DI, AOP, MVC, and transaction management, but requires manual configuration.
---
* [x] **What are Spring Boot Starters?**
    * Spring Boot Starters are dependency descriptors that bundle commonly used libraries together. Instead of adding multiple individual dependencies, you add one starter that includes everything needed for a specific functionality.
---
* [x] **Explain application.properties vs application.yml**
    * Both are Spring Boot configuration files, but differ in format. Use `.yml` for complex configurations with deep
      nesting, `.properties` for simple configs or when team prefers it. If both exist, `.properties` takes precedence

### REST APIs

* [x] **Difference between @RestController and @Controller**
    * @Controller - Used for traditional MVC applications that return views (HTML pages). Requires @ResponseBody on methods to return data directly.
    *  @RestController - Combination of @Controller + @ResponseBody. Used for REST APIs that return data (JSON/XML),
       not views.
---
* [x] **What are @PathVariable, @RequestParam, @RequestBody?**
    * @PathVariable → URL path,
    * @RequestParam → query parameters,
    * @RequestBody → request payload.
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

---
* [x] **Explain HTTP methods and their idempotency**
    * **Idempotent** means: making the same request multiple times results in the same server state.
    * **Key Interview Points**
        * Idempotent ≠ Safe (DELETE is idempotent but not safe).
        * GET, HEAD are safe and idempotent.
        * PUT is idempotent because it replaces state.
        * POST is non-idempotent by design (multiple creates).
        * PATCH is partial update

### Spring WebFlux (Reactive)

---
* [x] **Difference between blocking vs non-blocking I/O**
    * Blocking I/O ties up threads while waiting; non-blocking I/O frees threads and scales better.
---
* [x] **When to use WebFlux vs Spring MVC?**
    * Use MVC for simplicity and WebFlux for massive concurrency with non-blocking I/O.
---
* [x] **Explain Mono and Flux**
    * Mono is for one result, Flux is for many results—both are lazy and non-blocking.
---
* [x] **What is backpressure in reactive programming?**
    * Backpressure prevents fast producers from overwhelming slow consumers by controlling data flow.
    * Reactive pipelines handle backpressure automatically; use onBackpressureX only when the producer cannot slow down.
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



---
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
---
* [x] **Why Custom Privileges/Scopes Instead of Spring's hasRole()?**
    * Spring's hasRole() is limited to simple role-based access (ADMIN, USER). Our application needs fine-grained
      permissions at feature level (like 'marketing:read', 'marketing:write') and subscription-based access (Basic,
      Pro, Enterprise). Custom implementation gives us flexibility to combine multiple conditions and support complex
      business rules that Spring's built-in annotations can't handle.
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


### Microservices with Spring


* [x] **What is API Gateway pattern?**
    * API Gateway is a pattern where a single gateway handles all client requests and routes them to appropriate microservices, providing cross-cutting concerns like security, routing, and aggregation.
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
