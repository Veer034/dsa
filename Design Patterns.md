Design Patterns
* [x] **Explain SOLID principles with real-world Java examples**
> **How to use this:** For each principle, read "The Problem First" before anything else.
> Every principle exists because someone got burned by code that didn't follow it.
> Understanding the pain makes the principle stick.

---

## Table of Contents

1. [S — Single Responsibility Principle](#s--single-responsibility-principle)
2. [O — Open/Closed Principle](#o--openclosed-principle)
3. [L — Liskov Substitution Principle](#l--liskov-substitution-principle)
4. [I — Interface Segregation Principle](#i--interface-segregation-principle)
5. [D — Dependency Inversion Principle](#d--dependency-inversion-principle)
6. [How They Work Together in Spring Boot](#how-they-work-together-in-spring-boot)
7. [Interview Cheat Sheet](#interview-cheat-sheet)

---

## S — Single Responsibility Principle

### The One-Line Definition
**A class should have only one reason to change.**

### The Problem First

Imagine you have a `UserService` that handles user registration. A new requirement comes in: when a user registers, send them a welcome email AND log the registration to an audit table. A junior developer adds both inside `UserService`. Six months later:

- The email team changes the email template format → `UserService` needs to change
- The compliance team changes the audit log format → `UserService` needs to change again
- A bug in the email logic breaks user registration entirely

One class, three reasons to change, three teams stepping on each other. This is what SRP prevents.

### The Violation

```java
// WRONG — UserService is doing three different jobs
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public void registerUser(User user) {
        // Job 1: business logic — validating and saving the user
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }
        userRepository.save(user);

        // Job 2: sending email — completely different concern
        // If the email server is down, registration fails. Why?
        String emailBody = "Welcome " + user.getName() + "! Your account is ready.";
        JavaMailSender mailSender = new JavaMailSenderImpl();
        // ... email sending code ...

        // Job 3: audit logging — yet another concern
        String logEntry = LocalDateTime.now() + " | USER_REGISTERED | " + user.getEmail();
        Files.write(Paths.get("/var/log/audit.log"), logEntry.getBytes());
    }
}
```

**Why this is a problem:**
- Email server goes down → registration breaks (they should be independent)
- Want to change from file-based audit log to database → must touch `UserService`
- Want to unit test registration logic → forced to mock email and file I/O too
- Three developers can't work on this independently without merge conflicts

### The Fix — SRP Applied

```java
// CORRECT — each class has exactly one reason to change

// Responsibility 1: User registration business logic only
@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       EmailService emailService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    public void registerUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }
        userRepository.save(user);
        emailService.sendWelcomeEmail(user);       // delegate — don't implement
        auditService.logRegistration(user);        // delegate — don't implement
    }
}

// Responsibility 2: Email sending only
// Changes here when email templates or providers change — nothing else
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendWelcomeEmail(User user) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Welcome to our platform!");
        message.setText("Hi " + user.getName() + ", your account is ready.");
        mailSender.send(message);
    }
}

// Responsibility 3: Audit logging only
// Changes here when audit format or destination changes — nothing else
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logRegistration(User user) {
        AuditLog log = new AuditLog(
            "USER_REGISTERED",
            user.getEmail(),
            LocalDateTime.now()
        );
        auditLogRepository.save(log);
    }
}
```

**What changed:**
- Email server down → registration succeeds, email just fails independently
- Audit format changes → touch only `AuditService`, zero risk to registration
- Unit test `UserService` → mock `EmailService` and `AuditService` cleanly, no real I/O needed
- Three developers can own three classes without conflict

### How to Explain This in an Interview

> *"SRP says a class should have one reason to change. The way I think about it practically: if I'm describing what a class does and I use the word 'and', that's a red flag. A UserService that registers users AND sends emails AND logs audits will break in three different ways for three different reasons. In Spring Boot, this naturally leads to separate `@Service` classes for each concern, wired together through constructor injection. The test story is the real tell — if your unit test has to mock email servers and file systems just to test user validation logic, your class is doing too much."*

---

## O — Open/Closed Principle

### The One-Line Definition
**A class should be open for extension but closed for modification.**

### The Problem First

You have a payment service that processes credit cards. A new requirement: also support PayPal. A developer opens `PaymentService` and adds an `if/else`. Three months later: also support UPI. Another `if/else`. Six months later: also support crypto. The method is now a 200-line `if/else` chain. Every new payment method requires touching — and potentially breaking — existing payment logic. A bug in the PayPal code block can accidentally affect credit card processing.

OCP says: when new requirements come in, you should be able to **add** new code without **changing** existing code.

### The Violation

```java
// WRONG — every new payment type requires modifying this class
@Service
public class PaymentService {

    public void processPayment(String paymentType, double amount) {
        if (paymentType.equals("CREDIT_CARD")) {
            System.out.println("Processing credit card payment of ₹" + amount);
            // ... credit card specific logic ...

        } else if (paymentType.equals("PAYPAL")) {
            System.out.println("Processing PayPal payment of ₹" + amount);
            // ... PayPal specific logic ...

        } else if (paymentType.equals("UPI")) {
            System.out.println("Processing UPI payment of ₹" + amount);
            // ... UPI specific logic ...

        }
        // Next requirement: add crypto → come back, add another else if
        // Every addition risks breaking everything above it
    }
}
```

### The Fix — OCP Applied

```java
// Step 1: Define the abstraction — the contract every payment method must follow
public interface PaymentProcessor {
    void process(double amount);
    String getPaymentType();  // used for Spring to pick the right bean
}

// Step 2: Each payment method is its own class — self-contained, never touches others
@Component
public class CreditCardProcessor implements PaymentProcessor {

    @Override
    public void process(double amount) {
        System.out.println("Processing credit card payment of ₹" + amount);
        // credit card gateway logic here
    }

    @Override
    public String getPaymentType() {
        return "CREDIT_CARD";
    }
}

@Component
public class PayPalProcessor implements PaymentProcessor {

    @Override
    public void process(double amount) {
        System.out.println("Processing PayPal payment of ₹" + amount);
        // PayPal API logic here
    }

    @Override
    public String getPaymentType() {
        return "PAYPAL";
    }
}

@Component
public class UpiProcessor implements PaymentProcessor {

    @Override
    public void process(double amount) {
        System.out.println("Processing UPI payment of ₹" + amount);
        // UPI gateway logic here
    }

    @Override
    public String getPaymentType() {
        return "UPI";
    }
}

// Step 3: PaymentService is now closed for modification
// It discovers all PaymentProcessor beans automatically via Spring
@Service
public class PaymentService {

    // Spring injects ALL beans that implement PaymentProcessor
    private final Map<String, PaymentProcessor> processorMap;

    public PaymentService(List<PaymentProcessor> processors) {
        // Build a lookup map: "CREDIT_CARD" → CreditCardProcessor, etc.
        this.processorMap = processors.stream()
            .collect(Collectors.toMap(
                PaymentProcessor::getPaymentType,
                processor -> processor
            ));
    }

    public void processPayment(String paymentType, double amount) {
        PaymentProcessor processor = processorMap.get(paymentType);
        if (processor == null) {
            throw new UnsupportedPaymentTypeException("Unknown payment type: " + paymentType);
        }
        processor.process(amount);
    }
}
```

**Adding crypto payment now:**

```java
// Just create a new class. Touch NOTHING else. Zero risk to existing payment logic.
@Component
public class CryptoProcessor implements PaymentProcessor {

    @Override
    public void process(double amount) {
        System.out.println("Processing crypto payment of ₹" + amount);
    }

    @Override
    public String getPaymentType() {
        return "CRYPTO";
    }
}
// Spring auto-discovers it. PaymentService picks it up automatically.
// CreditCard, PayPal, UPI code untouched. Cannot be broken.
```

### How to Explain This in an Interview

> *"OCP says extend without modifying. The practical smell is an if/else chain that grows every time a new type gets added — eventually you have a 200-line method where a bug in one branch risks breaking all the others. In Spring, the clean version is a strategy pattern with an interface and one implementation per type. Spring injects all implementations as a list, you build a map from type to processor at startup, and routing is just a map lookup. Adding a new payment method is just a new class — you don't touch the service, you can't break existing processors, and Spring discovers it automatically."*

---

## L — Liskov Substitution Principle

### The One-Line Definition
**A subclass should be fully substitutable for its parent class without breaking the program.**

### The Problem First

You have a `Bird` class with a `fly()` method. A developer creates an `Ostrich` class that extends `Bird`. Code that loops through a list of `Bird` objects and calls `fly()` suddenly throws an exception or prints "ostriches can't fly." The subclass broke the contract the parent established. LSP says: if you replace a parent with its child anywhere in your code, the behaviour should remain valid.

In Spring Boot, this matters most with repository patterns, service layers that accept parent types, and REST controllers that operate on base DTOs.

### The Violation

```java
// WRONG — Ostrich extends Bird but breaks the fly() contract
public class Bird {
    public void fly() {
        System.out.println("Bird is flying");
    }
}

public class Ostrich extends Bird {
    @Override
    public void fly() {
        // Ostrich can't fly — so what do we do?
        throw new UnsupportedOperationException("Ostriches cannot fly!");
        // This breaks every caller that trusted the Bird contract
    }
}

// This code breaks at runtime when an Ostrich is in the list
public class BirdController {
    public void makeBirdsFly(List<Bird> birds) {
        for (Bird bird : birds) {
            bird.fly(); // Throws exception for Ostrich — caller had no idea
        }
    }
}
```

### The Fix — LSP Applied

The fix is to model the hierarchy correctly — don't force a subtype to inherit behaviour it can't honestly fulfil.

```java
// Correct hierarchy — split the abstraction at the right boundary

// Base class: only what ALL birds can do
public abstract class Bird {
    public abstract void eat();
    public abstract void makeSound();
}

// Only birds that can actually fly implement this
public interface Flyable {
    void fly();
}

// Sparrow can fly — honest contract
@Component
public class Sparrow extends Bird implements Flyable {

    @Override
    public void fly() {
        System.out.println("Sparrow flying");
    }

    @Override
    public void eat() {
        System.out.println("Sparrow eating seeds");
    }

    @Override
    public void makeSound() {
        System.out.println("Sparrow chirping");
    }
}

// Ostrich cannot fly — honest contract, doesn't pretend otherwise
@Component
public class Ostrich extends Bird {

    @Override
    public void eat() {
        System.out.println("Ostrich eating plants");
    }

    @Override
    public void makeSound() {
        System.out.println("Ostrich booming");
    }
    // No fly() method — because Ostrich doesn't implement Flyable. Correct.
}

// Now the controller is honest about what it needs
@RestController
public class BirdController {

    // This only accepts birds that can actually fly — type-safe, no surprises
    public void makeFlyingBirdsFly(List<Flyable> flyingBirds) {
        for (Flyable bird : flyingBirds) {
            bird.fly(); // Always safe — everything in this list can fly
        }
    }
}
```

### A More Realistic Spring Boot Example — Notification Service

```java
// Base contract
public abstract class NotificationSender {
    // All subclasses MUST be able to send a message and return a delivery ID
    public abstract String send(String recipient, String message);
}

// SMS — honest implementation, fulfils contract fully
@Service
public class SmsNotificationSender extends NotificationSender {

    @Override
    public String send(String recipient, String message) {
        // call SMS gateway
        System.out.println("SMS sent to " + recipient + ": " + message);
        return "SMS-" + UUID.randomUUID();
    }
}

// Email — honest implementation, fulfils contract fully
@Service
public class EmailNotificationSender extends NotificationSender {

    @Override
    public String send(String recipient, String message) {
        // call email gateway
        System.out.println("Email sent to " + recipient + ": " + message);
        return "EMAIL-" + UUID.randomUUID();
    }
}

// This service works with ANY NotificationSender subclass
// Sparrow/Ostrich problem can't happen here because every subclass
// genuinely can send() and return a delivery ID
@Service
public class NotificationService {

    public void notifyUser(NotificationSender sender, String recipient, String message) {
        String deliveryId = sender.send(recipient, message);
        System.out.println("Notification delivered. ID: " + deliveryId);
    }
}
```

### How to Explain This in an Interview

> *"LSP says a subclass must be genuinely substitutable for its parent — not just syntactically, but behaviourally. The classic violation is a square-rectangle or ostrich-bird problem, but the real-world version I think about is: if someone passes a subclass where a parent is expected and the code silently breaks or throws an unexpected exception, you've violated LSP. The fix is usually a hierarchy design problem — you've put behaviour in the parent that not all children can honestly fulfil. Split the abstraction. In Spring, I think about this with service interfaces — if an implementation has to throw UnsupportedOperationException for a method on the interface, that's a strong signal the interface needs to be split."*

---

## I — Interface Segregation Principle

### The One-Line Definition
**A class should not be forced to implement methods it doesn't use.**

### The Problem First

You have one fat `WorkerInterface` that defines `work()`, `eat()`, and `sleep()`. A `Robot` class implements it. Robots don't eat or sleep. So the Robot class is forced to write meaningless empty implementations — or worse, throw exceptions — for methods that conceptually don't apply to it. Every time someone adds a method to `WorkerInterface`, every single implementor must update, even the ones that have nothing to do with that new method.

ISP says: keep interfaces small and focused. One interface per distinct behaviour.

### The Violation

```java
// WRONG — one fat interface forces all implementors to deal with everything
public interface ReportService {
    void generatePdfReport();
    void generateExcelReport();
    void generateCsvReport();
    void sendReportByEmail();
    void sendReportBySms();
    void archiveReport();
}

// This class only generates PDFs and sends email — but is forced to implement everything
@Service
public class PdfEmailReportService implements ReportService {

    @Override
    public void generatePdfReport() {
        System.out.println("Generating PDF...");
    }

    @Override
    public void generateExcelReport() {
        // This service doesn't do Excel. Forced to write this anyway.
        throw new UnsupportedOperationException("Excel not supported");
    }

    @Override
    public void generateCsvReport() {
        // Same problem
        throw new UnsupportedOperationException("CSV not supported");
    }

    @Override
    public void sendReportByEmail() {
        System.out.println("Sending email...");
    }

    @Override
    public void sendReportBySms() {
        // This service doesn't do SMS
        throw new UnsupportedOperationException("SMS not supported");
    }

    @Override
    public void archiveReport() {
        // This service doesn't archive
        throw new UnsupportedOperationException("Archiving not supported");
    }
}
// Every time a new method is added to ReportService, this class must change
// even if the new method has nothing to do with it
```

### The Fix — ISP Applied

```java
// Split into small, focused interfaces — each represents one distinct capability

public interface PdfReportGenerator {
    void generatePdfReport();
}

public interface ExcelReportGenerator {
    void generateExcelReport();
}

public interface CsvReportGenerator {
    void generateCsvReport();
}

public interface EmailReportSender {
    void sendReportByEmail();
}

public interface SmsReportSender {
    void sendReportBySms();
}

public interface ReportArchiver {
    void archiveReport();
}

// Now each service implements only what it actually does
// No fake methods, no UnsupportedOperationException, no dead code

@Service
public class PdfEmailReportService implements PdfReportGenerator, EmailReportSender {

    @Override
    public void generatePdfReport() {
        System.out.println("Generating PDF report...");
    }

    @Override
    public void sendReportByEmail() {
        System.out.println("Sending report by email...");
    }
    // That's it. Nothing else. This class only knows what it does.
}

@Service
public class ExcelSmsReportService implements ExcelReportGenerator, SmsReportSender {

    @Override
    public void generateExcelReport() {
        System.out.println("Generating Excel report...");
    }

    @Override
    public void sendReportBySms() {
        System.out.println("Sending report by SMS...");
    }
}

@Service
public class FullReportService implements PdfReportGenerator, ExcelReportGenerator,
                                          EmailReportSender, ReportArchiver {
    @Override
    public void generatePdfReport() { System.out.println("PDF..."); }

    @Override
    public void generateExcelReport() { System.out.println("Excel..."); }

    @Override
    public void sendReportByEmail() { System.out.println("Email..."); }

    @Override
    public void archiveReport() { System.out.println("Archiving..."); }
    // Implements exactly the four capabilities it actually has. No noise.
}

// Callers depend only on the interface they need — nothing more
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final PdfReportGenerator pdfGenerator;
    private final EmailReportSender emailSender;

    // This controller only cares about PDF generation and email sending
    // It has no idea about Excel, SMS, archiving — and it shouldn't
    public ReportController(PdfReportGenerator pdfGenerator,
                            EmailReportSender emailSender) {
        this.pdfGenerator = pdfGenerator;
        this.emailSender = emailSender;
    }

    @PostMapping("/send")
    public ResponseEntity<String> generateAndSend() {
        pdfGenerator.generatePdfReport();
        emailSender.sendReportByEmail();
        return ResponseEntity.ok("Report sent");
    }
}
```

### How to Explain This in an Interview

> *"ISP says don't force a class to implement methods it doesn't use. The smell is `UnsupportedOperationException` in an interface implementation — that's a class telling you 'I was forced to implement this but I have nothing to do here.' The fix is splitting fat interfaces into small, focused ones that represent a single capability. In Spring this plays out nicely because you can have a class implement multiple focused interfaces, and callers declare only the interface they need — the controller that just needs to generate PDFs doesn't need to know that the underlying service also handles SMS. It makes testing much cleaner too, because you mock only the interface the caller actually uses."*

---

## D — Dependency Inversion Principle

### The One-Line Definition
**High-level modules should not depend on low-level modules. Both should depend on abstractions.**

### The Problem First

Your `OrderService` (high-level business logic) directly creates and uses `MySQLOrderRepository` (low-level detail). Tomorrow you need to switch to PostgreSQL. Or add a MongoDB option for archived orders. Or write a unit test without a database. Every change to the database layer forces a change in `OrderService`. The business logic is tightly chained to infrastructure.

DIP says: the business logic should depend on an interface (abstraction), not a concrete database implementation. The concrete implementation is wired in from outside — this is exactly what Spring's dependency injection does.

### The Violation

```java
// WRONG — OrderService directly depends on a concrete MySQL class
@Service
public class OrderService {

    // Hard-wired to MySQL — cannot be tested without a real MySQL database
    // Cannot be swapped for PostgreSQL without changing this class
    private MySQLOrderRepository orderRepository = new MySQLOrderRepository();

    public Order placeOrder(OrderRequest request) {
        Order order = new Order(request.getProduct(), request.getQuantity());
        orderRepository.save(order);   // tightly coupled to MySQL
        return order;
    }
}

// Low-level detail — knows how to talk to MySQL specifically
public class MySQLOrderRepository {
    public void save(Order order) {
        // MySQL JDBC connection, SQL insert, etc.
        System.out.println("Saving order to MySQL: " + order);
    }
}
```

**Problems:**
- Unit testing `OrderService` requires a real MySQL connection — slow, fragile tests
- Switching to PostgreSQL means opening and changing `OrderService` — business logic shouldn't care about database choices
- Cannot have multiple implementations (e.g., in-memory for tests, MySQL for prod, archived orders to MongoDB)

### The Fix — DIP Applied (This Is Exactly What Spring Does)

```java
// Step 1: Define the abstraction — the contract between business logic and data layer
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(Long id);
    List<Order> findByStatus(String status);
}

// Step 2: MySQL implementation — a detail hidden behind the interface
@Repository
public class MySQLOrderRepository implements OrderRepository {

    @Override
    public void save(Order order) {
        System.out.println("Saving to MySQL: " + order);
        // JDBC or JPA implementation
    }

    @Override
    public Optional<Order> findById(Long id) {
        // MySQL query
        return Optional.empty();
    }

    @Override
    public List<Order> findByStatus(String status) {
        // MySQL query
        return List.of();
    }
}

// Step 3: PostgreSQL implementation — can be swapped in without touching OrderService
@Repository
@Profile("postgres") // activate this bean only when postgres profile is active
public class PostgresOrderRepository implements OrderRepository {

    @Override
    public void save(Order order) {
        System.out.println("Saving to PostgreSQL: " + order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<Order> findByStatus(String status) {
        return List.of();
    }
}

// Step 4: In-memory implementation for tests — no database needed
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<Long, Order> store = new HashMap<>();
    private long idCounter = 1;

    @Override
    public void save(Order order) {
        order.setId(idCounter++);
        store.put(order.getId(), order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findByStatus(String status) {
        return store.values().stream()
            .filter(o -> status.equals(o.getStatus()))
            .collect(Collectors.toList());
    }
}

// Step 5: OrderService depends on the abstraction — not any concrete implementation
// Spring injects the right bean at runtime based on active profile or configuration
@Service
public class OrderService {

    private final OrderRepository orderRepository; // depends on interface, not MySQL

    // Constructor injection — Spring resolves which implementation to use
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order placeOrder(OrderRequest request) {
        Order order = new Order(request.getProduct(), request.getQuantity());
        orderRepository.save(order);
        return order;
    }

    public Optional<Order> getOrder(Long id) {
        return orderRepository.findById(id);
    }
}

// Step 6: Unit test — no Spring context, no database, instant feedback
class OrderServiceTest {

    @Test
    void testPlaceOrder() {
        // Use in-memory implementation — no MySQL, no mocking framework needed
        OrderRepository inMemoryRepo = new InMemoryOrderRepository();
        OrderService orderService = new OrderService(inMemoryRepo);

        OrderRequest request = new OrderRequest("Laptop", 1);
        Order order = orderService.placeOrder(request);

        assertNotNull(order);
        // verify it was saved
        assertTrue(inMemoryRepo.findById(order.getId()).isPresent());
    }
}
```

### The Real Spring Boot Config — Wiring It All Together

```java
// application.properties for MySQL
spring.profiles.active=mysql

// application-mysql.properties
spring.datasource.url=jdbc:mysql://localhost:3306/orders
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

// application-postgres.properties
spring.profiles.active=postgres
spring.datasource.url=jdbc:postgresql://localhost:5432/orders
spring.datasource.driver-class-name=org.postgresql.Driver

// No change to OrderService for any of this.
// The abstraction (OrderRepository interface) absorbs the variation.
```

### How to Explain This in an Interview

> *"DIP says high-level business logic should depend on an interface, not a concrete implementation. The classic violation is instantiating a repository directly inside a service — suddenly your business logic is coupled to your database choice, and you can't test it without a real DB. In Spring, DIP is practically built in — you define a repository interface, write one or more implementations, and Spring injects the right one based on profile or configuration. Your `OrderService` never knows whether it's talking to MySQL, PostgreSQL, or an in-memory stub for testing. The abstraction absorbs the variation. It's one of those principles where following it and following Spring's own conventions leads you to the same place naturally."*

---

## How They Work Together in Spring Boot

A real feature — "User places an order, gets notified, and the event is audited" — using all five principles:

```
OrderController (REST layer)
    │
    ▼
OrderService (SRP: only orchestrates order placement)
    │
    ├──► OrderRepository (DIP: interface, not MySQL class)
    │         └── MySQLOrderRepository / PostgresOrderRepository (OCP: swap without changing service)
    │
    ├──► NotificationService (SRP: only sends notifications)
    │         └── EmailSender / SmsSender (ISP: separate interfaces per channel)
    │                   — both implement Notifiable (LSP: truly substitutable)
    │
    └──► AuditService (SRP: only logs events)
```

Each layer depends on abstractions. Each class has one job. Adding a new notification channel (WhatsApp) means a new class only. Adding a new DB means a new implementation only. Nothing existing changes.

---

## Interview Cheat Sheet

| Principle | One Line | The Smell | The Fix in Spring |
|---|---|---|---|
| **SRP** | One class, one reason to change | Using "and" to describe what a class does | Separate `@Service` classes per concern, wired via constructor injection |
| **OCP** | Extend without modifying | Growing `if/else` or `switch` chains on type | Strategy pattern: interface + `@Component` per type, `List<T>` injection |
| **LSP** | Subclass must be truly substitutable | `UnsupportedOperationException` in a subclass | Fix the hierarchy — split the parent or use composition |
| **ISP** | Implement only what you use | `UnsupportedOperationException` in an interface implementation | Split fat interfaces into small focused ones |
| **DIP** | Depend on interfaces, not implementations | `new ConcreteClass()` inside a service | Constructor injection of interfaces; Spring resolves the impl |

### The One Paragraph if Asked "Explain SOLID to Me"

> *"SOLID is five principles for writing code that's easy to change and easy to test. SRP says each class should do one thing so it only changes for one reason. OCP says you should be able to add new behaviour without modifying existing code — typically through interfaces and strategy patterns. LSP says if you extend a class, the subclass must be genuinely substitutable — callers shouldn't get surprises. ISP says keep interfaces small and focused so implementors only implement what they actually do. DIP says depend on abstractions, not concrete implementations — which in Spring means constructor-injecting interfaces so the business logic is decoupled from database or infrastructure choices. Together they point toward the same goal: small, focused classes that talk to each other through well-defined interfaces, making the system easy to extend, test, and reason about."*

---

*Prepared for Java Backend Lead / Tech Lead interview. Stack: Java 17 / Spring Boot 3.x.*
---
* [ ] **When would you use each GoF pattern? Give production examples**

  **Creational:**
  - **Builder** → constructing complex objects with many optional fields (`HttpRequest.Builder`, Lombok `@Builder`). Avoids telescoping constructors.
  - **Factory Method** → letting subclasses decide which object to instantiate. `DocumentParserFactory.getParser("pdf")` returns the right parser without the caller knowing the concrete type.
  - **Singleton** → shared stateless resources (thread pool, configuration registry). Dangerous with mutable state — prefer Spring-managed beans over hand-rolled singletons.

  **Structural:**
  - **Decorator** → adding behaviour without subclassing. Java I/O streams are the canonical example: `new BufferedReader(new FileReader(path))`.
  - **Proxy** → Spring AOP (`@Transactional`, `@Cacheable`) wraps your bean in a proxy that intercepts calls.
  - **Adapter** → integrating third-party APIs whose interfaces don't match yours. Wrap the external client in an adapter that implements your internal port interface.

  **Behavioural:**
  - **Strategy** → swappable algorithms at runtime. `SortStrategy`, `PricingStrategy`. Replaces large `if/else` or `switch` blocks.
  - **Observer** → event-driven updates. Spring `ApplicationEventPublisher` / `@EventListener`. Also `java.util.Observable` (legacy).
  - **Template Method** → define a skeleton algorithm in a base class, let subclasses fill in steps. Common in framework code (`JdbcTemplate`, `AbstractController`).
  - **Command** → encapsulate a request as an object for queuing, logging, or undo. Job queues, audit trails.

---
* [x] **When does the Singleton pattern break in Java?**
  1. **Multiple ClassLoaders** (e.g., OSGI, application servers) can each load a class, creating multiple "singletons".
  2. **Serialisation** — deserialising a singleton creates a new instance unless you override `readResolve()`.
  3. **Reflection** — `Constructor.setAccessible(true)` bypasses private constructors.
  4. **Mutable state** — a singleton with mutable state is a hidden global variable; concurrency bugs are hard to trace.
  5. **Safe idiom**: Use `enum` singleton (serialisation-safe, reflection-safe) or Spring-managed `@Component` (no hand-rolling needed).

```java
// Enum singleton — safest approach
public enum ConfigRegistry {
    INSTANCE;
    public String get(String key) { ... }
}
```
 
---
* [x] **Explain common anti-patterns and how you enforce against them in code reviews**
  - **God class** → one class doing everything. Enforce via review rule: if a class has more than ~5 dependencies injected, it's doing too much.
  - **Anemic domain model** → entities are pure data bags; all logic lives in service classes. Business rules then scatter across services and duplicate.
  - **Primitive obsession** → passing `String email`, `String phone` everywhere instead of `Email`, `PhoneNumber` value objects that carry their own validation.
  - **Leaky abstraction** → a service returning JPA `@Entity` objects to the controller layer. The controller now depends on persistence semantics (lazy loading, dirty checking). Use DTOs at the boundary.
  - **Shotgun surgery** → one change requires edits in 10 files. Sign that the abstraction is wrong.

  **Enforcement approach**: PR template checklist, ArchUnit tests to assert package dependency rules, SonarQube for complexity metrics.

---
* [x] **Singleton pattern - different ways to implement, thread-safe singleton**
```java
// Double-Checked Locking (Recommended)
public class Singleton {
  private static volatile Singleton instance;
  private Singleton() {}
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
}

//Bill Pugh Singleton (Best Practice)
public class Singleton {
    private Singleton() {}

    private static class Holder {
        private static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}


```
---
* [x] **Why Spring Singleton ≠ Gang of Four(GoF) Singleton pattern.**
    * GoF Singleton guarantees one instance per JVM, while Spring Singleton guarantees one instance per container. Spring deliberately avoids JVM singletons to preserve testability, DI, and lifecycle management.
---
* [x] **Factory vs Abstract Factory pattern**
    * Factory creates a single object, Abstract Factory creates a family of related objects ensuring consistency.
---
* [x] **Why Spring uses Factory internally?**
    * Spring uses Factory because only a Factory can decide what object to create, when to create it, how to wire it, and what to return instead of it (proxy).
---
* [x] **When to use Builder pattern?**
    * Use Builder when object construction is complex, has many optional parameters, or must be immutable and readable.
---
* [x] **Explain Strategy, Observer, and Decorator patterns**
    * Strategy selects behavior, Observer notifies changes, Decorator enhances behavior dynamically.
---
* [x] **What is Dependency Injection?**
    * **Dependency Injection** is a design pattern where an object's dependencies are provided (injected) by an external framework rather than the object creating them itself. This promotes loose coupling, makes code more testable, and allows easy swapping of implementations. In Spring, dependencies are typically injected via constructor, setter, or field injection using @Autowired.



* [x] **How do you decide between a monolith and microservices?**
  - **Start with a modular monolith**. Microservices solve operational and scaling problems, not architectural ones. They introduce distributed systems complexity — network latency, partial failures, distributed transactions, operational overhead — that most teams are not ready to manage from day one.
  - **Split when**: a specific module has meaningfully different scaling requirements, a team boundary maps cleanly to a service boundary (Conway's Law), or independent deployment velocity is genuinely blocked by the monolith.
  - **Anti-pattern to call out**: distributed monolith — services split by technology layer (all UI in one service, all DB in another) rather than by business domain. You get all the operational pain of microservices with none of the independence.
  - **Framework for the decision**: Can you deploy this module independently today? Does it have a different SLA or scaling curve? Does a separate team own it end-to-end? If no to all three — keep it in the monolith.

---
* [x] **Walk me through designing a high-throughput event processing service**

  *Example prompt a TL will get: "Design a service that processes 50,000 events/sec, guarantees ordering per entity, and must survive pod restarts."*

  **Key decisions and trade-offs:**

  1. **Ingestion**: Kafka partitioned by entity ID. Ordering guaranteed within a partition. Consumers scale horizontally up to partition count.
  2. **Consumer threading**: One consumer thread per Kafka partition (or use virtual threads). Don't share partitions across threads — ordering breaks.
  3. **Processing**: Keep handlers stateless. If state is needed (aggregations), use a local RocksDB store (Kafka Streams) or push state to Redis.
  4. **Back-pressure**: `max.poll.records` limits the batch size. Consumer lag metric (Grafana) is your leading indicator — not CPU or memory.
  5. **Exactly-once vs at-least-once**: Exactly-once (Kafka transactions) has overhead. At-least-once with idempotent consumers (upsert by event ID) is usually the pragmatic choice.
  6. **Pod restarts / rebalance**: Kafka partition rebalance triggers offset commit of the last successfully processed event. Uncommitted work is reprocessed — idempotency is mandatory.
  7. **Observability**: consumer lag per partition, processing latency p50/p99, DLQ message count.

---
* [x] **How do you approach technical debt as a tech lead?**
  - **Classify, don't just list**: Strategic debt (deliberate shortcut to ship faster — document it), accidental debt (poor decision made at the time), bit rot (code that degraded as the system around it changed). Each has a different remediation approach.
  - **Make it visible**: Tech debt in a backlog item competes with features and loses. Instead, link each debt item to a concrete risk (e.g., "this service has no retry logic — one downstream blip loses orders"). Business understands risk language.
  - **Boy scout rule at team scale**: every PR touching a file should leave it marginally better. Enforce via code review culture, not big-bang refactors.
  - **20% capacity rule**: negotiate with product to keep ~20% of sprint capacity for engineering health. Document what shipped because of this investment (e.g., "reduced P1 incident rate by 40% after replacing custom auth with Keycloak").
  - **Don't tolerate test debt**: untested code is unrefactorable code. Low coverage is the root cause of most other debt.

---
* [x] **How do you evaluate whether to use a framework/library vs building in-house?**
  - **Default to the library**. Maintenance cost of in-house code is almost always underestimated. The team that built it leaves; documentation rots.
  - **Build when**: the problem is genuinely core to your competitive differentiation, existing libraries impose architectural constraints you cannot live with, or the library is abandoned/unmaintained.
  - **Evaluation checklist**: community and maintenance health (last commit, open issues), licence compatibility, performance benchmarks under your load profile, CVE history and response time, Spring/Jakarta EE compatibility if relevant.
  - **Wrapping vs direct dependency**: wrap third-party libraries behind an internal port/adapter interface when switching them out is plausible. Don't wrap ubiquitous things like SLF4J or Jackson.
 