## Elasticsearch

### Architecture & Core Concepts

* [x] **Explain the internal architecture of Elasticsearch. How do primary and replica shards work together?**
    * Elasticsearch is a distributed search and analytics engine built on Apache Lucene. Its architecture is designed for horizontal scalability, high availability, and fault tolerance.
    * **Core Components:**
        * **Cluster**: A collection of one or more nodes that together hold your entire data and provide indexing and search capabilities.
        * **Node**: A single server that is part of your cluster, stores data, and participates in the cluster's indexing and search operations.
        * **Index**: A collection of documents with similar characteristics. An index is a logical namespace that maps to one or more primary shards and can have zero or more replica shards.
        * **Shard**: A single Lucene instance - the fundamental unit that actually holds data and performs search operations.

    * **Shard Architecture**
        * Elasticsearch distributes data across multiple shards for two key reasons: to horizontally scale data volume and to parallelize operations for improved performance.

    * **Primary Shards**
        * Primary shards are the original shards where documents are first indexed. When you create an index, you specify the number of primary shards (this was historically fixed at index creation, though recent versions allow some flexibility). Each document belongs to exactly one primary shard, determined by a hash of the document's ID.
        * When a write request comes in, Elasticsearch routes it to the appropriate primary shard based on the document ID. The primary shard validates the request, performs the indexing operation locally, and then forwards the operation to all its replica shards.

    * **Replica Shards**
        * Replica shards are exact copies of primary shards. They serve two critical purposes: providing high availability if a node fails, and increasing read throughput by allowing searches to be executed on replicas in parallel.
        * The number of replicas can be changed dynamically for an existing index. If you have one replica, each primary shard has one copy, doubling your data redundancy and read capacity.

    * **How Primary and Replica Shards Work Together**

        * **Write Operations**
        * When a document is indexed, updated, or deleted:
            1. The request arrives at a coordinating node (any node can act as coordinator)
            2. The coordinator determines which shard should hold the document using the formula: `shard_num = hash(document_id) % num_primary_shards`
            3. The request is forwarded to the primary shard
            4. The primary shard executes the operation and validates it
            5. The primary shard replicates the operation in parallel to all replica shards
            6. Once all replicas confirm success, the primary acknowledges to the coordinator
            7. The coordinator responds to the client

          This process ensures data consistency - replicas are always in sync with their primary.

        * **Read Operations**
        * For search and retrieval operations:
            1. The coordinating node receives the search request
            2. The coordinator determines which shards hold the relevant data
            3. The request is forwarded to one copy of each relevant shard (either primary or replica), using round-robin load balancing
            4. Each shard executes the search locally and returns results
            5. The coordinator merges and ranks the results from all shards
            6. The final response is sent to the client

          This allows Elasticsearch to distribute the search load across both primary and replica shards, significantly
          improving read throughput.

    * **Failure Handling**
        * If a primary shard fails, Elasticsearch automatically promotes one of its replicas to become the new primary. If a replica fails, the cluster continues operating normally with reduced redundancy until the replica is restored. This automatic failover ensures high availability without manual intervention.

    * **Shard Allocation**
        * Elasticsearch's master node manages shard allocation, ensuring that primary and replica shards are distributed across different nodes. A replica is never allocated to the same node as its primary, preventing data loss if that node fails.

    * **Performance Considerations**
        * The number of shards affects performance. Too few shards limit parallelization and scalability; too many shards create overhead from managing many Lucene indices. A common guideline is to keep shard size between 10-50GB and aim for fewer, larger shards rather than many tiny ones.
        * This architecture allows Elasticsearch to scale horizontally while maintaining data durability and providing fast, distributed search capabilities across massive datasets.

---
* [x] **What is the difference between inverted index and forward index? How does Elasticsearch leverage inverted
  indices?**
    * **Forward Index**: Document → Terms
        - Doc1 → [apple, banana]
        - Doc2 → [banana, cherry]

    * **Inverted Index**: Term → Documents
        - apple → [Doc1]
        - banana → [Doc1, Doc2]
        - cherry → [Doc2]

    * **Why Inverted Index?**
        * Forward index requires scanning all documents to find a term. Inverted index allows instant lookup of documents containing a term - perfect for search.

    * **Elasticsearch Usage**

        1. **Tokenization**: "Quick Brown Fox" → [quick, brown, fox]

        2. **Index Storage**: Each term stores:
            - Document IDs containing it
            - Term frequency
            - Term positions (for phrase queries)

        3. **Query**: Search "brown fox"
            - Lookup "brown" → Doc IDs
            - Lookup "fox" → Doc IDs
            - Intersect results, rank by relevance

        4. **Key Points**:
            - One inverted index per text field
            - Immutable segments enable caching
            - Great for search, less optimal for sorting/aggregations
            - Doc values (forward index) complement it for analytics
              **Interview tip**: Inverted indices make Elasticsearch fast at "find documents with this term" but requires doc values for "get all values of this field across documents."

* [ ] **Describe the lifecycle of a document from indexing to search in Elasticsearch.**
    * **Indexing**
        1. **Routing**: Coordinator node calculates shard using `hash(doc_id) % num_primary_shards`
        2. **Primary writes**: Document → in-memory buffer + transaction log
        3. **Replication**: Primary forwards to all replicas in parallel
        4. **Acknowledge**: After replicas confirm, client gets response

    * **Making Searchable**
        * **Refresh (every 1 second)**: In-memory buffer → new segment → now searchable
          *This causes 1-second delay (near real-time)

    * **Flush (every 30 min)**: Segments → disk, translog cleared

    * **Merge**: Background process combines small segments → larger ones

    * **Searching**

        1. **Query phase**: Coordinator sends query to one copy of each shard
        2. **Local search**: Each shard searches using inverted index, returns doc IDs + scores
        3. **Fetch phase**: Coordinator requests full docs for top results
        4. **Response**: Merged results returned to client

    * **Update/Delete**
        - **Update**: Delete old + index new
        - **Delete**: Mark deleted, removed during merge
    * **Key point**: Documents live in immutable segments. Updates create new segments. Transaction log prevents data
      loss.

---
* [x] **How does Elasticsearch achieve near real-time search? Explain the role of refresh interval, translog, and
  flush operations.**
    * Elasticsearch is near real-time because refresh makes data searchable quickly, translog ensures durability, and flush persists data to disk asynchronously.
    * **Refresh interval (default ~1s):** Makes newly indexed documents searchable by creating a new in-memory segment
      and opening it for search.
        * Documents indexed → in-memory buffer (NOT searchable)
        * Every 1 second: buffer written to new in-memory segment
        * Segment opened for search → documents now searchable
        * Buffer cleared
          **Why delay?:** Creating segments is expensive. Batching every second balances searchability vs performance.
          Tuning:
            * Faster refresh (refresh_interval: 500ms) = quicker search, slower indexing
            * Slower refresh (30s) = better indexing throughput, delayed searchability

    * **Translog (transaction log):** Every write is first written to translog for crash recovery.
        * Every write → translog immediately (on disk)
        * If node crashes before flush, translog replays operations on restart
        * Prevents data loss for documents in in-memory buffer

        * **Why needed?:** In-memory segments aren't durable until flushed to disk

    * **Flush operation:** Persists in-memory segments to disk and clears translog.
        * In-memory segments → written to disk (fsync)
        * Translog cleared (operations now durable in segments)
        * Happens every 30 minutes OR when translog gets too large

  ```
  Index request → Translog (durable) + In-memory buffer (not searchable)
                                          ↓
                              Refresh (1s) → In-memory segment (searchable)
                                          ↓
                              Flush (30min) → Disk segment (durable + searchable)
  ```

---
* [x] **What are segments in Lucene? How does segment merging work and why is it important?**
    * **Segment = immutable mini-index** containing a subset of documents with its own inverted index, stored fields, and doc
      values.
    * When documents are indexed, they're written to new segments. Segments are never modified - only created or deleted.

    * **Why Segments?**
        - **Immutability**: Enables caching, concurrent reads without locks
        - **Write efficiency**: New data appended as new segments vs rewriting entire index
        - **Delete handling**: Documents marked deleted in bitmap, physically removed later during merge

    * Segment Merging
        * **What happens**: Background process combines multiple small segments into fewer large ones
            1. Merge policy selects segments to merge (typically small adjacent segments)
            2. Create new merged segment with live documents only
            3. Switch searchers to new segment
            4. Delete old segments

    * **Why Merging is Critical**
        1. **Search performance**: Fewer segments = fewer files to search = faster queries
        2. **Disk space**: Actually removes deleted documents (marked deleted but still occupy space)
        3. **Resource efficiency**: Too many segments = too many open files, more memory overhead
        4. **Relevance scoring**: Accurate IDF calculation across merged data

    * **Trade-offs**
        - **CPU cost**: Merging is I/O and CPU intensive
        - **Timing**: Happens in background but can impact cluster during large merges
        - **Tuning**: Can adjust merge policy - aggressive merging = better search, more overhead

  ```
  Index
  └── Shard 1 (primary)
        ├── Segment 1  50,000 documents
        ├── Segment 2  10,000 documents
        ├── Segment 3  20,000 documents
        └── ... (many segments)
  └── Shard 2 (primary)
        ├── Segment 1  10,000 documents
        ├── Segment 2  90,000 documents
        └── ...
  
  ## Complete Storage Hierarchy
  RELATIONAL:
  Database Server
    └── Database
          └── Schema
                └── Table
                      └── Row
                            └── Column
  
  ELASTICSEARCH:
  Cluster (multiple nodes)
    └── Index
          └── Shard (distributed across nodes)
                └── Segment (Lucene files)
                      └── Document
                            └── Field
  ```
    * **Key point**: Segments are why Elasticsearch writes are fast (append-only) but requires background merging to
      maintain search performance.
### Cluster Management & Scalability

* [x] **How would you design an Elasticsearch cluster for high availability and fault tolerance?**
    * **Node Setup:**
        - Minimum 3 master-eligible nodes (prevents split-brain)
        - Multiple data nodes across different availability zones
        - Use dedicated node roles: master, data, and coordinating nodes

    * **Data Protection:**
        - At least 1 replica per primary shard (data exists on 2+ nodes)
        - Enable shard allocation awareness by zone/rack
        - Regular snapshots to remote storage (S3, GCS)

    * **Key Configurations:**
        - Set `cluster.routing.allocation.awareness.attributes` for zone-aware shard placement
        - Configure discovery seed hosts for node discovery
        - Tune heap size to 50% of RAM (max 32GB)

    * **Monitoring:**
        - Track cluster health, unassigned shards, and node status
        - Alert on yellow/red cluster status
        - Monitor JVM heap and disk space

    * **Handling Failures:**
        - Node failure → automatic shard reallocation to remaining nodes
        - Zone failure → replicas in other zones keep cluster operational
        - Use rolling restarts for zero-downtime upgrades

    * **Production Example:** 3 masters + 6 data nodes (2 per zone) + 2 coordinators with a load balancer.

---
* [x] **Explain the master election process. What happens during a split-brain scenario and how do you prevent it?**
    * **How Election Works:**

        1. **Quorum-based voting**: Master-eligible nodes vote to elect a master
        2. **Majority required**: Need >50% of master-eligible nodes to agree (prevents split-brain)
        3. **Election triggers**: Happens on cluster startup, current master failure, or network issues
        4. **Fastest wins**: Node with lowest ID or fastest response typically becomes master

    * **Example with 3 nodes:**
        - Need 2 out of 3 votes to elect a master
    - If current master fails, remaining 2 nodes vote and elect new master

    * **Split-Brain Scenario**
        * Network partition splits cluster into 2+ groups, each thinking they're the real cluster. Both groups accept writes → **data divergence and corruption**.
      ```
      3-node cluster splits into:
      Group A: Node1, Node2 → elects master, accepts writes
      Group B: Node3 → elects itself master, accepts writes
      Result: Two versions of truth, data conflicts!
      ```

    * **Prevention Mechanisms**
        * **1. Quorum Requirement (Modern ES 7+)**
      ```yaml
      # Automatic in ES 7+
      cluster.initial_master_nodes: [node1, node2, node3]
      ```
    - Requires majority vote (2 out of 3)
    - Minority partition **cannot** elect master
    - Only the partition with 2+ nodes continues operating

    * **2. Discovery Configuration**
  ```yaml
  discovery.seed_hosts: [node1:9300, node2:9300, node3:9300]
  discovery.zen.minimum_master_nodes: 2  # ES 6.x and older
  ```

    * **3. Split Resolution:**
  ```
  Network split with 3 nodes:
  - Group A (2 nodes): Has quorum → elects master ✓
  - Group B (1 node): No quorum → cannot elect master ✗
  ```
  Node in Group B goes into read-only mode, waits to rejoin cluster.

    * **Best Practices**

        * **Odd number of master nodes**: Always use 3, 5, or 7 (never even numbers)
        * 3 nodes: survives 1 failure
        * 5 nodes: survives 2 failures

    * **Cross-zone deployment**: Place nodes in different availability zones to prevent full partition.
    * **Network stability**: Use reliable, low-latency network between master nodes.

    * **Formula**: For N master-eligible nodes, need (N/2) + 1 for quorum.

---
* [x] **How do you handle cluster scaling (both vertical and horizontal)? What are the considerations?**
    * **Horizontal Scaling (Add/Remove Nodes)**
        * **Adding nodes:**
            - Start new node with same cluster name
            - Auto-joins and shards rebalance automatically
            - Zero downtime

        * **Removing nodes:**
            - Exclude node from shard allocation
            - Wait for shards to move, then shutdown

        * **Considerations:**
            - Plan shard count upfront (can't split later)
            - Too many shards = overhead, too few = poor distribution
            - Target: 10-50GB per shard

    * **Vertical Scaling (Upgrade Hardware)**
        * **Process:**
        - Rolling restart: disable allocation → upgrade node → restart → repeat
        - Max heap: 32GB (use 50% of RAM)
        - SSDs over HDDs for better I/O

    * **Considerations:**
        - Downtime per node during upgrade
        - Diminishing returns after certain size

    * **When to Use What**
        - **Horizontal**: Need more capacity, better HA
        - **Vertical**: Nodes are undersized
    * **Key point**: Horizontal is preferred - better fault tolerance and easier to scale incrementally.


---
* [x] **Describe shard allocation strategies. When would you use awareness attributes?**
    * **Shard Allocation Strategies**
        * **1. Default allocation:** Round-robin distribution across all data nodes, balances shard count automatically.
        * **2. Awareness attributes:** Use `cluster.routing.allocation.awareness.attributes: zone` to distribute replicas
          across zones/racks, prevents data loss if entire zone fails.
        * **3. Filtering allocation:** Use `index.routing.allocation.include/exclude/require` to control which nodes host
          specific indices (hot/warm/cold architecture).
        * **4. Forced awareness:** `cluster.routing.allocation.awareness.force.zone.values: [zone1, zone2]` prevents shard
          allocation if not enough zones available.
        * **5. Total shards per node:** `cluster.routing.allocation.total_shards_per_node` limits shards per node to
          prevent hotspots.
    * **When to Use Awareness Attributes**
        * **Multi-AZ deployment:** Ensures primary and replica shards never in same availability zone, survives zone failure.
        * **Rack awareness:** In single datacenter with multiple racks, prevents replica on same rack as primary.
        * **Hardware tiers:** Tag nodes as hot/warm/cold, route recent data to fast SSDs, old data to cheaper HDDs.
        * **Compliance requirements:** Use attributes like `data_classification: sensitive` to ensure regulated data only on compliant nodes.
        * **Disaster recovery:** Geographic awareness across regions ensures cluster survives regional outages.


---
* [x] **How do you perform zero-downtime reindexing for a large index?**
    * **1. Create new index with updated mappings/settings:**
  ```json
  PUT /my_index_v2
  {
    "mappings": { /* new mappings */ },
    "settings": { /* optimized settings */ }
  }
  ```

    * **2. Use alias pattern:**
        - Point application to alias `my_index` instead of direct index name
        - Allows seamless switching between indices

    * **3. Reindex data (runs in background):**
  ```json
  POST _reindex
  {
    "source": { "index": "my_index_v1" },
    "dest": { "index": "my_index_v2" }
  }
  ```
  Use `wait_for_completion=false` for async operation, throttle with `requests_per_second` to reduce cluster load.

    * **4. Dual-write approach for ongoing updates:**
        - Write to both old and new index during reindexing
        - Or use `_reindex` with `conflicts: proceed` after initial reindex

    * **5. Switch alias atomically:**
  ```json
  POST _aliases
  {
    "actions": [
      { "remove": { "index": "my_index_v1", "alias": "my_index" }},
      { "add": { "index": "my_index_v2", "alias": "my_index" }}
    ]
  }
  ```

    * **6. Verify and cleanup:** Test queries on new index, monitor for issues, delete old index after confidence period.

  **Key points:** Use aliases for abstraction, throttle reindex to avoid cluster overload, atomic alias swap = zero downtime.


### Performance Optimization

* [x] **What strategies would you use to optimize search performance for a 500TB cluster?**
    * **1. Index Design:**
        - Use time-based indices (daily/monthly) for better data management
        - Optimal shard size: 20-50GB per shard
        - Limit replicas based on query load (1-2 replicas typically sufficient)

    * **2. Hot-Warm-Cold Architecture:**
        - Recent data on fast SSD nodes (hot tier) for active queries
        - Older data on cheaper storage (warm/cold tiers)
        - Use ILM to automatically move indices between tiers

    * **3. Query Optimization:**
        - Use filters over queries (cached and faster)
        - Limit `size` parameter, use pagination with `search_after`
        - Use `_source` filtering to return only needed fields
        - Avoid wildcard queries on large fields

    * **4. Caching Strategy:**
        - Enable request cache for aggregations
        - Query cache for filter clauses
        - Field data cache for sorting/aggregations (use doc_values instead)

    * **5. Hardware & Resources:**
        - Dedicated coordinating nodes to handle search requests
        - More replicas = more query capacity (distributes load)
        - Fast SSDs, adequate RAM (50% for heap, 50% for OS cache)

    * **6. Routing & Sharding:**
        - Use routing keys to query specific shards only
        - Avoid querying all indices with wildcards
        - Use index aliases to query only relevant time ranges

  **Key metrics to monitor:** Query latency, CPU usage, heap pressure, cache hit rates, slow query logs.


---
* [x] **Explain query vs filter context. How does caching work in each?**
    * *Query Context:** Calculates relevance score (`_score`), used for full-text search and ranking, **not cached** (expensive).

    * **Filter Context:** Binary yes/no match, no scoring, **automatically cached as bitsets**, much faster for exact
      matches like `term`, `range`, `exists`.

    * **Caching:** Filters cached at segment level, reused across queries. Query cache only for aggregations (`size=0`)
      with `request_cache: true`.

    * **Best practice:** Use filters for exact matches (status, dates, IDs), queries for text search needing relevance
      scoring.


---
* [x] **How would you troubleshoot slow queries? Walk through your debugging approach.**
    * **1. Enable slow query logs:**
  ```json
  PUT /my_index/_settings
  {
    "index.search.slowlog.threshold.query.warn": "2s",
    "index.search.slowlog.threshold.fetch.warn": "1s"
  }
  ```
  Identifies which queries are slow and their patterns.

    * **2. Use Profile API to analyze query execution:**
  ```json
  GET /my_index/_search
  {
    "profile": true,
    "query": { /* your query */ }
  }
  ```
  Shows time spent in each query phase, identifies bottleneck clauses.

    * **3. Check common issues:**
        - Wildcard/prefix queries on large fields → use n-grams instead
        - Large `size` parameter → use pagination with `search_after`
        - Sorting on text fields → use `keyword` type or doc_values
        - Deep pagination with `from/size` → switch to `search_after`
        - Fetching large `_source` → filter fields with `_source: ["field1", "field2"]`

    * **4. Monitor cluster metrics:**
        - High heap usage → increase memory or reduce query load
        - CPU spikes → too many concurrent queries, add nodes
        - Hot shards → uneven data distribution, check shard allocation

    * **5. Optimize based on findings:**
        - Add filters instead of queries (cached)
        - Use `bool` query to combine efficiently
        - Reduce shard count if too many small shards
        - Add replicas to distribute query load

  **Quick wins:** Use filters over queries, limit returned fields, avoid deep pagination, check if indices need reindexing with better mappings.


---
* [x] **What is the impact of having too many shards? How do you determine the optimal shard size?**
    * **Performance issues:**
        - High overhead: each shard = Lucene index with memory/CPU cost
        - Slow cluster state updates (master bottleneck managing shard metadata)
        - Longer recovery time during node failures (more shards to relocate)
        - Inefficient resource usage (thread pools, file handles per shard)

    * **Memory overhead:** Each shard consumes ~1MB heap just for metadata, 10,000 shards = 10GB heap wasted.

    * **Determining Optimal Shard Size**

        * **Target shard size:** 10-50GB per shard (20-30GB is sweet spot for most cases).
        * **Formula:** `number_of_shards = total_index_size / target_shard_size`

    * **Considerations:**
        - Search-heavy workload → smaller shards (10-20GB) for parallelism
        - Write-heavy workload → larger shards (30-50GB) to reduce overhead
        - Heap size limit: total shards per node × 1MB < 5% of heap

    * **Rule of thumb:** Shards per node should be < 20 per GB of heap (e.g., 30GB heap = max 600 shards per node).

    * **Best practice:** Start with fewer shards, can't reduce later without reindexing. Use rollover API for
      time-series data to create new indices when size/age threshold met.


* [x] **Describe techniques to optimize indexing throughput for high-volume data ingestion.**
    * **1. Bulk API & Batching:**
        - Use `_bulk` API instead of single document indexing (10-100x faster)
        - Optimal batch size: 5-15MB or 1000-5000 documents per request
        - Multiple parallel bulk requests from different threads

    * **2. Refresh Interval:**
        - Increase `index.refresh_interval` from default 1s to 30s or `-1` (disable during bulk load)
        - Refresh makes documents searchable but is expensive, delay it during ingestion

    * **3. Replica Management:**
        - Set `number_of_replicas: 0` during initial bulk load
        - Add replicas after indexing complete (faster recovery than replication)

    * **4. Translog Settings:**
        - Increase `index.translog.flush_threshold_size` to reduce disk syncs
        - Set `index.translog.durability: async` (trades durability for speed, use cautiously)

    * **5. Hardware & Resources:**
        - Use SSDs for faster disk I/O
        - More CPU cores for parallel indexing threads
        - Adequate heap size (50% of RAM, max 32GB)

    * **6. Disable Unnecessary Features:**
        - Disable `_source` if not needed for reindexing
        - Set `index.norms: false` for fields not needing scoring
        - Use `doc_values: false` for fields not used in sorting/aggregations

    * **7. Shard Configuration:**
        - More shards = more parallel indexing (but don't overdo it)
        - Use routing to distribute load evenly

    * **Key metrics:** Monitor indexing rate, CPU usage, disk I/O, rejected threads in bulk thread pool.

### Query DSL & Search

* [x] **Explain the difference between match, match_phrase, and term queries. When would you use each?**
    * **1. Term Query:**
        - Exact match on **non-analyzed** field (no tokenization)
        - Case-sensitive, matches stored term exactly
        - Use for: keywords, IDs, status codes, enum values, numbers, dates
        - Example: `{"term": {"status": "active"}}` matches "active" only, not "Active"

    * **2. Match Query:**
        - Full-text search on **analyzed** fields (tokenized, lowercased)
        - Finds documents containing any/all terms (OR by default)
        - Use for: searching text content, titles, descriptions
        - Example: `{"match": {"title": "quick brown"}}` matches "quick", "brown", or both

    * **3. Match Phrase Query:**
        - Finds exact phrase in **analyzed** field (terms in same order)
        - Position-aware, maintains word order
        - Use for: searching exact phrases, names, specific expressions
        - Example: `{"match_phrase": {"title": "quick brown"}}` matches "quick brown" only, not "brown quick"

    * **When to Use Each**
        * **Term:** Filtering exact values → `status="published"`, `user_id=123`, `category="electronics"`
        * **Match:** General text search → searching blog posts, product descriptions, fuzzy matching
        * **Match Phrase:** Exact phrase search → company names "New York Times", quotes, multi-word terms
    * **Key difference:** Term = no analysis (exact), Match = analyzed (flexible text search), Match Phrase = analyzed +
      order matters.


---
* [x] **How do bool queries work? Explain must, should, filter, and must_not clauses with scoring implications.**
    * **Must:**
        - Document **must** match (required)
        - Contributes to `_score` (increases relevance)
        - Use for: required conditions that affect ranking
        - Example: `"must": [{"match": {"title": "elasticsearch"}}]`

    * **Filter:**
        - Document **must** match (required)
        - **No scoring** (does not affect `_score`)
        - **Cached** for performance
        - Use for: exact matches, ranges, filters that don't need relevance
        - Example: `"filter": [{"term": {"status": "published"}}]`

    * **Should:**
        - Document **may** match (optional, increases score if matched)
        - At least one should match if no `must` clause exists
        - Contributes to `_score` (boosts relevance)
        - Use for: optional criteria that improve ranking
        - Example: `"should": [{"match": {"tags": "featured"}}]`

    * **Must_Not:**
        - Document **must not** match (exclusion)
        - **No scoring** (executed in filter context)
        - **Cached** for performance
        - Use for: excluding documents
        - Example: `"must_not": [{"term": {"status": "deleted"}}]`

    * **Scoring Impact**
        * **Scored:** `must` and `should` increase `_score` based on relevance.
        * **Not scored:** `filter` and `must_not` are boolean checks only (faster, cached).

    * **Best practice:** Use `filter` instead of `must` for exact matches to leverage caching and skip scoring overhead.


### Data Modeling & Index Management

* [x] **How do you design index mapping for a multi-tenant SaaS application?**
    * **1. Index Per Tenant (Best for isolation):**
        - Separate index for each tenant: `tenant_123_data`, `tenant_456_data`
        - **Pros:** Complete data isolation, independent optimization, easy deletion
        - **Cons:** Too many indices = overhead, use only for <1000 tenants
        - **Use when:** Strong isolation needed, tenants have very different data volumes

    * **2. Shared Index with Tenant Field (Best for scale):**
      ```json
      {
        "mappings": {
          "properties": {
            "tenant_id": {"type": "keyword"},  // Filter by this
            "user_name": {"type": "text"},
            "created_at": {"type": "date"}
          }
        }
      }
      ```
        - All tenants in one index, filter by `tenant_id`
        - **Pros:** Scales to millions of tenants, efficient resource usage
        - **Cons:** No hard isolation, queries must include tenant_id filter
        - **Use when:** Many small tenants, similar data patterns

    * **3. Index Per Tenant Tier (Hybrid approach):**
        - `premium_tenants_*`, `standard_tenants_*`, `free_tenants_*`
        - Group by SLA/size, premium gets dedicated resources
        - **Best of both:** Balance isolation and scalability


---
* [x] **How would you implement time-series data storage? Discuss rollover, ILM policies, and data tiers.**
    *  **1. Rollover Strategy:**
        - Create monthly indices: `analytics-2024-01`, `analytics-2024-02`, `analytics-2024-03`
        - Use alias `analytics-write` pointing to current month
    ```json
    POST analytics-write/_rollover
    {
    "conditions": {
        "max_age": "30d",
        "max_size": "50gb"
      }
    }
    ```

    * **2. ILM Policy for Permanent Analytics Storage:**
  ```json
  PUT _ilm/policy/analytics_monthly
  {
    "policy": {
      "phases": {
        "hot": {"actions": {"rollover": {"max_age": "30d"}}},           // Current month
        "warm": {"min_age": "1M", "actions": {"shrink": {}, "readonly": {}}},  // 1-6 months
        "cold": {"min_age": "6M", "actions": {"searchable_snapshot": {}}}      // 6+ months
        // NO delete phase - data kept forever
      }
    }
  }
  ```

    * **3. Data Tiers (No Deletion):**
        - **Hot (0-1 month):** Current month, fast SSDs, active dashboards, write + read
        - **Warm (1-6 months):** Recent quarters, read-only, reduced replicas, slower disks
        - **Cold (6+ months):** Historical data, searchable snapshots on cheapest storage, minimal resources
        - **Frozen (optional):** Very old data (2+ years), ultra-cheap, very slow queries

    * **4. Cost Optimization:**
        - Searchable snapshots in cold tier reduce storage cost by 50-90%
        - Keep data forever but on progressively cheaper storage
        - Old data still searchable but slower (acceptable for historical analytics)

    * **Key benefit:** Never delete data, but move old months to cold storage for 90% cost savings while keeping it
      queryable.

### Monitoring & Production Issues

* [x] **What metrics do you monitor in production? How do you set up alerts?**

    * **Cluster Health**
        - Cluster status (green/yellow/red)
        - Number of nodes and their status
        - Unassigned shards count
        - Active shards and relocating shards

    * **Performance Metrics**
        - Query latency (search and indexing)
        - Indexing rate and search rate
        - Query throughput
        - Thread pool rejections (search, write, bulk)
        - JVM heap usage and garbage collection frequency
        - CPU and memory utilization per node

    * **Resource Usage**
        - Disk space usage per node (critical for preventing index failures)
        - Network I/O
        - File descriptor usage
        - Circuit breaker trips

    * **Data Metrics**
        - Index size and document count
        - Segment count and merge statistics
        - Refresh and flush times

    * **Setting Up Alerts**

        * **Critical Alerts** (immediate action needed)
            - Cluster status red: data loss risk
            - Disk usage above 85-90%: Elasticsearch will block writes at 95%
            - JVM heap consistently above 75%: GC pressure
            - High thread pool rejections: capacity issues
            - Node disconnections

        * **Warning Alerts** (investigate soon)
            - Cluster status yellow: replica shards unassigned
            - Query latency exceeding SLA thresholds
            - Indexing lag increasing
            - High GC pause times
            - Circuit breakers triggering frequently

    * **Implementation Approaches**

        * **Native Elasticsearch Monitoring**
            - Enable monitoring in `elasticsearch.yml`
            - Use Kibana's Stack Monitoring for visualization
            - Configure Watcher for alerting (requires X-Pack/Elastic license)

        * **External Monitoring Tools**
            - Prometheus + Grafana (use elasticsearch_exporter)
            - Datadog, New Relic, or similar APM tools
            - ELK Stack itself (self-monitoring using Metricbeat)
            - CloudWatch (if on AWS)

    * **Sample Alert Configuration Example** (Prometheus AlertManager style):
  ```yaml
  - alert: ElasticsearchClusterRed
    expr: elasticsearch_cluster_health_status{color="red"} == 1
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Cluster {{ $labels.cluster }} is RED"
  
  - alert: ElasticsearchHighHeapUsage
    expr: elasticsearch_jvm_memory_used_bytes{area="heap"} / elasticsearch_jvm_memory_max_bytes{area="heap"} > 0.85
    for: 5m
    labels:
      severity: warning
  ```

    * **Best Practices**
        - Set different thresholds for dev/staging/production
        - Avoid alert fatigue by tuning thresholds appropriately
        - Use escalation policies (warn → critical)
        - Include runbooks in alert notifications
        - Test alerts regularly
        - Monitor the monitoring system itself

---
* [x] **Explain the impact of heap size on Elasticsearch performance. How do you tune JVM settings?**
    *  **Heap Size Impact on Elasticsearch**
        - Set heap to 50% of RAM, max 31GB (never exceed 32GB due to compressed pointers overhead)
        - Above 32GB, JVM uses 8-byte pointers vs 4-byte, losing ~30% efficiency
        - Remaining 50% RAM goes to OS file system cache for Lucene (critical for search performance)
        - Always set `-Xms` equal to `-Xmx` to avoid expensive heap resizing
        - Monitor heap usage: should stay 40-75%; above 80% indicates undersized heap or memory leaks
        - Use G1GC (default in ES 7+), enable GC logging, and tune based on workload (search-heavy needs more OS cache, indexing-heavy needs more heap)
---
* [x] **Describe your approach to handling unassigned shards in production.**
    * **Diagnosis Steps:**
        * **Check cluster health:** GET /_cluster/health and identify yellow/red status
        * **Find unassigned shards:** GET /_cat/shards?h=index,shard,prirep,state,unassigned.reason | grep UNASSIGNED
        * **Check allocation explanation:** GET /_cluster/allocation/explain for specific reason


---
* [x] **How do you perform disaster recovery? What's your backup and restore strategy?**
    * **Snapshot Repository Setup:**
        - Configure shared repository (S3, NFS, Azure, GCS): `PUT /_snapshot/my_backup {"type": "s3", "settings": {"bucket": "es-backups", "region": "us-east-1"}}`
        - Use snapshot lifecycle management (SLM) for automated backups: `PUT /_slm/policy/daily_snapshots` with schedule and retention rules
        - Store snapshots in different region/zone than primary cluster for true DR

    * **Backup Strategy:**
        - **Full snapshots**: Daily for complete cluster state (indices, mappings, settings)
        - **Incremental snapshots**: Hourly/continuous for critical indices (only stores changed segments)
        - **Retention**: Keep 7 daily, 4 weekly, 12 monthly based on RPO/RTO requirements
        - Test restores monthly to validate backup integrity

    * **Restore Process:**
        - Close existing index if restoring to same cluster: `POST /my_index/_close`
        - Restore snapshot: `POST /_snapshot/my_backup/snapshot_1/_restore` with specific indices or full cluster
        - For disaster recovery, spin up new cluster, configure repository, and restore from latest snapshot
        - Verify data integrity, repoint applications, monitor cluster health post-restore


---
* [x] **How vector data stored in the elasticsearch?**
    * Use dense_vector field type in mappings: "embedding": {"type": "dense_vector", "dims": 768, "index": true, "similarity": "cosine"}
    * Vectors stored as arrays of floats: [0.23, -0.45, 0.78, ...] with fixed dimensionality
    * Indexed using HNSW (Hierarchical Navigable Small World) algorithm for approximate nearest neighbor (ANN) search
    * Alternative: sparse_vector for high-dimensional sparse data (deprecated in favor of rank features)


### Security & Compliance
* [x] **How do you implement role-based access control (RBAC) in Elasticsearch?**
    * **Prerequisites:**
        - Requires X-Pack Security (included in basic license+)
        - Enable in `elasticsearch.yml`: `xpack.security.enabled: true`

    * **Setup Steps:**

        1. **Create Roles** (define permissions):
      ```bash
      PUT /_security/role/app_reader
      {
        "cluster": ["monitor"],
        "indices": [{
        "names": ["app-logs-*"],
        "privileges": ["read", "view_index_metadata"]
        }]
      }
      ```

        2. **Create Users** (assign roles):
      ```bash
      POST /_security/user/jane_doe
      {
        "password": "secure_pass",
        "roles": ["app_reader", "kibana_user"]
      }
      ```
        3. **Apply Restrictions** (optional fine-grained control):
            - **Document-level**: `"query": {"term": {"department": "sales"}}` - users only see matching docs
            - **Field-level**: `"field_security": {"grant": ["*"], "except": ["ssn"]}` - hide sensitive fields

    * **Common Patterns:**
        - Read-only users: `["read"]` privileges
        - Data engineers: `["write", "create_index", "manage"]`
        - Admins: `"cluster": ["all"]` with all index privileges

---
* [x] **How do you secure data at rest and in transit?**
    * **Data in Transit (Network Encryption):**

        1. **Enable TLS/SSL for HTTP** (client-to-node):
      ```yaml
      # elasticsearch.yml
      xpack.security.http.ssl.enabled: true
      xpack.security.http.ssl.keystore.path: certs/elastic-certificates.p12
      xpack.security.http.ssl.truststore.path: certs/elastic-certificates.p12
      ```

        2. **Enable TLS for Transport** (node-to-node):
       ```yaml
       xpack.security.transport.ssl.enabled: true
       xpack.security.transport.ssl.verification_mode: certificate
       xpack.security.transport.ssl.keystore.path: certs/elastic-certificates.p12
       xpack.security.transport.ssl.truststore.path: certs/elastic-certificates.p12
      ```

        3. **Generate Certificates**:
      ```bash
      bin/elasticsearch-certutil ca  # Create CA
      bin/elasticsearch-certutil cert --ca elastic-stack-ca.p12  # Generate node certs
      ```

    * **Data at Rest (Disk Encryption):**

        - **Elasticsearch doesn't provide native encryption** - relies on OS/infrastructure level
        - **Linux**: Use LUKS (dm-crypt): `cryptsetup luksFormat /dev/sdb`, mount encrypted volumes for data directories
        - **Cloud providers**: AWS EBS encryption, Azure Disk Encryption, GCP persistent disk encryption
        - **Storage arrays**: Enable encryption at SAN/NAS level

    * **Additional Security Measures:**
        - Enable authentication: `xpack.security.enabled: true`
        - Use API keys instead of passwords: `POST /_security/api_key`
        - Network segmentation: Firewall rules, bind to private IPs only
        - Audit logging: `xpack.security.audit.enabled: true` to track all access

### Integration & Ecosystem

* [x] **How do you design a logging/monitoring pipeline using the ELK/Elastic stack?**
    * The pipeline follows this flow: **Application Logs → Filebeat → Logstash → Elasticsearch → Kibana**

    * **Components Breakdown**

        * 1. **Application Layer**

            * **Spring Boot Pods (Java)**
                - Write logs to stdout/stderr or log files mounted on persistent volumes
                - Use structured logging (JSON format recommended) with libraries like Logback or Log4j2
                - Each pod writes to `/var/log/app/application.log` or similar paths

            * **Python Services**
                - Running as systemd services or containerized workloads
                - Write logs to standard locations like `/var/log/python-service/app.log`
                - Use Python logging with JSON formatters (python-json-logger)

        * 2. **Filebeat DaemonSet**
            * Filebeat runs as a **DaemonSet** in Kubernetes, meaning one Filebeat pod runs on every node.
            * **Key responsibilities:**
                - Monitors log files from all pods on that node
                - Lightweight log shipper that reads log files and forwards to Logstash/Elasticsearch
                - Uses autodiscover feature to detect new pods automatically

            * **Configuration for Kubernetes:**

      ```yaml
  filebeat.autodiscover:
  providers:
  - type: kubernetes
  node: ${NODE_NAME}
  hints.enabled: true
  templates:
  - condition:
  contains:
  kubernetes.labels.app: "springboot"
  config:
  - type: container
  paths:
  - /var/log/containers/*${data.kubernetes.container.id}.log
  processors:
  - add_kubernetes_metadata:
  host: ${NODE_NAME}
  - decode_json_fields:
  fields: ["message"]
  target: ""

          - condition:
              contains:
                kubernetes.labels.app: "python-service"
            config:
              - type: log
                paths:
                  - /var/log/python-service/*.log
      ```

    * **For Python services running as system services** (not in Kubernetes):
      - Install Filebeat directly on the host
      - Configure file paths to monitor Python service logs
      - Forward to the same Logstash endpoint

    * 3. **Logstash (Processing Layer)**
        * Logstash receives logs from Filebeat, processes/enriches them, and sends to Elasticsearch.
        * **Key functions:**
            - Parse unstructured logs
            - Add additional fields (environment, region, etc.)
            - Filter and route logs based on content
            - Aggregate and transform data

        * **Sample Logstash pipeline:**

```ruby
input {
  beats {
    port => 5044
  }
}

filter {
  # Parse Spring Boot logs
  if [kubernetes][labels][app] == "springboot" {
    grok {
      match => { "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:log_level} %{NUMBER:pid} --- \[%{DATA:thread}\] %{DATA:class} : %{GREEDYDATA:log_message}" }
    }
  }
  
  # Parse Python logs
  if [kubernetes][labels][app] == "python-service" or [service][name] == "python-service" {
    json {
      source => "message"
    }
  }
  
  # Add common fields
  mutate {
    add_field => { "environment" => "${ENV:prod}" }
    add_field => { "cluster" => "${CLUSTER_NAME}" }
  }
  
  # Drop debug logs in production
  if [log_level] == "DEBUG" {
    drop { }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "logs-%{[kubernetes][namespace]}-%{+YYYY.MM.dd}"
    user => "${ES_USER}"
    password => "${ES_PASSWORD}"
  }
}
```


* 4. **Elasticsearch (Storage & Indexing)**
    - Stores processed logs in indices
    - Provides full-text search capabilities
    - Typically deployed as a StatefulSet in Kubernetes with persistent volumes
    - Configure index lifecycle management (ILM) for log rotation


* 5. **Kibana (Visualization)**
    - Web UI for searching, analyzing, and visualizing logs
    - Create dashboards for application metrics
    - Set up alerts for error patterns

* **Deployment Architecture**

```
┌─────────────────────────────────────────────┐
│           Kubernetes Cluster                │
│                                             │
│  ┌──────────────┐      ┌──────────────┐   │
│  │ Spring Boot  │      │ Spring Boot  │   │
│  │    Pod 1     │      │    Pod 2     │   │
│  │              │      │              │   │
│  │ logs to file │      │ logs to file │   │
│  └──────┬───────┘      └──────┬───────┘   │
│         │                     │            │
│         └──────────┬──────────┘            │
│                    │                       │
│         ┌──────────▼──────────┐            │
│         │  Filebeat DaemonSet │            │
│         │  (on each node)     │            │
│         └──────────┬──────────┘            │
└────────────────────┼────────────────────────┘
                     │
┌────────────────────▼────────────────────────┐
│            External Host                    │
│                                             │
│  ┌──────────────┐                          │
│  │   Python     │                          │
│  │  Service     │                          │
│  │              │                          │
│  │ logs to file │                          │
│  └──────┬───────┘                          │
│         │                                  │
│  ┌──────▼───────┐                          │
│  │   Filebeat   │                          │
│  │  (installed) │                          │
│  └──────┬───────┘                          │
└─────────┼──────────────────────────────────┘
          │
          └──────────┐
                     │
          ┌──────────▼──────────┐
          │     Logstash        │
          │  (processing layer) │
          └──────────┬──────────┘
                     │
          ┌──────────▼──────────┐
          │   Elasticsearch     │
          │  (storage/search)   │
          └──────────┬──────────┘
                     │
          ┌──────────▼──────────┐
          │      Kibana         │
          │  (visualization)    │
          └─────────────────────┘
```

* Key Interview Points to Mention

    1. **Filebeat as DaemonSet** ensures every node has a log shipper without manually deploying to each pod
    2. **Autodiscover** feature automatically detects new pods and configures log collection
    3. **Structured logging** (JSON) makes parsing much easier and more reliable
    4. **Logstash provides flexibility** for complex transformations, though Filebeat can send directly to Elasticsearch for simpler setups
    5. **Index lifecycle management** is crucial for managing disk space with time-series log data
    6. **Horizontal scaling**: Elasticsearch and Logstash can be scaled based on log volume
    7. **Security**: Use TLS for communication, enable authentication, implement RBAC in Kibana

  
---
* [x] **What are the differences between Elasticsearch, Solr, and newer alternatives like OpenSearch?**
    * **Origins & Licensing**
        - **Elasticsearch**: Changed to restrictive SSPL license in 2021 (not truly open-source anymore)
        - **Solr**: Apache 2.0 license, fully open-source, Apache Foundation governed
        - **OpenSearch**: AWS fork of Elasticsearch 7.10, created after license change, Apache 2.0

    * **Architecture**
        - **Elasticsearch**: Cloud-native, real-time focused, RESTful JSON API, distributed by default
        - **Solr**: Traditional enterprise search, more configuration-based, can be standalone or distributed (SolrCloud)
        - **OpenSearch**: Similar to Elasticsearch architecture, maintains compatibility with ES 7.x ecosystem

    * **Performance**
        - **Elasticsearch/OpenSearch**: Better for real-time indexing, time-series data, write-heavy workloads
        - **Solr**: Better for read-heavy workloads, complex queries with heavy faceting

    * **Best Use Cases:**
        * **Elasticsearch/OpenSearch:**
            - Log analytics (ELK/OpenSearch stack)
            - Real-time monitoring and APM
            - Time-series data and metrics
            - Modern application search

        * **Solr:**
            - E-commerce with complex faceting
            - Enterprise content management
            - Digital libraries
            - When you need complex joins and traditional search

    * **Key Features:**

  | Feature | Elasticsearch | Solr | OpenSearch |
    |---------|--------------|------|------------|
  | Real-time indexing | Excellent | Good | Excellent |
  | Complex faceting | Good | Excellent | Good |
  | ML capabilities | Paid | Limited | Free |
  | Security | Basic free | Plugins | Built-in free |
  | Ecosystem | Beats, Kibana* | Hadoop, Tika | Dashboards, Data Prepper |


### Scenario-Based Questions

* [x] **Your cluster is showing RED status in production. Walk me through your troubleshooting steps.**
    * **Step 1: Check Cluster Health**
      ```bash
      GET /_cluster/health?pretty
      GET /_cat/indices?v&health=red
      ```
        - Identify which indices are RED
        - Note unassigned shards count

    * **Step 2: Check Shard Allocation**
      ```bash
      GET /_cat/shards?v&h=index,shard,prirep,state,unassigned.reason
      GET /_cluster/allocation/explain?pretty
      ```
        - Find unassigned primary shards (critical)
        - Check unassigned reasons

    * **Step 3: Check Node Status**
      ```bash
      GET /_cat/nodes?v
      GET /_cat/health
      ```
        - Verify all nodes are up
        - Check disk space (>85% triggers read-only)
        - Check memory/CPU usage

    * **Step 4: Common Issues & Fixes**

        * **Issue: Disk Space Full**
      ```bash
      # Check disk usage
      GET /_cat/allocation?v
  
      * Remove read-only block
      PUT /_all/_settings
      {
       "index.blocks.read_only_allow_delete": null
      }
      ```

        * **Issue: Missing Primary Shard**
  ```bash
  # Check if data exists
  GET /_cat/shards?v | grep UNASSIGNED
  
  # Last resort: Allocate empty primary (DATA LOSS)
  POST /_cluster/reroute
  {
    "commands": [{
      "allocate_empty_primary": {
        "index": "my-index",
        "shard": 0,
        "node": "node-1",
        "accept_data_loss": true
      }
    }]
  }
  ```

    * **Issue: Replica Cannot Allocate**
  ```bash
  # Manually retry allocation
  POST /_cluster/reroute?retry_failed
  
  # Or force allocate to specific node
  POST /_cluster/reroute
  {
    "commands": [{
      "allocate_replica": {
        "index": "my-index",
        "shard": 0,
        "node": "node-2"
      }
    }]
  }
  ```

    * **Issue: Node Dropped**
        - Check node logs: `/var/log/elasticsearch/`
        - Check network connectivity
        - Restart dead node
        - Wait for automatic rebalancing

    * **Step 5: Check Cluster Settings**
      ```bash
      GET /_cluster/settings?include_defaults=true&filter_path=*.cluster.routing.allocation.*
      ```
        - Verify allocation is enabled
        - Check awareness attributes
        - Check watermark settings

    * **Step 6: Review Logs**
  ```bash
  # Check master node logs
  tail -f /var/log/elasticsearch/[cluster-name].log
  
  # Look for:
  # - OutOfMemoryError
  # - Disk space issues
  # - Network timeouts
  # - Split brain scenarios
  ```

    * **Step 7: Temporary Fixes for Production**

        * **Increase replica count temporarily**
      ```bash
      PUT /my-index/_settings
      {
        "number_of_replicas": 0
      }
      ```

        * **Disable allocation (during maintenance)**
      ```bash
      PUT /_cluster/settings
      {
        "persistent": {
          "cluster.routing.allocation.enable": "none"
        }
      }
      ```

        * **Re-enable after fix**
      ```bash
      PUT /_cluster/settings
      {
        "persistent": {
          "cluster.routing.allocation.enable": "all"
        }
      }
      ```

    * Step 8: Monitor Recovery
  ```bash
  GET /_cat/recovery?v&active_only=true
  GET /_cluster/health?wait_for_status=yellow&timeout=50s
  ```

    * **Priority Order**
        1. Check disk space (most common)
        2. Verify nodes are up
        3. Check for unassigned primary shards (critical)
        4. Review allocation explain API
        5. Check logs for errors
        6. Force allocation if necessary (understand data loss risk)


---
* [x] **A query that previously took 100ms now takes 5 seconds. How do you diagnose and fix this?**
    * **Step 1: Profile the Query**
      ```bash
      GET /my-index/_search
      {
      "profile": true,
      "query": { ... }
      }
      ```

        - Identify which part is slow (query, fetch, aggregation)
        - Check time breakdown by phase

    * **Step 2: Check Query Execution Plan**
      ```bash
      GET /my-index/_validate/query?explain=true
      {
      "query": { ... }
      }
      ```

        - Verify query is rewritten correctly
        - Check for inefficient filters

    * **Step 3: Analyze Slow Logs**
      ```bash
      # Check slow query logs
      GET /_nodes/stats/indices/search
      
      # Review logs at
      /var/log/elasticsearch/[cluster]_index_search_slowlog.log
      ```
        - Compare current vs previous execution times
        - Identify patterns

    * **Step 4: Common Causes & Fixes**

        * **Cause 1: Index Size Growth**
          ```bash
          GET /_cat/indices/my-index?v&h=index,docs.count,store.size
          GET /_cat/shards/my-index?v
          ```
          **Fix:**
            - Check if docs increased significantly
            - Consider time-based indices (daily/monthly)
            - Reduce number of shards or increase shard size

        * **Cause 2: Too Many Segments**
          ```bash
          GET /my-index/_stats/segments
          ```
          **Fix:**
          ```bash
           POST /my-index/_forcemerge?max_num_segments=1
          ```
            - Schedule during off-peak hours
            - Only for read-heavy indices

        * **Cause 3: Missing Field Data Cache**
          ```bash
          GET /_nodes/stats/indices/fielddata
          ```
          **Fix:**
          ```bash
           # Increase fielddata cache
           PUT /my-index/_settings
           {
            "index.fielddata.cache.size": "40%"
           }
          ```

        * **Cause 4: Inefficient Query Structure**
            * **Problem:** Wildcard queries
          ```json
          // Slow
          { "query": { "wildcard": { "field": "*search*" }}}
          ```

      **Fix:** Use ngrams or match queries
        ```json
        { "query": { "match": { "field": "search" }}}
        ```

        * **Problem:** Deep pagination
          ```json
          // Slow
          { "from": 10000, "size": 10 }
          ```
      **Fix:** Use search_after or scroll API
      ```json
      { "search_after": [1463538857, "tweet#654323"], "size": 10 }
      ```

        * **Cause 5: Unfiltered Aggregations**
      ```bash
      # Check aggregation cardinality
      GET /my-index/_field_caps?fields=category
      ```
      **Fix:** Add filters before aggregating
        ```json
        {
        "query": { "range": { "date": { "gte": "now-7d" }}},
        "aggs": { ... }
        }
       ```

        * **Cause 6: Circuit Breaker Tripped**
      ```bash
      GET /_nodes/stats/breaker
      ```

      **Fix:**
        ```bash
        PUT /_cluster/settings
       {
        "persistent": {
         "indices.breaker.fielddata.limit": "60%"
         }
       }
       ```

        * **Cause 7: Hot Nodes / Resource Contention**
          ```bash
          GET /_nodes/stats/os,jvm
          GET /_nodes/hot_threads
          ```
          **Fix:**
            - Check CPU/memory on nodes
            - Scale horizontally
            - Enable shard allocation awareness

        * **Cause 8: Mapping Explosion**
          ```bash
          GET /my-index/_mapping
          ```

          **Fix:**
            - Check for too many fields
            - Use nested objects instead of dynamic fields
            - Set `index.mapping.total_fields.limit`

        * **Cause 9: No Index Refresh**
          ```bash
          GET /my-index/_stats/refresh
          ```
          **Fix:**
          ```bash
          PUT /my-index/_settings
          {
            "refresh_interval": "30s"  // Default is 1s
          }
          ```

        * **Cause 10: Query Cache Disabled/Cleared**
          ```bash
          GET /_nodes/stats/indices/query_cache
          ```
          **Fix:**
          ```bash
          PUT /my-index/_settings 
          {
           "index.queries.cache.enabled": true
          }
       ```

    * **Step 5: Optimize Query**

        * Before (Slow):
  ```json
  {
    "query": {
      "bool": {
        "must": [
          { "wildcard": { "title": "*search*" }},
          { "range": { "date": { "gte": "2024-01-01" }}}
        ]
      }
    }
  }
  ```

    * After (Fast):
  ```json
  {
    "query": {
      "bool": {
        "must": [
          { "match": { "title": "search" }}
        ],
        "filter": [  // Filters are cached
          { "range": { "date": { "gte": "2024-01-01" }}}
        ]
      }
    }
  }
  ```

    * **Step 6: Check Cluster Health**
      ```bash
      GET /_cluster/stats
      GET /_cat/nodes?v&h=name,heap.percent,ram.percent,cpu,load_*
      ```
        - CPU > 80%?
        - Memory > 85%?
        - Disk I/O bottleneck?

    * **Step 7: Compare with Baseline**
      ```bash
      # Run same query on smaller dataset
      GET /my-index-2024-01/_search { ... }
    
      # Check if index-specific or cluster-wide
      ```

    * **Step 8: Enable Request Cache**
      ```json
      GET /my-index/_search?request_cache=true
      {
        "size": 0,  // Only for size=0 queries
        "aggs": { ... }
      }
      ```

    * **Quick Wins Checklist**

        1. ✓ Use filters instead of queries (cacheable)
        2. ✓ Avoid wildcards and regex on analyzed fields
        3. ✓ Use `search_after` instead of deep pagination
        4. ✓ Filter before aggregating
        5. ✓ Merge segments on read-heavy indices
        6. ✓ Increase refresh interval for bulk indexing
        7. ✓ Use routing for targeted queries
        8. ✓ Denormalize data to avoid joins
        9. ✓ Use keyword fields for exact match
        10. ✓ Enable query/request cache
