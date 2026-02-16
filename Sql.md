## MYSQL

### SQL Fundamentals


* [x] **Difference between INNER JOIN, LEFT JOIN, RIGHT JOIN, FULL OUTER JOIN**
    * INNER returns only matches, LEFT keeps all left rows, RIGHT keeps all right rows, and FULL OUTER keeps everything from both sides.
---
* [x] **What is self-join? Give an example**
    * A self join joins a table with itself using aliases, commonly used for hierarchical or relational data like employee–manager relationships.
        * Use case: Find each employee with their manager name.
        * ```
          SELECT e.name AS employee, m.name AS manager
          FROM Employee e
          LEFT JOIN Employee m
          ON e.manager_id = m.emp_id;

           ```
          * Use case: Find employees under a specific manager.
            ```
            SELECT e.name
            FROM Employee e
            JOIN Employee m
            ON e.manager_id = m.emp_id
            WHERE m.name = 'B';
            ```
---
* [x] **Explain GROUP BY and HAVING clause**
    * GROUP BY aggregates rows into groups, and HAVING filters those groups based on aggregate conditions.
* ```
  # Use case: Total salary per department
  SELECT dept, SUM(salary) AS total_salary
  FROM Employee
  GROUP BY dept;

  # Use case: Departments with total salary > 1,00,000
  SELECT dept, SUM(salary) AS total_salary
  FROM Employee
  GROUP BY dept
  HAVING SUM(salary) > 100000;

  ```
---
* [x] **Difference between WHERE and HAVING**
    * WHERE filters individual rows before aggregation, while HAVING filters aggregated results after GROUP BY.
    * ```
      FROM
      → JOIN
      → WHERE
      → GROUP BY
      → HAVING
      → SELECT
      → ORDER BY
      → LIMIT

      ```
---
* [x] **What are aggregate functions?**
    * Aggregate functions compute a single result from multiple rows, commonly used with GROUP BY.

###  Indexing & Performance

* [x] **What is an index? Types of indexes (B-Tree, Hash, Full-Text)**
    * An index is a data structure that speeds up data retrieval; B-Tree is used for range queries, Hash for exact matches, and Full-Text for text search.
---
* [x] **When should you create an index?**
    * Create indexes on frequently queried, high-selectivity columns used in WHERE, JOIN, or ORDER BY, especially in read-heavy workloads.
---
* [x] **Proper Index Search Rules (Composite Index)**
    * Composite indexes follow the leftmost prefix rule: filters must start from the first indexed column in order; skipping columns or using range conditions limits index usage.
    * Key rules summary :
        * Index works left → right
        * Skipping columns breaks usage
        * Range condition stops further columns
        * Non-index column filters run after index lookup

---
* [x] **What is covering index?**
    * A covering index is an index that contains all the columns required by a query, so the database can answer the query using only the index without accessing the table.
---
* [x] **Explain the difference between clustered and non-clustered index**
    * **Clustered Index:** Like a dictionary where words are physically arranged in alphabetical order. The data IS the index.
    * **Non-Clustered Index:** Like a book's index at the back - it tells you page numbers where topics appear, but the actual content is stored elsewhere.
---
* [x] **How does EXPLAIN plan work?**
    * EXPLAIN shows you how the database will execute your query - which indexes it'll use, how tables will be joined, estimated rows scanned, etc. It's like seeing the database's game plan before running the query.
---
* [x] **What are slow query logs and how to analyze them?**
    * Slow query logs capture long-running queries, which are analyzed using tools and EXPLAIN plans to optimize indexes and query structure.

### Transactions & Isolation

* [x] **Explain ACID properties**
    * ACID ensures that database transactions execute atomically, preserve schema consistency, remain isolated from concurrent operations, and are permanently durable after commit.
---
* [x] **What are transaction isolation levels? (Read Uncommitted, Read Committed, Repeatable Read, Serializable)**
    * **Read Uncommitted:** A transaction can read uncommitted changes from others (dirty reads allowed).
    * **Read Committed:** A transaction reads only committed data, but the same query may return different results if data changes.
    * **Repeatable Read:** Rows read once will not change within the transaction, but new matching rows may appear.
    * **Serializable:** Transactions are fully isolated and behave as if executed one after another.

    * **Key Differences MySQL and Oracle Isolation:**

  | Aspect | MySQL (InnoDB) | Oracle |
    |--------|---------------|---------|
  | **Default Level** | Repeatable Read | Read Committed |
  | **Snapshot taken** | At transaction start | At each query |
  | **Phantom reads in default** | ❌ Prevented | ✅ Possible |
  | **MVCC approach** | Transaction-level snapshot | Statement-level snapshot |
  | **Read vs Write** | Readers don't block writers, writers don't block readers | Same |
  | **Serializable** | Uses locks | Uses snapshot + conflict detection |

    * **Which is better?**
        * **MySQL's approach:** Better for consistency within a transaction - you get a stable view
        * **Oracle's approach:** Better for seeing the latest data - you always see current committed values
    * Both are valid! It depends on your application needs. Financial systems often prefer MySQL's approach for consistency, while reporting systems might prefer Oracle's approach to see latest data.

---
* [x] **What is MVCC(Multi-Version Concurrency Control)?**
    * Instead of locking data when someone reads it, the database keeps multiple versions of each row so readers see an old "snapshot" while writers create new versions - no waiting needed!
    * **How Database Stores This:**
        * **MySQL (InnoDB):**
            * Stores old versions in the **undo log**
            * Each row has hidden columns: `DB_TRX_ID` (transaction ID), `DB_ROLL_PTR` (pointer to old version)
            * When you read, MySQL follows the pointer chain backward to find your version

        * **Oracle:**
            * Stores old versions in **undo tablespace**
            * Each transaction gets a System Change Number (SCN)
            * Reads data as it was at your SCN
        * **Benefits of MVCC:**
            * ✅ **Readers never block writers** - they see old versions
            * ✅ **Writers never block readers** - they create new versions
            * ✅ **No read locks needed** - much faster!
            * ✅ **Consistent snapshots** - you see stable data throughout your transaction

---
* [x] **What are dirty read, non-repeatable read, and phantom read?**
    * **Dirty Read:** Reading data that another transaction has changed but NOT YET COMMITTED (might get rolled back).
    * **Non-Repeatable Read:** Reading the same row twice in one transaction and getting different values because
      another transaction modified and committed it in between..
    * **Phantom Read:** Running the same query twice and getting different number of rows because another transaction
      inserted or deleted rows in between.

---
* [x] **Difference between COMMIT and ROLLBACK?**
    * COMMIT permanently saves all transaction changes to the database, while ROLLBACK discards all changes and restores the database to its state before the transaction started.
---
* [x] **What is deadlock in database and how to prevent it?**
    * A deadlock occurs when two or more transactions are waiting for each other to release locks, creating a circular wait condition where none can proceed.
    * **How to prevent Deadlock:**
        * accessing resources in a consistent order across all transactions.
        * keeping transactions short.
        * using  appropriate isolation levels.
        * implementing timeout and retry mechanisms.

### Database Design

* [x] **What is normalization? Explain 1NF, 2NF, 3NF, BCNF**
    * Normalization is the process of organizing database tables to reduce redundancy and dependency by dividing larger tables into smaller ones and defining relationships between them.
    * **1NF(Normal Form):** A table is in 1NF if all columns contain only atomic (indivisible) values, and each column contains values of a single type with no repeating groups.
  ```
  ❌ Not 1NF:
  Student | Courses
  --------|------------------
  John    | Math, Physics, Chemistry  (multiple values!)
  
  ✅ 1NF:
  Student | Course
  --------|----------
  John    | Math
  John    | Physics
  John    | Chemistry
  ```
    * **2NF(Second Normal Form):** A table is in 2NF if it's in 1NF and eliminates partial dependencies - all non-key
      attributes must depend on the entire composite primary key, not just part of it.
  ```
  ❌ Not 2NF (partial dependency):
  StudentID | CourseID | StudentName | CourseName | Grade
  ----------|----------|-------------|------------|-------
  1         | 101      | John        | Math       | A
  1         | 102      | John        | Physics    | B
  
  Primary Key: (StudentID, CourseID) - COMPOSITE KEY

  Problem:
   - StudentName depends on StudentID only (not full key) ❌
   - CourseName depends on CourseID only (not full key) ❌
  
  ✅ 2NF (remove partial dependencies):
  Students:    StudentID | StudentName
  Courses:     CourseID  | CourseName
  Enrollments: StudentID | CourseID | Grade
  ```
    * **3NF (Third Normal Form):** A table is in 3NF if it's in 2NF and eliminates transitive dependencies - non-key
      attributes must depend only on the primary key, not on other non-key attributes
  ```
  ❌ Not 3NF (transitive dependency):
  EmployeeID (PK) | DeptID | DeptName
  ----------------|--------|----------
  1               | 10     | Sales
  2               | 20     | IT
  3               | 10     | Sales

  Primary Key: EmployeeID - SINGLE KEY (not composite)

  Problem:
  - DeptName depends on DeptID (non-key → non-key) ❌
  - Both DeptID and DeptName are non-key attributes
  
  ✅ 3NF (remove transitive dependency):
  Employees:   EmployeeID | DeptID
  Departments: DeptID     | DeptName
  ```
    * **BCNF(Boyce–Codd Normal Form):** A table is in BCNF if it's in 3NF and for every functional dependency X→Y, X must be a super key (candidate key).
---
* [x] **When to denormalize?**
    * Denormalize when read performance and simplicity matter more than strict normalization, and the system can tolerate controlled redundancy.
    * **Denormalize when:**
        * **Read-heavy workloads:** Far more reads than writes (e.g., reporting, analytics).
        * **Joins are a bottleneck:** High-latency or frequent joins on hot paths.
        * Low write frequency / tolerant to eventual consistency
        * **Precomputed aggregates are needed:** Counters, totals, rankings (avoid GROUP BY on every read).
        * **Caching layers / NoSQL models:** Redis, DynamoDB, Elasticsearch favor denormalized models.
        * **Scale limits of normalization:** At very high QPS, fewer lookups beat perfect normalization.
---
* [x] **What is ER diagram?**
    * An ER diagram models entities, attributes, and relationships to design a database schema visually before implementation.
    * Key components
        * Entity – real-world object (e.g., Employee, Department)
        * Attribute – properties of an entity (e.g., id, name)
        * Relationship – how entities are connected (e.g., works_in)
        * Cardinality – one-to-one, one-to-many, many-to-many
---
* [x] **Primary key vs Foreign key vs Unique key**
    * **Primary Key:** Uniquely identifies each row in a table; cannot be NULL and only one primary key exists per table.
    * **Foreign Key:** References a primary/unique key in another table to maintain referential integrity; duplicates and NULL may be allowed.
    * **Unique Key:** Ensures all values in a column are unique; multiple unique keys are allowed and NULL is usually permitted (DB-specific).
---
* [x] **What is composite key?**
    * A composite key is a primary or candidate key made up of two or more columns together that uniquely identify a row, where no single column is sufficient on its own.

### Advanced Concepts

* [x] **What are stored procedures and functions?**
    * Procedures are action-oriented, while functions are value-returning and can be used inside SELECT queries.
    * **Stored Procedure:** A precompiled set of SQL statements stored in the database, used to perform operations and may return multiple values or result sets.
    * **Function:** A database routine that returns a single value and is mainly used in SQL expressions.

---
* [x] **What are triggers? When to use them?**
    * A trigger is a database object that automatically executes when a specific event occurs on a table (e.g., INSERT, UPDATE, DELETE).
    * **When to use triggers:**
        * **Audit & logging** – track data changes automatically.
        * **Enforce complex rules** – validations not possible via constraints.
        * **Maintain derived data** – auto-update summary tables.
        * **Security control** – prevent unauthorized data changes.
    * **When NOT to use:**
        * Business logic (hard to debug, hidden execution).
        * High-throughput tables (performance overhead).
        * When application code can handle it clearly.

---
* [x] **Explain views - materialized vs regular views**
    * **Regular View:**
        * A regular view is a stored SQL query that does not store data and is executed every time it is queried.
        * Use cases: simplify complex joins, enforce security by exposing limited columns/rows.
  ```
  CREATE VIEW active_users AS
  SELECT id, name FROM users WHERE active = true;
  ```
    * **Materialized View:**
        * A materialized view stores the actual query result and must be refreshed to stay updated.
        * **Use cases:** reporting, analytics, and performance optimization for heavy aggregations.
  ```
  CREATE MATERIALIZED VIEW sales_summary AS
  SELECT product_id, SUM(amount) FROM sales GROUP BY product_id;
  ```

---
* [x] **What is partitioning? Types of partitioning**
    * Partitioning is a database technique where a large table is split into smaller logical parts (partitions) based on a key, while appearing as a single table to queries.
      Use cases: improve query performance, faster maintenance, and efficient data management on large datasets.
    * **Types of Partitioning**
        * Range partitioning – data split by value ranges (e.g., date-wise partitions).
        * List partitioning – data split by discrete values (e.g., region = IN, US).
        * Hash partitioning – data distributed using a hash function for uniform load.
        * Composite partitioning – combination of two methods (e.g., range + hash).
---
* [x] **How to handle schema migrations in production?**
    * Production migrations must be backward-compatible and zero-downtime, using expand-migrate-contract and batch backfills. Never couple schema changes tightly with application deployment.
    * **Best Practices (Production-safe):**
        * **Backward-compatible changes first:** Add new columns/tables without breaking old code.
        * **Expand → Migrate → Contract pattern**
            * Expand: add column/index
            * Migrate: backfill data in batches
            * Contract: remove old column later
        * **Zero-downtime migrations:**  Avoid table locks; use online DDL.
          ```
          ALTER TABLE users ADD COLUMN age INT, ALGORITHM=INPLACE, LOCK=NONE;
          ```
        * **Rollback strategy:** Every migration must have a safe rollback or forward fix.

### Replication & Scaling

* [x] **Explain master-slave replication**
    * Master–slave replication is a setup where a single master handles all writes, and one or more slaves replicate the data and serve read queries.
    * Changes are propagated using redo/binlogs, usually asynchronously, which can cause replication lag.
    * Used for read scaling, high availability, and offloading reporting/backup workloads, but it does not scale writes.
---
* [x] **What is read replica?**
    * A read replica is a copy of the primary database that replicates data from the master and is used only for read operations.
    * It improves read scalability and availability by offloading SELECT queries from the primary.
    * Since replication is usually asynchronous, replicas may return slightly stale data.
---
* [x] **How to scale MySQL databases?**
    * MySQL scales reads via replicas, writes via sharding, and performance via caching and workload separation.
    * **Vertical scaling (Scale-up)**
        * Increase CPU/RAM/IO of a single MySQL instance.
        * ✔ Simple, ❌ hardware limit.
    * **Read scaling (Read replicas)**
        * Use replicas for SELECT, master for writes.
        * ✔ High read throughput, ❌ write bottleneck & replica lag.
    * **Sharding (Horizontal scaling)**
        * Split data across multiple MySQL servers using a shard key (e.g., user_id % N).
        * ✔ Scales writes, ❌ complex queries & app awareness.
    * **Caching layer**
        * Use Redis/Memcached for hot reads.
        * ✔ Reduces DB load, ❌ cache invalidation complexity.
    * **Offload search/analytics**
        * Send search & reporting queries to Elasticsearch/OLAP DB.
        * ✔ Keeps MySQL for OLTP only.
---
* [x] **Difference between vertical and horizontal scaling**
    * **Vertical Scaling (Scale-up)**
        * Vertical scaling means adding more resources to a single machine (CPU, RAM, disk).
        * **Use case:** quick performance boost for a single DB or service; simple but limited by hardware.

    * **Horizontal Scaling (Scale-out)**
        * Horizontal scaling means adding more machines/nodes and distributing load or data across them.
        * **Use case:** large systems needing high availability and unlimited growth; complex but highly scalable.
