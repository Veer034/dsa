# Design Twitter Feed / Instagram Feed
> The hardest "mid-level" question. Tests: fan-out strategies, cache design, eventual consistency, the celebrity problem.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| How many DAU? | Drives everything — 100M vs 500M is a different architecture |
| Read-heavy or write-heavy? | Twitter is read-heavy (people scroll more than post) |
| What's in a tweet/post? | Text, images, videos? Influences media pipeline |
| Do we need real-time or near-real-time feed? | Polling vs WebSocket vs SSE |
| Celebrity accounts — how many followers can one user have? | This is the hard problem |
| Is like/retweet count exact or approximate? | Exact = expensive, approximate = Druid/HLL |
| Any ranking/ML recommendation? | Chronological vs algorithmic feed |

**Typical answer:** 300M DAU, users post ~5 tweets/day, average 500 followers, some celebrities with 50M+ followers, feed is near-real-time, ranking is chronological + some boosting.

---

## Scale Estimation

```
Posts per day:    300M users * 5 tweets = 1.5B tweets/day → ~17,000 writes/sec
Feed reads:       Each user checks feed 10x/day → 3B reads/day → ~35,000 reads/sec
Fanout events:    1 tweet * avg 500 followers = 500 events per write
                  17,000 writes/sec * 500 = 8.5M fanout ops/sec  ← This is the problem
Storage (tweets): 1.5B * 300 bytes = 450 GB/day text alone
```

---

## HLD Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                        Client (Web/Mobile)                     │
└──────────────────────────────┬─────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │    CDN / NGINX [LB]  │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼─────────────────┐
              │                │                 │
    ┌─────────▼──────┐ ┌───────▼──────┐ ┌───────▼────────┐
    │  Post Service  │ │ Feed Service │ │  User Service  │
    │  [Java/SBoot]  │ │ [Java/SBoot] │ │  [Java/SBoot]  │
    └─────────┬──────┘ └───────┬──────┘ └───────┬────────┘
              │                │                 │
              │         ┌──────▼──────┐          │
              │         │{Redis Cache}│          │
              │         │ Feed Cache  │          │
              │         │ (per user)  │          │
              │         └──────┬──────┘          │
              │                │ miss            │
    ┌─────────▼──────┐  ┌──────▼──────────┐  ┌──▼─────────────┐
    │  <Kafka Topic> │  │  ((ScyllaDB))   │  │  ((MySQL))     │
    │  tweet.created │  │  Tweet Store    │  │  Users/Follow  │
    └─────────┬──────┘  │  Feed Timeline  │  └────────────────┘
              │         └─────────────────┘
    ┌─────────▼────────────────────────────────────────┐
    │              Fan-out Workers (Consumer Group)     │
    │  [Normal User Fan-out]   [Celebrity Handler]      │
    │  → push to follower      → skip fan-out           │
    │    feed cache            → pull on read           │
    └──────────────────────────────────────────────────┘
              │
    ┌─────────▼──────────┐
    │   Notification Svc │ → Push / WebSocket
    └────────────────────┘
              │
    ┌─────────▼──────────┐
    │ Analytics Pipeline │ → Druid (likes, impressions, reach)
    └────────────────────┘
```

---

## The Core Problem: Fan-out

This is where 90% of your interview time should go.

### Fan-out on Write (Push Model)

When a user posts, **immediately push** the tweet ID to each follower's feed cache.

```
User A posts tweet T1
  → Kafka: tweet.created { tweetId, authorId }
  → Fan-out Worker reads follower list of A (from MySQL/Redis)
  → For each follower B: LPUSH feed:B [T1]  (Redis list, capped at 800)

Read:   GET /feed → LRANGE feed:userId 0 99 → 100 tweet IDs → bulk fetch tweet data
```

**Pros:** Feed reads are O(1), just read from Redis list
**Cons:** Celebrity with 50M followers = 50M Redis writes per tweet. Unacceptable.

### Fan-out on Read (Pull Model)

Don't pre-compute. When user reads feed, **query the DB** for who they follow, then merge timelines.

```
Read:   GET /feed
  → Fetch followees of userId (cached in Redis)
  → For each followee, query ScyllaDB: SELECT tweet_id WHERE author_id = X ORDER BY time DESC LIMIT 20
  → Merge N sorted lists → top 100 tweets → return
```

**Pros:** No fan-out problem for celebrities
**Cons:** Read is expensive — merging 500 sorted lists on every page load is slow

### Hybrid (What Twitter Actually Did — Say This)

```
Fan-out on Write for:
  → Normal users (< 10,000 followers)
  → Pre-populate Redis feed list

Fan-out on Read for:
  → Celebrity accounts (> 10,000 followers threshold)
  → On read, inject celebrity tweets into the pre-built feed
  → Only need to check a small set of "followed celebrities" per user

Result:
  → Feed reads are fast (Redis list + small celebrity merge)
  → Fan-out workers don't choke on celebrity posts
```

---

## Key Design Decisions

### Tweet Storage — ScyllaDB

```
Why ScyllaDB over MySQL for tweets?
→ Tweet writes are extremely high volume (17K/sec)
→ We query by (author_id, time) — perfect partition key for ScyllaDB
→ No complex joins needed on tweet data
→ ScyllaDB handles millions of writes/sec with predictable low latency

Table design:
  tweets_by_author (
      author_id   UUID     PARTITION KEY,
      tweet_id    TIMEUUID CLUSTERING KEY DESC,  ← time-sorted
      content     TEXT,
      media_urls  LIST<TEXT>,
      like_count  COUNTER  ← approximate
  )
```

### Feed Cache — Redis

```
Key:   feed:{userId}          → Redis Sorted Set (score = timestamp)
       OR Redis List (simpler, ordered by insert)

ZADD feed:userId <timestamp> <tweetId>
ZRANGE feed:userId 0 99 REV  → latest 100 tweet IDs

Cap at 800 entries per user (trim on each write).
If cache is cold/empty → rebuild from ScyllaDB + followee list.
```

### Like Count — Approximate with HyperLogLog / Druid

```
Exact like counts require a write per like — at scale, a tweet with 1M likes
means 1M DB writes.

Options:
1. Write to Kafka → Druid aggregates → serve from Druid (approximate, near-real-time)
2. Redis INCR for hot tweets (fast, not durable) + async flush to DB every minute
3. Hybrid: Redis for hot, DB for cold

What you say: "I'd use Redis INCR for live like count since it's atomic and fast,
with a Kafka consumer flushing to DB every 30 seconds for durability."
```

### Following/Follower Graph

```
MySQL (source of truth):
  follows (follower_id, followee_id, created_at)
  INDEX on (follower_id), INDEX on (followee_id)

Redis (cached):
  followees:{userId} → Redis Set of followee IDs (for fan-out worker)
  TTL: 1 hour, refresh on follow/unfollow event

For massive graphs (Twitter-scale): consider a dedicated graph DB
(Neo4j, or even a custom adjacency list in ScyllaDB)
```

---

## LLD — Post Tweet Flow

```
POST /tweet { content, mediaUrls }
    │
    ├─ Validate (auth, content length, rate limit)
    ├─ Generate Snowflake tweet_id
    ├─ If media: upload to S3, get CDN URLs
    ├─ Write to ScyllaDB (tweets_by_author)
    ├─ Write to MySQL (tweets table for search indexing)
    ├─ Publish to Kafka: tweet.created { tweetId, authorId, followerCount }
    └─ Return 201 to client immediately (async fan-out)

Kafka Consumer — Fan-out Worker:
    if (followerCount < 10_000):
        fetch follower IDs from Redis Set: followees:{authorId}
        for each followerId:
            ZADD feed:{followerId} <tweetTimestamp> <tweetId>
            ZREMRANGEBYRANK feed:{followerId} 0 -801  ← keep top 800
    else:
        skip fan-out (celebrity — handle at read time)
        publish to celebrity.tweet topic for notification only
```

---

## LLD — Read Feed Flow

```
GET /feed?page=1
    │
    ├─ Get userId from JWT
    ├─ Check Redis: ZRANGE feed:{userId} 0 99 REV → tweet IDs
    │       cache miss → rebuild from ScyllaDB + followee list
    │
    ├─ Check if user follows any celebrity accounts
    │       → fetch latest N tweets from celebrity timelines
    │       → merge with feed IDs by timestamp
    │
    ├─ Bulk fetch tweet content:
    │       → Redis: MGET tweet:{id} for each ID (tweet detail cache)
    │       → ScyllaDB fallback for cache misses
    │
    ├─ Enrich with user info (avatar, display name) from User Service cache
    └─ Return sorted, paginated feed
```

---

## Database Schemas

```sql
-- MySQL: Social graph + metadata
CREATE TABLE follows (
    follower_id  BIGINT NOT NULL,
    followee_id  BIGINT NOT NULL,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, followee_id),
    INDEX idx_followee (followee_id)
);

CREATE TABLE tweets_meta (
    tweet_id     BIGINT PRIMARY KEY,   -- Snowflake ID
    author_id    BIGINT NOT NULL,
    content      VARCHAR(280),
    created_at   DATETIME,
    like_count   BIGINT DEFAULT 0,
    retweet_count BIGINT DEFAULT 0,
    INDEX idx_author_created (author_id, created_at)
);
```

```
// ScyllaDB CQL: High-throughput tweet timeline
CREATE TABLE tweets_by_author (
    author_id  UUID,
    tweet_id   TIMEUUID,
    content    TEXT,
    media_urls LIST<TEXT>,
    PRIMARY KEY (author_id, tweet_id)
) WITH CLUSTERING ORDER BY (tweet_id DESC)
  AND default_time_to_live = 7776000;  -- 90 days, older in cold storage
```

---

## Elasticsearch for Search

```
On tweet publish → Kafka consumer → index to Elasticsearch

Index: tweets
{
  "tweet_id": 123456,
  "content": "Just landed in Tokyo!",
  "author_id": 789,
  "author_name": "john_doe",
  "hashtags": ["tokyo", "travel"],
  "created_at": "2025-01-01T10:00:00Z",
  "like_count": 42
}

Query: full-text + hashtag filter + date range
→ Elasticsearch handles this natively with inverted index
```

---

## Interview Tips

- **The fan-out problem is the interview.** Spend most of your time here.
- **Name the hybrid approach** — "Twitter calls this mixed fan-out, Etsy and LinkedIn use similar patterns."
- Drop **ScyllaDB** for tweet storage — it signals you know write-heavy data patterns.
- Mention **feed cache is eventually consistent** — it's fine if a tweet appears 1-2 seconds late.
- For ranking: mention **ML-based scoring can be plugged in** — score = engagement prediction — without going deep unless asked.
- Bring up **pagination cursors** (not page numbers) since new tweets arrive constantly.
