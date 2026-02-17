# Apache Druid — Complete Guide

---

## What is Druid?

Apache Druid is a **real-time analytics database** built for fast slice-and-dice queries on large event-driven datasets. It ingests streaming or batch data, stores it in a highly compressed columnar format, and returns sub-second query results even over billions of rows.

**One-line summary:** If you need to run GROUP BY, COUNT, SUM, filters, and time-series rollups on billions of events in under a second — Druid is built exactly for that.

It is not a general-purpose database. It does one thing exceptionally well: **fast aggregation queries on immutable, time-stamped data at scale.**

---

## Does Druid Run as a Cluster or Single Node?

**Druid is designed to run as a cluster.** It is made of 6 different service types, and in production each runs on its own VM. However, for development/testing, all services can run on a single machine ("micro-quickstart" mode).

There are 3 deployment tiers:

| Tier | Setup | Use Case |
|------|-------|----------|
| **Nano / Dev** | 1 VM, all services co-located | Local testing only |
| **Small Cluster** | 3–5 VMs, services grouped | Staging / small production |
| **Production Cluster** | 6+ VMs, each service isolated | High-traffic production |

---

## Cluster Architecture — Node Roles & VM Sizing

### The 6 Druid Services

```
┌─────────────────────────────────────────────────────────────┐
│                     Druid Cluster                           │
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   Kafka /    │    │   Batch      │    │   Queries    │  │
│  │   Kinesis    │    │   Files      │    │   (SQL/JSON) │  │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘  │
│         │                   │                   │          │
│         ▼                   ▼                   ▼          │
│  ┌─────────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │MiddleManager│    │  Coordinator │    │   Broker     │   │
│  │ (Ingestion) │    │ (Scheduling) │    │ (Query fanout│   │
│  └──────┬───────┘   └──────────────┘    └──────┬───────┘   │
│         │                                       │           │
│         ▼                                       ▼           │
│  ┌─────────────┐                       ┌──────────────┐    │
│  │  Historical  │◄──────────────────── │   Router     │    │
│  │ (Stores      │   Broker queries      │ (Routes to   │    │
│  │  segments)   │   segment data        │  Broker)     │    │
│  └─────────────┘                       └──────────────┘    │
│         ▲                                                   │
│  ┌─────────────┐                                           │
│  │Deep Storage │  (S3 / HDFS / GCS — source of truth)      │
│  └─────────────┘                                           │
└─────────────────────────────────────────────────────────────┘
```

### Recommended VM Config per Node (Small Production — 3 VM Setup)

In a 3-VM setup, services are grouped by resource profile:

```
VM 1 — "Master Node"          VM 2 — "Query Node"         VM 3 — "Data Node"
─────────────────────         ────────────────────         ──────────────────
Services:                     Services:                    Services:
  • Coordinator                 • Broker                     • Historical (x2)
  • Overlord                    • Router                     • MiddleManager
  • ZooKeeper                                                • Indexer

RAM:  16 GB                   RAM:  32 GB                  RAM:  64 GB
CPU:  4 vCPU                  CPU:  8 vCPU                 CPU:  16 vCPU
Disk: 100 GB SSD              Disk: 100 GB SSD             Disk: 2 TB SSD
Role: Cluster coordination    Role: Query handling         Role: Storage + ingestion
```

**Why the Data Node needs the most RAM?**
Historical nodes load segment data into memory (or memory-mapped files) for fast query access. The more RAM, the more segments stay hot in cache = faster queries.

### Full Production Setup (6 VM — Isolated Services)

| VM | Service | RAM | CPU | Disk |
|----|---------|-----|-----|------|
| VM1 | Coordinator + ZooKeeper | 16 GB | 4 vCPU | 100 GB |
| VM2 | Overlord | 16 GB | 4 vCPU | 100 GB |
| VM3 | Broker | 32 GB | 8 vCPU | 100 GB |
| VM4 | Router | 8 GB | 4 vCPU | 50 GB |
| VM5 | Historical | 64 GB | 16 vCPU | 2 TB NVMe |
| VM6 | MiddleManager | 32 GB | 8 vCPU | 500 GB |

> For high availability, run at least **2 Brokers** and **2+ Historicals** behind a load balancer.

---

## When Should You Use Druid?

| Need | Why Druid fits |
|------|----------------|
| **Sub-second queries on billions of rows** | Columnar storage + pre-aggregation + indexes |
| **Real-time dashboards and analytics** | Ingests from Kafka with seconds-level latency |
| **Time-series event data** | Built around a mandatory timestamp column |
| **High-concurrency query workloads** | Handles thousands of concurrent queries |
| **Ad-tech, clickstream, metrics, logs** | Classic Druid sweet spot — event counting at scale |
| **Slice-and-dice by many dimensions** | Bitmap indexes make filtering on any column fast |
| **You need streaming + batch in one place** | Native Kafka ingestion + batch backfill in same cluster |

---

## Why Druid Over the Alternatives

### Druid vs PostgreSQL / MySQL

PostgreSQL is a row-store — terrible for analytics that only touch a few columns across millions of rows.

```
Row store (PostgreSQL):
Row 1: [ user_id | event | country | revenue | timestamp | ... 20 more cols ]
→ Reads EVERY column of EVERY row just to access 2 columns

Columnar store (Druid):
Column: country   [ US, US, IN, DE, US, IN ... ]
Column: revenue   [ 10, 20, 5,  15, 8,  12 ... ]
→ Reads ONLY the 2 relevant columns — sub-second even on billions of rows
```

### Druid vs ClickHouse

| Aspect | Druid | ClickHouse |
|--------|-------|------------|
| **Real-time ingestion** | Native Kafka, Kinesis, HTTP | Kafka via engine, less seamless |
| **Pre-aggregation (rollup)** | Built-in, automatic at ingest | Manual via materialized views |
| **Operational complexity** | Higher (6 service types) | Lower (simpler to run) |
| **Best for** | Real-time, high-concurrency dashboards | Batch analytics, fewer concurrent users |

### Druid vs Elasticsearch

| Aspect | Druid | Elasticsearch |
|--------|-------|---------------|
| **Aggregations (SUM, COUNT, AVG)** | Excellent, pre-computed | Slow on large datasets |
| **Full-text search** | No | Yes |
| **Storage efficiency** | Very compressed (columnar) | Large index overhead |
| **Time-series rollup** | Built-in | Not native |

---

## How Druid Works Internally

### Segments — How Data is Stored

Druid splits data into **segments** — time-partitioned chunks of compressed columnar data, typically covering one hour or one day of events.

```
datasource: "page_views"

Segment: 2024-01-01T00/PT1H  (1 hour of data)
┌──────────────────────────────────────────────────┐
│  Timestamp column  │  2024-01-01 00:00 to 00:59  │
│  Dimension columns │  page, country, device_type  │
│  Metric columns    │  views, clicks, revenue      │
│  Stored as:                                       │
│    - Compressed column files                      │
│    - Bitmap index per dimension value             │
│    - Pre-rolled up at ingest (if rollup enabled)  │
└──────────────────────────────────────────────────┘
```

### The Rollup Feature

```
Raw events (Kafka):
  timestamp            page       country   views
  2024-01-01 00:01     /home      US        1
  2024-01-01 00:01     /home      US        1
  2024-01-01 00:01     /home      US        1

With rollup (granularity = 1 minute):
  timestamp            page       country   views (SUM)
  2024-01-01 00:01     /home      US        3   ← 3 rows → 1

At scale: billions of events → hundreds of millions of rows
Queries run faster because there's simply less data to scan
```

---

## Schema & Ingestion Specs

### Kafka Ingestion Spec

```json
{
  "type": "kafka",
  "dataSchema": {
    "dataSource": "page_views",
    "timestampSpec": {
      "column": "event_time",
      "format": "auto"
    },
    "dimensionsSpec": {
      "dimensions": [
        "page", "country", "device_type",
        { "type": "string", "name": "user_id" },
        { "type": "long",   "name": "session_duration_ms" }
      ]
    },
    "metricsSpec": [
      { "type": "count",    "name": "event_count" },
      { "type": "longSum",  "name": "revenue_cents", "fieldName": "revenue_cents" },
      { "type": "doubleMax","name": "max_load_time",  "fieldName": "load_time_ms" }
    ],
    "granularitySpec": {
      "segmentGranularity": "HOUR",
      "queryGranularity":   "MINUTE",
      "rollup": true
    }
  },
  "ioConfig": {
    "type": "kafka",
    "consumerProperties": {
      "bootstrap.servers": "kafka:9092"
    },
    "topic": "page-view-events",
    "inputFormat": { "type": "json" }
  },
  "tuningConfig": {
    "type": "kafka",
    "maxRowsInMemory": 1000000,
    "maxRowsPerSegment": 5000000
  }
}
```

---

## SQL Queries

```sql
-- 1. Hourly events last 24 hours
SELECT
  TIME_FLOOR(__time, 'PT1H') AS hour,
  COUNT(*)                   AS events,
  SUM(revenue_cents) / 100.0 AS revenue_usd
FROM page_views
WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '1' DAY
GROUP BY 1 ORDER BY 1;

-- 2. Top pages by traffic this week
SELECT
  page,
  COUNT(*) AS views,
  COUNT(DISTINCT user_id) AS unique_users
FROM page_views
WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '7' DAY
GROUP BY page ORDER BY views DESC LIMIT 20;

-- 3. Approximate distinct count (uses HyperLogLog — very fast)
SELECT
  page,
  APPROX_COUNT_DISTINCT(user_id) AS approx_unique_users
FROM page_views
WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '1' DAY
GROUP BY page;
```

---

## Spring Boot Integration

### Maven Dependencies

```xml
<dependencies>
    <!-- HTTP client to call Druid Broker REST API -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Kafka Consumer -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- JSON serialization -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

### application.yml

```yaml
druid:
  broker:
    url: http://druid-broker:8082   # Druid Broker SQL endpoint

spring:
  kafka:
    bootstrap-servers: kafka:9092
    consumer:
      group-id: druid-ingestion-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

---

### 1. Model — PageViewEvent

```java
package com.example.druid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PageViewEvent {

    @JsonProperty("event_time")
    private String eventTime;       // ISO-8601: "2024-01-15T14:32:00Z"

    @JsonProperty("page")
    private String page;            // e.g. "/checkout"

    @JsonProperty("country")
    private String country;         // e.g. "US"

    @JsonProperty("device_type")
    private String deviceType;      // e.g. "mobile"

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("revenue_cents")
    private long revenueCents;

    @JsonProperty("load_time_ms")
    private double loadTimeMs;
}
```

---

### 2. Druid Query Service — Read Data from Druid

This service sends SQL queries to the **Druid Broker** over HTTP and returns results.

```java
package com.example.druid.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class DruidQueryService {

    @Value("${druid.broker.url}")
    private String druidBrokerUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Execute any SQL query against Druid Broker.
     * Returns list of rows, each row is a Map of columnName -> value.
     */
    public List<Map<String, Object>> query(String sql) {
        String endpoint = druidBrokerUrl + "/druid/v2/sql";

        // Build request body
        Map<String, Object> requestBody = Map.of(
            "query", sql,
            "resultFormat", "object",   // returns list of JSON objects
            "header", true
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
            endpoint,
            HttpMethod.POST,
            request,
            JsonNode.class
        );

        // Parse response into list of maps
        return objectMapper.convertValue(
            response.getBody(),
            objectMapper.getTypeFactory()
                .constructCollectionType(List.class, Map.class)
        );
    }

    /**
     * Get hourly revenue for the last N days
     */
    public List<Map<String, Object>> getHourlyRevenue(int days) {
        String sql = String.format("""
            SELECT
              TIME_FLOOR(__time, 'PT1H')  AS hour,
              COUNT(*)                    AS event_count,
              SUM(revenue_cents) / 100.0  AS revenue_usd
            FROM page_views
            WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '%d' DAY
            GROUP BY 1
            ORDER BY 1
            """, days);

        return query(sql);
    }

    /**
     * Get top N pages by views in last N days
     */
    public List<Map<String, Object>> getTopPages(int topN, int days) {
        String sql = String.format("""
            SELECT
              page,
              COUNT(*)                        AS views,
              APPROX_COUNT_DISTINCT(user_id)  AS unique_users,
              SUM(revenue_cents) / 100.0      AS revenue_usd
            FROM page_views
            WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '%d' DAY
            GROUP BY page
            ORDER BY views DESC
            LIMIT %d
            """, days, topN);

        return query(sql);
    }

    /**
     * Get revenue breakdown by country and device type
     */
    public List<Map<String, Object>> getRevenueByCountryAndDevice(
            String startDate, String endDate) {

        String sql = String.format("""
            SELECT
              country,
              device_type,
              COUNT(*)                    AS events,
              SUM(revenue_cents) / 100.0  AS revenue_usd
            FROM page_views
            WHERE __time >= '%s' AND __time < '%s'
              AND country IN ('US', 'GB', 'DE', 'IN')
            GROUP BY country, device_type
            ORDER BY revenue_usd DESC
            """, startDate, endDate);

        return query(sql);
    }
}
```

---

### 3. Kafka Consumer — Write Data into Druid via Kafka

Druid's MiddleManager reads directly from Kafka topics. Your Spring Boot app just needs to **produce events onto the Kafka topic** — Druid handles the rest automatically via its ingestion supervisor.

```java
package com.example.druid.consumer;

import com.example.druid.model.PageViewEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PageViewKafkaConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PageViewKafkaConsumer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Consume events from upstream topic, enrich/validate,
     * then forward to the Druid ingestion topic.
     *
     * Flow:
     *   upstream-events (raw) --> this consumer --> page-view-events (Druid reads this)
     */
    @KafkaListener(topics = "upstream-events", groupId = "druid-ingestion-group")
    public void consume(String message) {
        try {
            PageViewEvent event = objectMapper.readValue(message, PageViewEvent.class);

            // Validate required fields
            if (event.getEventTime() == null || event.getUserId() == null) {
                log.warn("Skipping invalid event — missing required fields: {}", message);
                return;
            }

            // Enrich: set default device type if missing
            if (event.getDeviceType() == null) {
                event.setDeviceType("unknown");
            }

            // Forward to the Druid ingestion topic
            // Druid's MiddleManager is subscribed to "page-view-events"
            String enrichedMessage = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("page-view-events", event.getUserId(), enrichedMessage);

            log.info("Forwarded event to Druid topic: userId={}, page={}",
                event.getUserId(), event.getPage());

        } catch (Exception e) {
            log.error("Failed to process Kafka message: {}", message, e);
        }
    }
}
```

---

### 4. Kafka Producer — Send Events Directly to Druid Topic

Use this when your app generates events itself (no upstream topic needed).

```java
package com.example.druid.producer;

import com.example.druid.model.PageViewEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class PageViewEventProducer {

    private static final String DRUID_TOPIC = "page-view-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PageViewEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(String page, String country, String deviceType,
                          String userId, long revenueCents, double loadTimeMs) {
        try {
            PageViewEvent event = new PageViewEvent();
            event.setEventTime(Instant.now().toString());
            event.setPage(page);
            event.setCountry(country);
            event.setDeviceType(deviceType);
            event.setUserId(userId);
            event.setRevenueCents(revenueCents);
            event.setLoadTimeMs(loadTimeMs);

            String message = objectMapper.writeValueAsString(event);

            // Use userId as partition key so same user's events go to same partition
            kafkaTemplate.send(DRUID_TOPIC, userId, message);

            log.debug("Sent event: userId={}, page={}", userId, page);

        } catch (Exception e) {
            log.error("Failed to send event to Kafka", e);
        }
    }
}
```

---

### 5. REST Controller — Expose Druid Analytics via API

```java
package com.example.druid.controller;

import com.example.druid.producer.PageViewEventProducer;
import com.example.druid.service.DruidQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final DruidQueryService druidQueryService;
    private final PageViewEventProducer producer;

    public AnalyticsController(DruidQueryService druidQueryService,
                               PageViewEventProducer producer) {
        this.druidQueryService = druidQueryService;
        this.producer = producer;
    }

    // GET /analytics/revenue?days=7
    @GetMapping("/revenue")
    public ResponseEntity<List<Map<String, Object>>> getHourlyRevenue(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(druidQueryService.getHourlyRevenue(days));
    }

    // GET /analytics/top-pages?topN=10&days=7
    @GetMapping("/top-pages")
    public ResponseEntity<List<Map<String, Object>>> getTopPages(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(druidQueryService.getTopPages(topN, days));
    }

    // GET /analytics/country-breakdown?start=2024-01-01&end=2024-02-01
    @GetMapping("/country-breakdown")
    public ResponseEntity<List<Map<String, Object>>> getCountryBreakdown(
            @RequestParam String start,
            @RequestParam String end) {
        return ResponseEntity.ok(
            druidQueryService.getRevenueByCountryAndDevice(start, end));
    }

    // POST /analytics/track  — send a raw event into Druid via Kafka
    @PostMapping("/track")
    public ResponseEntity<String> trackEvent(@RequestBody Map<String, Object> payload) {
        producer.sendEvent(
            (String) payload.get("page"),
            (String) payload.get("country"),
            (String) payload.get("deviceType"),
            (String) payload.get("userId"),
            Long.parseLong(payload.get("revenueCents").toString()),
            Double.parseDouble(payload.get("loadTimeMs").toString())
        );
        return ResponseEntity.ok("Event queued for Druid ingestion");
    }
}
```

---

### Data Flow — End to End

```
Your App (Spring Boot)
  │
  │  POST /analytics/track
  ▼
PageViewEventProducer
  │  produces JSON to Kafka topic: "page-view-events"
  ▼
Kafka Broker
  │  Druid's MiddleManager subscribes to this topic
  ▼
Druid MiddleManager
  │  ingests, rolls up, writes segments
  ▼
Druid Historical Nodes
  │  stores compressed columnar segments
  ▼
Druid Broker
  │  handles SQL queries from Spring Boot
  ▼
DruidQueryService (Spring Boot)
  │  GET /analytics/revenue, /top-pages, etc.
  ▼
Dashboard / API Consumer
```

---

## Schema Changes

| Change | Requires Reindex? |
|--------|-------------------|
| Add new dimension | No — old data returns null |
| Add new metric | No — old data returns 0 |
| Change column type | Yes — reindex affected intervals |
| Change rollup granularity | Yes — reindex affected intervals |
| Remove a dimension | No — stop including it in new spec |

---

## What Druid Is NOT Good For

- ❌ **Point lookups** — `SELECT * WHERE id = 123` — use PostgreSQL or ScyllaDB
- ❌ **Data that gets updated or deleted** — Druid is append-only
- ❌ **Complex multi-table JOINs** — use a data warehouse for relational queries
- ❌ **Small datasets** — operational overhead not worth it under 100 GB
- ❌ **Unstructured / full-text search** — use Elasticsearch
- ❌ **Transactional workloads** — no ACID, no row-level updates

---

## Quick Reference

```
When to use Druid:
  ✅ Sub-second GROUP BY on billions of rows
  ✅ Real-time dashboards fed from Kafka
  ✅ Time-series analytics with rollup/aggregation
  ✅ High-concurrency query workloads (1000s of users)
  ✅ Ad-tech, clickstream, usage metrics, infrastructure metrics

When NOT to use Druid:
  ❌ Point lookups or row-level fetches
  ❌ Frequent updates or deletes
  ❌ Complex multi-table JOINs
  ❌ Full-text search
  ❌ Simple transactional workloads
```

---

## Resources

- [Apache Druid Docs](https://druid.apache.org/docs/latest/)
- [Druid Design Overview](https://druid.apache.org/docs/latest/design/)
- [Druid vs Other Systems](https://druid.apache.org/docs/latest/comparisons/)
- [Imply (managed Druid)](https://imply.io)