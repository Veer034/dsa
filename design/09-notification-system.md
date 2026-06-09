# Design a Notification System
> Tests: fan-out at scale, push/SMS/email routing, idempotency, user preferences, priority queuing.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| What channels? Email, SMS, Push, In-app, WhatsApp? | Each channel has different latency and cost |
| Volume? | 1M/day vs 1B/day is very different |
| Real-time or batch? | Marketing blast vs transactional alert |
| Priority levels? | OTP (immediate) vs newsletter (best effort) |
| User preferences / opt-out? | Compliance (GDPR, CAN-SPAM) |
| Retry on failure? | Idempotency key needed |
| Notification templates? | Dynamic vs static content |
| Multi-tenant SaaS? | Tenant-level rate limits, custom templates |

**Typical answer:** All channels (push, email, SMS, in-app), 1B notifications/day, transactional + marketing, priority queues, user preferences, retries with dedup.

---

## Scale Estimation

```
1B notifications/day → ~11,600/sec average
Peak (marketing blast at 9 AM): 100x → 1.16M/sec spike

Channel breakdown:
  Push (FCM/APNs): 60% → 700M/day  (cheapest, fastest)
  Email:           25% → 250M/day  (SendGrid / SES throughput: 100K/sec)
  SMS:             10% → 100M/day  (Twilio: expensive, reserved for OTPs)
  In-app:           5% → 50M/day   (real-time, WebSocket)

Throughput needed per channel needs independent scaling.
```

---

## HLD Diagram

```
         ┌───────────────────────────────────────────────┐
         │              Event Sources                    │
         │  [Order Service] [Auth Service] [Marketing]   │
         └─────────────────────┬─────────────────────────┘
                               │ publish notification event
         ┌─────────────────────▼─────────────────────────┐
         │          Notification API Service             │
         │          [Java / Spring Boot]                 │
         │  - Validate & enrich                          │
         │  - Check user preferences                    │
         │  - Deduplicate (Bloom Filter / Redis)         │
         │  - Route to priority queue                   │
         └──────────────────┬────────────────────────────┘
                            │
         ┌──────────────────▼────────────────────────────┐
         │          <Kafka Topics (by priority)>         │
         │   notif.critical   (OTP, alerts)   → lag=0   │
         │   notif.high       (order updates)            │
         │   notif.normal     (social, activity)         │
         │   notif.low        (marketing, promos)        │
         └──┬──────────┬──────────────┬──────────────────┘
            │          │              │
  ┌─────────▼──┐ ┌─────▼──────┐ ┌────▼──────────┐
  │  Push      │ │   Email    │ │     SMS       │
  │  Dispatcher│ │  Dispatcher│ │  Dispatcher   │
  │  [Java]    │ │  [Java]    │ │  [Java]       │
  └─────────┬──┘ └─────┬──────┘ └────┬──────────┘
            │          │              │
  ┌─────────▼──┐ ┌─────▼──────┐ ┌────▼──────────┐
  │ FCM / APNs │ │  SendGrid  │ │   Twilio      │
  │ (Google/   │ │  AWS SES   │ │   SNS         │
  │  Apple)    │ │            │ │               │
  └─────────┬──┘ └─────┬──────┘ └────┬──────────┘
            │          │              │
  ┌─────────▼──────────▼──────────────▼──────────┐
  │         Delivery Receipt Handler              │
  │    (delivery status → ScyllaDB + Kafka)       │
  └───────────────────────────────────────────────┘

Side services:
  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐
  │ Preference Svc  │  │  Template Svc    │  │  Analytics Svc   │
  │ ((MySQL+Redis)) │  │  ((MySQL+S3))    │  │  Druid           │
  └─────────────────┘  └──────────────────┘  └──────────────────┘
```

---

## Key Design Decisions

### 1. Priority Queue Architecture

```
Not all notifications are equal:
  OTP / 2FA:     MUST deliver in < 5 seconds → critical
  Order update:  Should deliver in < 30 seconds → high
  Social likes:  OK within 5 minutes → normal
  Newsletter:    OK within hours → low

Kafka topics by priority:
  notif.critical  → 20 partitions, consumers always running, no lag tolerated
  notif.high      → 10 partitions
  notif.normal    → 5 partitions
  notif.low       → 2 partitions, consumers throttled, rate limited

Why separate topics and not a single topic with priority field?
  → Kafka is a FIFO queue per partition
  → A low-priority marketing blast can starve critical OTPs if in same topic
  → Separate topics = separate consumer groups = independent throughput

Dead Letter Queue:
  → Failed deliveries after 3 retries → notif.dlq
  → Monitored, alerted, replayable
```

### 2. Deduplication

```
Problem: same notification sent multiple times due to:
  - Kafka consumer retries
  - Network timeout → sender retries
  - Bug in upstream service publishing duplicate events

Solutions:

A. Idempotency key:
   → Every notification event has a unique idempotency_key (UUID from sender)
   → Before processing: check Redis SET notif_processed:{idempotency_key} EX 86400
   → If already SET → skip (already delivered)
   → SETNX (set if not exists) → atomic dedup

B. Bloom Filter (pre-check):
   → Bloom filter loaded with recently processed idempotency keys
   → False positive check: mightContain(key)? → if false, definitely not processed
   → Reduces Redis lookups by ~70% (most events are new)

C. Database dedup (strong guarantee):
   → INSERT IGNORE INTO notifications (idempotency_key, ...) 
   → Unique constraint on idempotency_key
   → If INSERT returns 0 rows affected → duplicate, skip

Production: Bloom Filter + Redis SETNX + DB dedup as fallback
```

### 3. User Preferences & Opt-out

```
Data model:
  notification_preferences (
      user_id          BIGINT,
      channel          ENUM('push', 'email', 'sms', 'inapp'),
      notification_type VARCHAR(50),   -- 'order_update', 'marketing', 'security'
      is_enabled        BOOLEAN DEFAULT TRUE,
      quiet_hours_start TIME,          -- e.g., 22:00
      quiet_hours_end   TIME,          -- e.g., 08:00
      timezone          VARCHAR(50),
      PRIMARY KEY (user_id, channel, notification_type)
  )

Check flow:
  1. Notification API receives event for userId + notification_type
  2. Fetch preferences from Redis (cached, TTL 30min)
  3. If channel disabled for this type → skip
  4. If in quiet hours for user's timezone → delay to quiet_hours_end
  5. If user unsubscribed globally → skip
  6. Proceed

Quiet hours implementation:
  → Convert "send now" to "send at" based on timezone
  → Delayed messages re-queued to Kafka with timestamp as key
  → Kafka consumer skips messages with future timestamp (or use scheduled delivery)
  → Simple: store delayed notifications in MySQL, cron job re-enqueues at right time
```

### 4. Retry Strategy with Exponential Backoff

```
Failure scenarios:
  → FCM returns 5xx → retry
  → Email provider rate limit → backoff and retry
  → SMS gateway down → failover to backup gateway

Retry policy:
  Attempt 1: immediate
  Attempt 2: +30 seconds
  Attempt 3: +2 minutes
  Attempt 4: +10 minutes
  Attempt 5: → Dead Letter Queue, alert on-call

Implementation options:
  A. Kafka consumer: catch exception → sleep → re-consume (blocks partition)
  B. Retry topic: failed message → notif.retry.{attempt} with delayed processing
  C. DB retry queue: failed notifications in MySQL with next_retry_at column
     → Scheduler picks up and re-enqueues every minute (simple, reliable)

For marketing (low priority):
  → Failure at peak time → pause, retry next off-peak window
  → Throttle to respect provider rate limits (SendGrid: 100 emails/sec per IP)

Failover:
  → Primary: SendGrid → fallback: AWS SES (different provider, different infra)
  → Primary: FCM → APNs fallback for iOS (same channel, different protocol)
  → Circuit breaker (Resilience4j) per provider
```

### 5. Template Engine

```
Dynamic notifications:
  "Your order {orderId} for ₹{amount} has been {status}."
  → Template stored in MySQL/S3
  → Variables substituted at send time from event payload

Template versioning:
  → templates (template_id, version, channel, content, variables[], status)
  → A/B testing: send template_v1 to 50%, template_v2 to 50%
  → Track open rates per version in Druid

Multi-language:
  → template_translations (template_id, locale, content)
  → User's preferred locale from User Service → pick right translation

Rich push notifications:
  → Payload includes image_url, action_buttons, deep_link
  → FCM: 4KB payload limit → keep template compact, deep link for details

Email:
  → HTML templates stored in S3
  → Handlebars / Mustache for variable substitution
  → Rendered HTML → SendGrid API → delivered
```

### 6. In-App Notifications (Real-Time)

```
Approach: WebSocket or Server-Sent Events (SSE)

Flow:
  → Notification event → Kafka topic: notif.inapp
  → In-App Notification Service subscribes (per user connection)
  → User has WebSocket connection → push immediately
  → User offline → store in ScyllaDB: inapp_notifications(userId, notif_id, content, is_read, created_at)
  → On reconnect → fetch unread notifications

Badge count:
  → Redis INCR unread_count:{userId} on new notification
  → Redis DECR on mark-as-read
  → Badge count returned in API response or WebSocket event

Mark as read:
  → Batch API: PATCH /notifications/read { ids: [1,2,3] }
  → Update ScyllaDB: is_read = true WHERE notif_id IN (...)
  → Kafka: notif.read event → analytics

Pagination:
  → ScyllaDB: SELECT * FROM inapp_notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 20
  → Cursor-based pagination (last_notif_id from previous page)
```

---

## Analytics Pipeline

```
Track:
  - Sent: notification left our system
  - Delivered: FCM/SendGrid confirmed delivery
  - Opened: user clicked/opened (tracked via tracking pixel or push open event)
  - Converted: user took action (made purchase, etc.)

Pipeline:
  notif.sent, notif.delivered, notif.opened events → Kafka
  → Druid real-time ingestion → dashboard

Metrics to monitor:
  - Delivery rate (sent vs delivered) — alert if < 95%
  - Open rate by channel, notification_type, hour
  - Lag on critical Kafka topic — alert if > 0
  - Provider error rates — circuit breaker trigger

Druid query example:
  SELECT notification_type, channel,
         COUNT(*) as sent,
         SUM(CASE WHEN status='delivered' THEN 1 END) / COUNT(*) as delivery_rate
  FROM notifications_events
  WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '1' HOUR
  GROUP BY 1, 2
```

---

## Code Skeleton — Notification API

```java
@Service
public class NotificationService {

    public void send(NotificationRequest request) {
        // 1. Validate
        validateRequest(request);

        // 2. Dedup check
        if (isDuplicate(request.getIdempotencyKey())) return;

        // 3. Check user preferences
        List<Channel> enabledChannels = preferenceService
            .getEnabledChannels(request.getUserId(), request.getType());

        // 4. Enrich (template rendering, user data)
        NotificationPayload payload = templateService.render(request);

        // 5. Route to Kafka by priority
        String topic = "notif." + request.getPriority().name().toLowerCase();
        for (Channel channel : enabledChannels) {
            NotificationEvent event = NotificationEvent.builder()
                .userId(request.getUserId())
                .channel(channel)
                .payload(payload)
                .idempotencyKey(request.getIdempotencyKey())
                .scheduledAt(resolveScheduleTime(request, channel))
                .build();
            kafkaTemplate.send(topic, request.getUserId().toString(), event);
        }

        // 6. Record in DB (for dedup + status tracking)
        notificationRepository.save(toEntity(request));
    }

    private boolean isDuplicate(String idempotencyKey) {
        if (!bloomFilter.mightContain(idempotencyKey)) return false;  // fast path
        Boolean setResult = redisTemplate.opsForValue()
            .setIfAbsent("notif:" + idempotencyKey, "1", Duration.ofDays(1));
        return !Boolean.TRUE.equals(setResult);
    }
}
```

---

## Interview Tips

- **Priority queues via separate Kafka topics** — this is the key architectural insight interviewers want.
- **Dedup** (Bloom Filter + Redis SETNX) — shows you understand real-world retry problems.
- **Quiet hours** with timezone handling — shows product thinking.
- Mention **provider failover** — SMS primary → fallback, Email primary → fallback.
- **Dead letter queue + alerting** — shows operational maturity.
- Drop **Druid for analytics** — open/delivery rate dashboards are core product metrics.
- Mention **GDPR compliance**: right to delete, unsubscribe links, suppress lists.
