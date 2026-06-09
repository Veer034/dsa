# System Design Interview Guide — HLD & LLD
### Senior Backend Engineer (10+ Years Experience)

> **Stack in play:** Java · Spring Boot · MySQL · Kafka · Redis · Elasticsearch · Kubernetes · Docker · NGINX · ReactJS · Azure · AWS · GCP · Druid · Bloom Filter · ScyllaDB · Python · Lua Script

---

## File Index

| File | Questions Inside |
|------|-----------------|
| [`01-url-shortener-pastebin.md`](./01-url-shortener-pastebin.md) | Design TinyURL / Pastebin |
| [`02-twitter-instagram-feed.md`](./02-twitter-instagram-feed.md) | Design Twitter Feed / Instagram Feed |
| [`03-whatsapp-chat-system.md`](./03-whatsapp-chat-system.md) | Design WhatsApp / Chat System |
| [`04-netflix-youtube.md`](./04-netflix-youtube.md) | Design Netflix / YouTube |
| [`05-uber-lyft.md`](./05-uber-lyft.md) | Design Uber / Lyft Ride Matching |
| [`06-google-search.md`](./06-google-search.md) | Design Google Search / Typeahead |
| [`07-rate-limiter.md`](./07-rate-limiter.md) | Design a Rate Limiter |
| [`08-distributed-cache.md`](./08-distributed-cache.md) | Design Distributed Cache (Redis-like) |
| [`09-notification-system.md`](./09-notification-system.md) | Design Notification System |
| [`10-payment-system.md`](./10-payment-system.md) | Design Payment / Wallet System |

---

## How to Use This in an Interview

1. **Clarify before you draw** — ask scale, consistency vs availability, latency SLA, read/write ratio.
2. **HLD first** — boxes, arrows, data stores. Talk while drawing. Interviewer will stop you when they want depth.
3. **Pick 1–2 components to go deep** — don't wait to be asked, say *"I want to go deep on the feed generation since that's the hard part"*.
4. **Say trade-offs out loud** — never just say what you chose, say what you rejected and why.
5. **Mention failure modes** — what happens when Kafka consumer lags? What if Redis goes down? This separates 10-year engineers from 2-year engineers.

---

## Diagram Legend Used Throughout

```
[Service]         = Microservice / App
((Database))      = Persistent store
{Redis / Cache}   = Cache layer
<Kafka Topic>     = Async message queue
-->               = Synchronous call (HTTP/gRPC)
- ->              = Asynchronous / event-driven
[LB]              = Load Balancer / NGINX
[CDN]             = Content Delivery Network
```

---

## Tech Stack Cheat Sheet — When to Drop Which Card

| Technology | Drop It When Talking About |
|------------|---------------------------|
| **Kafka** | Event streaming, audit log, fan-out, decoupling, replay |
| **Redis + Lua** | Atomic rate limiting, leaderboard, session, pub/sub, distributed lock |
| **Elasticsearch** | Full-text search, autocomplete, log analytics, faceted filters |
| **ScyllaDB** | High write throughput, time-series, IoT telemetry, chat history |
| **Druid** | Real-time OLAP, aggregation over billions of events, dashboards |
| **Bloom Filter** | Duplicate detection, cache-miss avoidance, username existence check |
| **NGINX + Lua** | API gateway, JWT auth, dynamic routing, request rewriting |
| **Kubernetes** | Autoscaling, rolling deploys, pod disruption budgets, service mesh |
| **S3 / Azure Blob / GCS** | Object storage for video, images, exports, backups |
| **CDN (CloudFront / Akamai)** | Static assets, video segments, geo-distributed reads |

---

## Reusable Patterns (Know These Cold)

```
Pattern              When to Use
─────────────────────────────────────────────────────────────────
Outbox Pattern       Guaranteed event publish with DB write (no dual-write)
Saga (Choreography)  Distributed transactions without 2PC — order/payment flow
CQRS                 Separate read model (Elasticsearch) from write model (MySQL)
Fan-out on Write     Pre-compute feed on publish (Twitter celebrity problem)
Fan-out on Read      Compute feed at read time (celeb accounts, fresh data)
Circuit Breaker      Resilience4j — stop cascade failures downstream
Token Bucket / Sliding Window  Rate limiting in Redis + Lua
Consistent Hashing   Shard data across nodes without full resharding
Bloom Filter         Pre-check existence before hitting DB
Write-Through Cache  Write to cache + DB together for consistency
Read-Through Cache   Cache miss → fetch from DB → populate cache
```
