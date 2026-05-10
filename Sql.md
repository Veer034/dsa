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

---

## Advanced Topics for 11+ Years Experienced Engineers

### Query Optimization & Execution Internals

* [ ] **A query that was fast 6 months ago is now 10x slower. How do you diagnose and fix it without downtime?**
    * This is a classic production scenario — data growth breaks query plans.
    * **Step 1: Capture the problem**
        ```sql
        -- Enable slow query log
        SET GLOBAL slow_query_log = 'ON';
        SET GLOBAL long_query_time = 1;  -- log queries >1 second
        SET GLOBAL slow_query_log_file = '/var/log/mysql/slow.log';

        -- Analyze with pt-query-digest (Percona Toolkit)
        pt-query-digest /var/log/mysql/slow.log | head -100
        ```
    * **Step 2: Get the execution plan**
        ```sql
        EXPLAIN FORMAT=JSON SELECT ...;
        -- Or use EXPLAIN ANALYZE (MySQL 8.0+) for actual runtime stats
        EXPLAIN ANALYZE SELECT o.id, u.name
        FROM orders o JOIN users u ON o.user_id = u.id
        WHERE o.status = 'pending' AND o.created_at > '2026-01-01';
        ```
        * **Key fields to look at:**
            * `type`: Should be `ref` or `range`. `ALL` (full scan) = problem.
            * `rows`: Estimated rows scanned — if this is millions, you need an index.
            * `Extra`: `Using filesort` or `Using temporary` = expensive operations.
    * **Step 3: Check index usage and statistics**
        ```sql
        SHOW INDEX FROM orders;

        -- Check if statistics are stale (common cause!)
        ANALYZE TABLE orders;  -- recalculates statistics

        -- Check when stats were last updated
        SELECT table_name, update_time FROM information_schema.tables
        WHERE table_schema = 'your_db' AND table_name = 'orders';
        ```
        * **Stale statistics** are the #1 hidden cause of sudden query regressions — MySQL's optimizer uses them to choose query plans.
    * **Step 4: Fix without downtime**
        ```sql
        -- Add index online (MySQL 5.6+ InnoDB)
        ALTER TABLE orders
        ADD INDEX idx_status_created (status, created_at),
        ALGORITHM=INPLACE, LOCK=NONE;

        -- Or use pt-online-schema-change (no lock, works on all MySQL versions)
        pt-online-schema-change --alter "ADD INDEX idx_status_created (status, created_at)" \
          D=mydb,t=orders --execute

        -- Or gh-ost (GitHub's online DDL tool — preferred at scale)
        gh-ost --table=orders --alter="ADD INDEX idx_status_created (status, created_at)" ...
        ```
    * **Force index if optimizer makes wrong choice:**
        ```sql
        SELECT * FROM orders FORCE INDEX (idx_status_created)
        WHERE status = 'pending' AND created_at > '2026-01-01';
        ```

---

* [ ] **Explain the difference between a nested loop join, hash join, and merge join. When does MySQL choose each?**
    * The join algorithm chosen by the optimizer dramatically affects query performance on large tables.
    * **Nested Loop Join (NLJ) — MySQL's primary algorithm:**
        * For each row in the outer table, scan (or index-lookup) the inner table.
        * **Complexity:** O(N × M) for full scan, O(N × log M) with index on inner table.
        * **MySQL uses NLJ when:** Both tables have appropriate indexes, result sets are small-moderate.
        ```sql
        -- Index nested loop join (fast)
        SELECT * FROM orders o JOIN users u ON o.user_id = u.id WHERE o.status = 'shipped';
        -- MySQL scans filtered orders, does index lookup on users for each row
        ```
    * **Block Nested Loop (BNL) — when inner table has no usable index:**
        * Loads chunks of the outer table into `join_buffer`, scans inner table once per chunk.
        * Controlled by `join_buffer_size` (default 256KB, increase for large joins).
        * **Optimization:** Add an index on the join column to convert BNL → NLJ.
    * **Hash Join (MySQL 8.0.18+):**
        * Build a hash table from the smaller table in memory, probe it with rows from the larger table.
        * **O(N + M)** — much faster than NLJ for large tables without indexes.
        * MySQL uses hash join when no index is available for the join condition (replaces BNL).
        ```sql
        -- Force hash join (MySQL 8.0+)
        SELECT /*+ HASH_JOIN(o u) */ * FROM orders o JOIN users u ON o.user_id = u.id;
        ```
    * **Merge Join:** MySQL doesn't natively support merge join (MariaDB does). Simulated via sorted index scans.
    * **Decision Framework:**
      | Scenario | Algorithm |
      |----------|-----------|
      | Index on join column | Index Nested Loop |
      | No index, small table | Hash Join (8.0+) |
      | No index, old MySQL | Block Nested Loop |
      | Both tables sorted on join key | Merge Join (MariaDB) |

---

* [ ] **How does InnoDB's MVCC work internally, and what happens to undo logs over time?**
    * **InnoDB Row Versioning:**
        * Every InnoDB row has two hidden columns: `DB_TRX_ID` (ID of last transaction to modify the row) and `DB_ROLL_PTR` (pointer to the undo log entry for the previous version).
        * When a transaction modifies a row, InnoDB: writes old row data to undo log, updates the row with new data and current transaction ID, sets `DB_ROLL_PTR` to point to the undo log entry.
    * **Read View (Snapshot):**
        * When a transaction starts a consistent read, InnoDB creates a **Read View** containing: list of active transaction IDs at that moment, min and max transaction IDs.
        * For each row read, InnoDB checks: if `DB_TRX_ID` is visible to the Read View → return the row. Otherwise, follow `DB_ROLL_PTR` chain through undo log until finding a visible version.
    * **Undo Log Growth — the hidden danger:**
        * Long-running transactions prevent undo log purging.
        * The purge thread can only clean undo logs for transactions older than the oldest active Read View.
        ```sql
        -- Check undo log size
        SHOW ENGINE INNODB STATUS\G
        -- Look for: "History list length 50000" -- dangerous if growing

        -- Find the culprit: long-running transaction
        SELECT trx_id, trx_started, trx_query, trx_rows_locked
        FROM information_schema.INNODB_TRX
        ORDER BY trx_started ASC LIMIT 10;
        ```
    * **Production Impact of Undo Log Bloat:**
        * Reads slow down because they must traverse longer version chains.
        * Disk space consumed by growing undo tablespace.
        * **Fix:** Kill long-running transactions. Set `innodb_max_undo_log_size`. Enable `innodb_undo_log_truncate`.
    * **Recommended Settings:**
        ```sql
        SET GLOBAL innodb_undo_log_truncate = ON;
        SET GLOBAL innodb_purge_rseg_truncate_frequency = 128;
        -- Monitor
        SELECT NAME, SUBSYSTEM, COUNT FROM INFORMATION_SCHEMA.INNODB_METRICS
        WHERE NAME LIKE '%undo%';
        ```

---

### Large-Scale Schema Design

* [ ] **How would you design a multi-tenant SaaS database schema that supports 10,000 tenants with data isolation, without separate databases per tenant?**
    * **The Three Approaches:**
    * **Approach 1: Shared Schema (Column-based tenancy):**
        ```sql
        CREATE TABLE orders (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            tenant_id INT NOT NULL,   -- ← tenancy discriminator
            order_number VARCHAR(50),
            amount DECIMAL(10,2),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_tenant_order (tenant_id, created_at),
            INDEX idx_tenant_status (tenant_id, status)
        );
        ```
        * All tenants share one table. Every query MUST include `tenant_id` in WHERE clause.
        * **Risk:** Missing `tenant_id` filter = data leak across tenants.
        * **Mitigation:** Use Row-Level Security (available in PostgreSQL natively; in MySQL via application-level enforcement or views).
    * **Approach 2: Schema-per-tenant (MySQL databases per tenant):**
        ```sql
        CREATE DATABASE tenant_1234;
        -- Each tenant gets their own set of tables
        ```
        * **Pros:** Strong isolation, easy backup per tenant, schema migrations can be per-tenant.
        * **Cons:** 10,000 databases × N tables = connection pool explosion, difficult cross-tenant analytics.
    * **Recommended Hybrid for 10K tenants:**
        * Shared schema for small/medium tenants.
        * Dedicated schema/database for "enterprise" tenants (SLA-based isolation).
        * **Application-level enforcement via middleware:**
        ```java
        @Aspect
        public class TenantIsolationAspect {
            @Before("@annotation(TenantScoped)")
            public void enforceTenantFilter(JoinPoint jp) {
                // Automatically append `AND tenant_id = ?` to all queries
                TenantContext.set(getCurrentTenantId());
            }
        }
        ```
    * **Indexing Strategy:**
        * All indexes must include `tenant_id` as the leading column: `(tenant_id, user_id)`, `(tenant_id, created_at)`, `(tenant_id, status, created_at)`.
        * Without `tenant_id` in the index, queries scan all tenants' data.
    * **Sharding Consideration:**
        * Shard by `tenant_id` range or hash when table exceeds ~500M rows.
        * Use consistent hashing so adding shards doesn't require full reshuffle.

---

* [ ] **Design a time-series data schema in MySQL to store 1 billion+ IoT sensor readings per day with efficient range queries**
    * **Naive approach (fails at scale):**
        ```sql
        CREATE TABLE sensor_readings (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            sensor_id INT, reading DECIMAL(10,4), recorded_at TIMESTAMP
        );
        -- At 1B rows/day: 365B rows/year. Queries become full-table scans.
        ```
    * **Production Schema with Partitioning:**
        ```sql
        CREATE TABLE sensor_readings (
            sensor_id INT NOT NULL,
            recorded_at DATETIME(3) NOT NULL,  -- millisecond precision
            metric_name VARCHAR(50) NOT NULL,
            value DOUBLE NOT NULL,
            PRIMARY KEY (sensor_id, recorded_at, metric_name)  -- no surrogate PK
        )
        ENGINE=InnoDB
        PARTITION BY RANGE (TO_DAYS(recorded_at)) (
            PARTITION p_2026_01 VALUES LESS THAN (TO_DAYS('2026-02-01')),
            PARTITION p_2026_02 VALUES LESS THAN (TO_DAYS('2026-03-01')),
            PARTITION p_2026_03 VALUES LESS THAN (TO_DAYS('2026-04-01')),
            -- ... add monthly partitions
            PARTITION p_future VALUES LESS THAN MAXVALUE
        );
        ```
    * **Why composite PK instead of AUTO_INCREMENT?**
        * `(sensor_id, recorded_at)` as PK = data physically sorted by sensor + time in the clustered index → range queries on one sensor are sequential disk reads.
        * AUTO_INCREMENT PK = data sorted by insertion order → sensor time-range queries cause random I/O.
    * **Partition Pruning:**
        * `WHERE recorded_at BETWEEN '2026-05-01' AND '2026-05-07'` → MySQL scans only the May partition.
        * Partition management: drop old partitions instead of DELETE (instant, no row-by-row deletion).
        ```sql
        ALTER TABLE sensor_readings DROP PARTITION p_2025_01;  -- instant, free disk space
        ```
    * **Rollup Strategy for Analytics:**
        ```sql
        -- 1-minute aggregates table (pre-computed)
        CREATE TABLE sensor_readings_1min (
            sensor_id INT NOT NULL,
            bucket_time DATETIME NOT NULL,
            avg_value DOUBLE, min_value DOUBLE, max_value DOUBLE, count INT,
            PRIMARY KEY (sensor_id, bucket_time)
        );
        -- Run a scheduled job to aggregate raw → 1min → 1hr → 1day
        ```
    * **Columnar Alternative:** For analytics workloads, stream data to ClickHouse or BigQuery — MySQL is not ideal as a primary store for 1B+ time-series rows at petabyte scale.

---

### Advanced Transactions & Concurrency

* [ ] **Explain SELECT FOR UPDATE, SELECT FOR SHARE, and SKIP LOCKED — give production use cases for each**
    * These are pessimistic locking mechanisms that coordinate concurrent access to rows.
    * **SELECT FOR UPDATE:**
        ```sql
        BEGIN;
        SELECT balance FROM accounts WHERE id = 123 FOR UPDATE;
        -- No other transaction can read-for-update or modify this row
        UPDATE accounts SET balance = balance - 100 WHERE id = 123;
        COMMIT;
        ```
        * Acquires an **exclusive lock** — blocks other `FOR UPDATE` and `FOR SHARE` on same rows.
        * **Use case:** Inventory deduction, financial transfers, any "check-then-update" pattern where you must prevent concurrent modification.
    * **SELECT FOR SHARE (aka LOCK IN SHARE MODE):**
        ```sql
        SELECT * FROM config WHERE tenant_id = 456 FOR SHARE;
        ```
        * Acquires a **shared lock** — multiple transactions can hold shared locks simultaneously, but no one can get an exclusive lock (no modifications).
        * **Use case:** Read a row and ensure it won't change during your transaction, without blocking other readers. E.g., reading a parent record before inserting dependent child records (to ensure parent still exists).
    * **SKIP LOCKED — the job queue pattern:**
        ```sql
        -- Multiple workers competing for jobs without deadlocks
        BEGIN;
        SELECT id, payload FROM job_queue
        WHERE status = 'pending'
        ORDER BY priority DESC, created_at ASC
        LIMIT 10
        FOR UPDATE SKIP LOCKED;
        -- SKIP LOCKED: skip rows already locked by other workers instead of waiting
        UPDATE job_queue SET status = 'processing', worker_id = ? WHERE id IN (...);
        COMMIT;
        ```
        * **Without SKIP LOCKED:** Workers queue up waiting for the same rows → serialized processing → throughput bottleneck.
        * **With SKIP LOCKED:** Workers immediately grab unlocked rows → parallel processing → linear throughput scaling.
        * **Production use:** Exactly-once job processing, outbox pattern, batch reservation systems.
    * **NOWAIT variant:**
        ```sql
        SELECT * FROM orders WHERE id = 789 FOR UPDATE NOWAIT;
        -- Immediately returns error if row is locked (instead of waiting)
        -- Use when you want to fail fast rather than queue up
        ```

---

* [ ] **How do you handle database-level optimistic locking vs pessimistic locking? When does each break down in production?**
    * **Pessimistic Locking:**
        * Lock the row before reading it (`SELECT FOR UPDATE`). Guarantees no concurrent modification.
        * **Breaks down when:**
            * Long-running transactions hold locks for seconds → other transactions pile up → connection exhaustion.
            * Deadlocks from inconsistent lock ordering.
            * Lock timeout errors under high concurrency.
        * **Use for:** Short transactions, financial operations, inventory where correctness > throughput.
    * **Optimistic Locking:**
        * Read without locking. Before writing, verify no one else changed the row since your read.
        ```sql
        -- Schema: add version column
        ALTER TABLE products ADD COLUMN version INT DEFAULT 0;

        -- Read (no lock)
        SELECT price, stock, version FROM products WHERE id = 123;
        -- Returns: price=100, stock=50, version=7

        -- Update with version check
        UPDATE products
        SET price = 110, stock = 49, version = version + 1
        WHERE id = 123 AND version = 7;  -- ← optimistic check

        -- If affected_rows == 0: someone else modified it → retry
        ```
    * **Breaks down when:**
        * High contention on same rows → many retries → "thrash" where transactions keep failing.
        * Under very high concurrency, retry storms can amplify load on the DB.
    * **Hybrid Approach for Production:**
        * Use optimistic for low-contention updates (product catalog, user profile).
        * Use pessimistic for high-contention resources (inventory stock decrement, seat booking).
        * Add exponential backoff on optimistic lock failures:
        ```python
        def update_with_retry(product_id, new_price, max_retries=3):
            for attempt in range(max_retries):
                rows_updated = db.execute(
                    "UPDATE products SET price=?, version=version+1 WHERE id=? AND version=?",
                    [new_price, product_id, current_version]
                ).rowcount
                if rows_updated == 1:
                    return True
                time.sleep(0.1 * (2 ** attempt) + random.uniform(0, 0.05))
            raise OptimisticLockException("Exceeded retries")
        ```

---

### Production Scaling Patterns

* [ ] **How would you shard a MySQL database for a social media platform with 500M users? Walk through the shard key selection and the challenges you'll face**
    * **Shard Key Selection — the most critical decision:**
        * **Option A: user_id (recommended for social)**
            * All user data (profile, posts, followers) for a user lives on one shard.
            * `shard_id = user_id % num_shards`
            * **Pro:** Single-shard lookups for most user operations.
            * **Con:** Cross-shard queries for "global trending" or "mutual friends."
        * **Option B: geography**
            * Users from India on Asia shards, users from US on US shards.
            * **Pro:** Low latency for region-local users.
            * **Con:** Cross-region social graphs require cross-shard joins.
    * **Data Model per Shard:**
        ```
        Shard 0 (user_id % 8 == 0): users, posts, comments, follows
        Shard 1 (user_id % 8 == 1): same schema
        ...
        ```
    * **Challenges and Mitigations:**
        * **1. Cross-shard JOIN queries (e.g., "posts by users I follow"):**
            * You cannot JOIN across shards.
            * **Solution:** Denormalize — maintain a `user_feed` table on each user's shard with precomputed data from followees (fan-out on write).
        * **2. Global unique IDs across shards:**
            * AUTO_INCREMENT creates duplicate IDs across shards.
            * **Solution:** Use distributed ID generation — Twitter Snowflake format: `timestamp(41 bits) + datacenter(5) + machine(5) + sequence(12)` = globally unique 64-bit ID.
        * **3. Hotspot shards ("celebrity problem"):**
            * A user with 100M followers generates massive write load on their shard.
            * **Solution:** Store celebrity writes to a dedicated "hot shard." Fan-out asynchronously via message queue (Kafka).
        * **4. Rebalancing when adding shards:**
            * Going from 8 → 16 shards requires rehashing: `user_id % 16` sends half the users to new shards.
            * **Solution:** Use consistent hashing — only 1/N of data moves when adding 1 shard. Tools: Vitess (YouTube), ProxySQL.
        * **5. Schema migrations across 8+ shards:**
            * Running `ALTER TABLE` on 8 shards sequentially takes hours.
            * **Solution:** Automate with Vitess's VSchema migrations or custom migration tooling. Always use online DDL (`gh-ost`).

---

* [ ] **Explain the CQRS pattern with event sourcing at the database level — how does it interact with MySQL in production?**
    * **CQRS (Command Query Responsibility Segregation):**
        * Separate your write path (Commands) from your read path (Queries) using different data models.
    * **Write Side (Command Model):**
        ```sql
        -- Normalized, append-only event store in MySQL
        CREATE TABLE domain_events (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            aggregate_id VARCHAR(36) NOT NULL,  -- e.g., order_id
            aggregate_type VARCHAR(50) NOT NULL,
            event_type VARCHAR(100) NOT NULL,
            event_data JSON NOT NULL,
            event_version INT NOT NULL,
            occurred_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
            INDEX idx_aggregate (aggregate_type, aggregate_id, event_version)
        );
        ```
        * Every state change is stored as an immutable event. Current state = replay all events for an aggregate.
    * **Read Side (Query Model — denormalized projections):**
        ```sql
        -- Read-optimized view, rebuilt from events
        CREATE TABLE order_summary_view (
            order_id VARCHAR(36) PRIMARY KEY,
            user_id INT, user_name VARCHAR(100),
            status VARCHAR(50), total_amount DECIMAL(10,2),
            item_count INT, last_updated TIMESTAMP,
            INDEX idx_user_status (user_id, status)
        );
        ```
        * Projections are updated asynchronously by consuming domain events.
    * **Event Projection Pipeline:**
        ```
        MySQL (events table) → CDC (Debezium/binlog) → Kafka → Projection Service → MySQL (read views) / Redis / Elasticsearch
        ```
    * **Why MySQL for the event store?**
        * ACID guarantees for event append.
        * Optimistic locking via `event_version`: append `version=N+1` only if `version=N` exists → prevents concurrent conflicting events.
        ```sql
        INSERT INTO domain_events (aggregate_id, aggregate_type, event_type, event_data, event_version)
        SELECT 'order-123', 'Order', 'OrderShipped', '{"tracking":"XYZ"}', MAX(event_version) + 1
        FROM domain_events WHERE aggregate_id = 'order-123';
        -- Unique constraint on (aggregate_id, event_version) prevents duplicate versions
        ```
    * **Trade-offs:**
        * **Pro:** Audit trail, temporal queries ("what was the order state at 3 PM?"), replay to rebuild projections.
        * **Con:** Read latency (eventual consistency), complex operational model, event schema evolution is hard.

---

* [ ] **How do you implement zero-downtime blue-green deployments when your schema migration requires a column rename or data backfill on a 500M row table?**
    * Column renames are the most dangerous migrations — no online DDL tool supports them directly without tricks.
    * **The Expand-Contract Pattern (the only safe approach):**
    * **Phase 1 — Expand (backward compatible):**
        ```sql
        -- Add NEW column alongside old
        ALTER TABLE users ADD COLUMN full_name VARCHAR(200),
        ALGORITHM=INPLACE, LOCK=NONE;  -- online, no lock
        ```
        * Deploy app code that writes to BOTH `name` (old) and `full_name` (new) on every insert/update.
        * App reads from `name` still (old column is source of truth).
    * **Phase 2 — Backfill (online, batched):**
        ```sql
        -- Backfill in batches to avoid locking
        UPDATE users SET full_name = name
        WHERE id BETWEEN 1 AND 100000 AND full_name IS NULL;
        -- Repeat with id ranges, sleeping between batches
        ```
        * Use pt-online-schema-change or custom scripts with sleep intervals.
        * Monitor replication lag — if replica lag grows, slow down batch rate.
    * **Phase 3 — Switch reads to new column:**
        * Deploy app code that reads from `full_name` (new column is source of truth).
        * Still writes to both columns.
    * **Phase 4 — Contract (remove old column):**
        * Verify no queries reference `name` column.
        * Deploy app code that only writes to `full_name`.
        ```sql
        ALTER TABLE users DROP COLUMN name,
        ALGORITHM=INPLACE, LOCK=NONE;
        ```
    * **Rollback Plan:** At each phase, you can roll back to the previous app version without data loss — because both columns always have valid data during the transition.
    * **Tooling:**
        * `gh-ost`: Hooks for pausing, throttling based on replication lag, dry-run mode.
        * `pt-osc`: Simpler but uses triggers (adds write overhead).
        * Both support `--max-load` flag to auto-pause if DB load exceeds threshold.