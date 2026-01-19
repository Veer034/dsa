Design Patterns

* [x] **Singleton pattern - different ways to implement, thread-safe singleton**
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
