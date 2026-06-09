# Design Google Search / Typeahead Autocomplete
> Tests: inverted index, web crawler, Trie / prefix structures, ranking, distributed indexing.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| Full web search or domain-specific search? | Web crawler scope changes drastically |
| Autocomplete / typeahead only, or full search? | Trie vs full inverted index |
| How many QPS for typeahead? | Google-scale = 10M+ QPS |
| Freshness requirements? | Real-time index vs batch? |
| Personalization? | Per-user suggestions vs global |
| Multi-language? | Tokenization, stemming complexity |
| How many queries/day? | Drives caching strategy |

**Typical answer (Typeahead focused):** 10M DAU, 10 suggestions per query, < 100ms latency, global, top N popular queries, some personalization.

---

## Scale Estimation

```
Typeahead:
  10M DAU * 10 searches/day * 20 keystrokes avg = 2B typeahead requests/day
  → ~23,000 QPS

  Response must be < 100ms (perceived instant)
  
  Data: top 10B unique queries ever → need prefix index

Web Crawler (if asked):
  Web has ~5B pages, crawl all within 30 days
  5B / (30 * 86400) = ~1,900 pages/sec needed
  Avg page 100KB → 190 MB/sec download bandwidth
```

---

## HLD Diagram — Typeahead

```
┌──────────────────────────────────────────┐
│                 Client                   │
│  User types: "sys" → "syst" → "syste"   │
└───────────────────┬──────────────────────┘
                    │ GET /suggest?q=syste (debounced 100ms)
           ┌────────▼────────┐
           │   CDN / NGINX   │  ← Cache frequent prefixes at edge
           └────────┬────────┘
                    │ cache miss
           ┌────────▼────────────┐
           │  Typeahead Service  │  [Java/SBoot]
           └────────┬────────────┘
                    │
         ┌──────────▼──────────┐
         │   {Redis Cache}     │  prefix → [top 10 suggestions]
         │   prefix:syste      │  TTL: 10 min
         └──────────┬──────────┘
                    │ miss
         ┌──────────▼──────────┐
         │  Trie Service       │  In-memory Trie (JVM heap)
         │  or Elasticsearch   │  prefix query on search index
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │  Query Aggregator   │  Batch job: compute top queries
         │  [Python / Spark]   │  per prefix daily
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │  ((MySQL / S3))     │  query_count(query, count, date)
         │  Raw query logs     │  aggregated by prefix
         └─────────────────────┘
                    ▲
         ┌──────────┴──────────┐
         │  <Kafka Topic>      │  search.query events
         │  (every search)     │  → raw log → aggregation pipeline
         └─────────────────────┘
```

---

## Key Design Decisions

### 1. Data Structure — Trie vs Elasticsearch

```
Option A: Trie (Prefix Tree)
  → Each node = one character
  → Each node stores top-K (10) suggestions weighted by query frequency
  → Lookup: O(prefix_length) — very fast
  → Problem: doesn't fit in single machine at Google scale
  → Solution: shard Trie by prefix (a-e on node1, f-j on node2...)

                root
               /    \
              s      p
             / \      \
            y   e      y
           /     \      \
          s       a      t
         / \       \      \
        t   t       r      h
        |   |       |      |
        e   e       c      o
        |   |       |      |  [top suggestions at each terminal]
       [m] [d]     [h]    [n]

Option B: Elasticsearch Prefix Query (Recommended for interviews)
  → Index all queries with their frequency score
  → Query: { "prefix": { "query_text": "syste" }, "size": 10 }
  → Sort by frequency score descending
  → Much simpler to maintain, handles updates gracefully
  → Edge n-gram tokenizer for efficient prefix search

What you say: "For a practical production system I'd use Elasticsearch with
edge n-gram tokenization. Pure Trie is academically elegant but operationally
complex. That said, Google likely uses a distributed Trie internally for microsecond latency."
```

### 2. Query Frequency Computation

```
Raw event flow:
  User searches → Kafka: search.query { query, userId, timestamp }
  
Batch aggregation (daily Spark job):
  spark.read("s3://search-logs/date=2025-01-01/")
    .groupBy("query").count()
    .filter("count > 100")  ← noise filter
    .write("mysql://query_frequencies")

Real-time aggregation (for trending queries):
  Kafka → Flink/Spark Streaming → tumbling 1-hour window
  → ZINCRBY trending_queries:{hour} 1 {query}  (Redis sorted set)
  → Top 1000 trending queries refreshed hourly

Combine:
  final_score = historical_frequency * 0.7 + trending_score * 0.3
```

### 3. Caching Strategy — The Most Important Part

```
Level 1: CDN Edge Cache
  → Cache at CloudFront/Akamai
  → Key: /suggest?q=<prefix>&lang=en
  → TTL: 5 minutes for common prefixes
  → Covers ~60-70% of traffic (common prefixes: "the", "how", "what")

Level 2: Redis Cache (Application Layer)
  → Key: suggest:{prefix}:{lang}
  → Value: JSON array of 10 suggestions
  → TTL: 10 minutes
  → LRU eviction: top 10M prefixes stay in memory

Level 3: In-process Cache (JVM)
  → Caffeine cache per Typeahead Service pod
  → Top 100K prefixes (most common) held in heap
  → No network hop — microsecond reads
  → Invalidated on daily Trie/ES update

  Hit rate target: L1 60% + L2 30% + L3 8% = 98% cache hit rate
  Only 2% hit backend Elasticsearch
```

### 4. Personalization

```
User-specific suggestions (if asked):
  → Log per-user search history in ScyllaDB: user_queries(userId, query, timestamp)
  → On typeahead request: 
      global_suggestions = top 10 from Elasticsearch/Trie
      personal_boost = queries user has typed before matching prefix
      merge: personal matches at top, then global

  Implementation:
      GET /suggest?q=syste&userId=123
      → Fetch user's recent queries matching prefix "syste" from Redis (SET user_history:123)
      → Intersect with global suggestions
      → Personal matches get +50% score boost

  Privacy: user history stored client-side too (local autocomplete)
  GDPR: user_queries table with right-to-delete
```

---

## Full Web Search — HLD (Bonus)

```
Three major subsystems:

1. Web Crawler
   → URL Frontier: BFS queue of URLs to crawl (stored in Kafka)
   → Fetcher: downloads page, extracts links and text
   → URL dedup: Bloom Filter (don't re-crawl same URL)
   → robots.txt respect + politeness delay per domain
   → Store: raw HTML in S3, extracted text in document store

2. Indexer
   → Parse HTML → tokenize → remove stopwords → stem
   → Build inverted index: term → [(docId, tf-idf score, position)]
   → Distributed: shard by term hash across index servers
   → Elasticsearch handles this for you in practice

3. Query Processor + Ranking
   → Parse query → expand synonyms → fetch candidate docs from index
   → Rank by:
       TF-IDF (term frequency × inverse document frequency)
       PageRank (authority of the page)
       Freshness (recently updated = bonus)
       User engagement (CTR, bounce rate) — real Google signal
   → Return top 10 results

Inverted Index structure:
  term: "java"
  → [(docId: 123, score: 0.85, positions: [12, 45, 89]),
     (docId: 456, score: 0.72, positions: [5]),
     ...]
```

---

## Elasticsearch Edge N-Gram Config (Code Level)

```json
// Index settings for typeahead
{
  "settings": {
    "analysis": {
      "filter": {
        "edge_ngram_filter": {
          "type": "edge_ngram",
          "min_gram": 1,
          "max_gram": 20
        }
      },
      "analyzer": {
        "autocomplete": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "edge_ngram_filter"]
        },
        "autocomplete_search": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "query_text": {
        "type": "text",
        "analyzer": "autocomplete",
        "search_analyzer": "autocomplete_search"
      },
      "frequency": { "type": "long" }
    }
  }
}
```

```java
// Typeahead query
SearchRequest request = SearchRequest.of(r -> r
    .index("queries")
    .query(q -> q
        .match(m -> m
            .field("query_text")
            .query(prefix)
        )
    )
    .sort(s -> s.field(f -> f.field("frequency").order(SortOrder.Desc)))
    .size(10)
);
```

---

## Interview Tips

- **Lead with the caching strategy** — 23K QPS with < 100ms latency is impossible without aggressive caching at every layer.
- **Mention Bloom Filter for crawler URL deduplication** — textbook usage.
- **Edge n-gram vs Trie** — explain both, recommend Elasticsearch for production simplicity, Trie for pure performance.
- Mention **debounce on client** (100-200ms) to reduce actual QPS from keystrokes to queries.
- For ranking: drop **TF-IDF + PageRank** and say you'd also add **CTR feedback loop** — real signal Google uses.
- Mention **geo-specific suggestions**: "system design" might trend higher in India than in UK — serve region-specific index shards.
