# HLD: Notification System (Fan-Out, Retries, Multi-Channel)

> **Experience Level:** 10+ Years Java | Spring Boot · Kafka · Redis · MySQL · Elasticsearch · ScyllaDB · Druid

---

## 🔁 Clarifying Questions (You → Interviewer)

| # | Question | Why It Matters |
|---|----------|----------------|
| 1 | What channels must be supported? Push, SMS, Email, In-app? | Channel abstraction |
| 2 | Are notifications transactional (must deliver) or best-effort? | Retry and DLQ strategy |
| 3 | Is this for a social app (follow/like) or operational (OTP, alerts)? | Fan-out vs targeted delivery |
| 4 | What's the scale — notifications per second at peak? | Kafka partition count |
| 5 | Do we need user preference management (opt-out per channel/category)? | Preference service |
| 6 | Do we need deduplication? (e.g., user shouldn't get same notification twice) | Idempotency design |
| 7 | Is there a priority system (high-priority OTP vs low-priority marketing)? | Separate queues/topics |
| 8 | Do we need delivery analytics (open rate, click rate)? | Tracking pipeline |
| 9 | What is the retry policy on failure — fixed, exponential backoff? | Retry and DLQ design |

---

## 🔁 Expected Follow-up Questions (Interviewer → You)

- "Explain the difference between fan-out on write vs fan-out on read."
- "How do you prevent a user from receiving 10,000 duplicate notifications after a retry storm?"
- "How do you handle a third-party provider (FCM/Twilio) being temporarily down?"
- "Walk me through the full path of a 'someone liked your post' notification."
- "How do you rate-limit notifications per user to prevent spam?"
- "What is a Dead Letter Queue and when do you use it?"
- "How does your system handle 50M users needing a notification simultaneously?"

---

## Scale Estimation

```
Events per second:  100K notification triggers/sec (peak social event)
Fan-out example:    1M followers → 1M push notifications per celebrity post
Throughput:         Push: 500K/sec, Email: 50K/sec, SMS: 10K/sec
Deduplication TTL:  24 hours (Redis bloom filter or set)
Retry max:          5 attempts with exponential backoff
```

---

## System Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     Event Producers                                       │
│  (Ride Service, Social Service, Order Service, Auth Service)              │
└─────────────────────────────────┬────────────────────────────────────────┘
                                  │ Kafka: raw.events
                                  ▼
                   ┌──────────────────────────┐
                   │   Notification Trigger    │
                   │   Service (Kafka Consumer)│
                   │  - Resolve recipients     │
                   │  - Apply user preferences │
                   │  - Fan-out to channels    │
                   └──────┬───────────────────┘
                          │ Kafka: notifications.push
                          │        notifications.email
                          │        notifications.sms
                          │        notifications.inapp
              ┌───────────┼──────────────────────┐
              ▼           ▼                       ▼
     ┌────────────┐  ┌──────────┐         ┌───────────────┐
     │Push Worker │  │  Email   │         │  SMS Worker   │
     │(FCM/APNs)  │  │  Worker  │         │  (Twilio)     │
     │            │  │ (SendGrid│         │               │
     └─────┬──────┘  └────┬─────┘         └──────┬────────┘
           │               │                      │
           └───────────────┴──────────────────────┘
                           │ Failures
                           ▼
              ┌─────────────────────────┐
              │     Retry Service        │
              │  (Exponential Backoff)   │
              │  → Kafka: retry.queue    │
              └─────────────┬───────────┘
                            │ After max retries
                            ▼
              ┌─────────────────────────┐
              │   Dead Letter Queue      │
              │   (Kafka DLQ topic)      │
              │   + Alert Engineering    │
              └─────────────────────────┘

Delivery receipts → Kafka: notification.receipts → Druid (analytics)
```

---

## Event Model

```java
// Raw event from any service
public record NotificationEvent(
    String eventId,
    String eventType,       // RIDE_COMPLETED, POST_LIKED, OTP_REQUEST, ORDER_PLACED
    String sourceService,
    String actorId,         // who caused the event
    String targetId,        // main target (userId, orderId, rideId)
    Map<String, Object> payload,
    Instant occurredAt
) {}

// Resolved, ready-to-send notification
public record Notification(
    String notificationId,
    String userId,
    NotificationChannel channel,    // PUSH, EMAIL, SMS, IN_APP
    NotificationPriority priority,  // HIGH, NORMAL, LOW
    String title,
    String body,
    Map<String, String> data,       // deep-link, action payload
    String templateId,
    Instant createdAt,
    int retryCount
) {}

public enum NotificationChannel { PUSH, EMAIL, SMS, IN_APP }
public enum NotificationPriority { HIGH, NORMAL, LOW }
```

---

## Notification Trigger Service (Fan-Out Engine)

```java
@Service
public class NotificationTriggerService {

    @Autowired private UserPreferenceService  preferenceService;
    @Autowired private TemplateService        templateService;
    @Autowired private DeduplicationService   deduplication;
    @Autowired private KafkaTemplate<String, Notification> kafka;

    @KafkaListener(topics = "raw.events", groupId = "notification-trigger",
                   concurrency = "20")
    public void onEvent(NotificationEvent event) {
        // 1. Resolve recipients (may be 1 user or millions for broadcast)
        List<String> recipients = resolveRecipients(event);

        // 2. Fan-out (batch for large volumes)
        List<List<String>> batches = Lists.partition(recipients, 1000);

        for (List<String> batch : batches) {
            batch.parallelStream().forEach(userId -> processForUser(userId, event));
        }
    }

    private void processForUser(String userId, NotificationEvent event) {
        // 3. Check user preferences (opt-out, do-not-disturb)
        UserPreference prefs = preferenceService.get(userId);
        if (!prefs.isEnabled(event.eventType())) return;

        // 4. Deduplication check
        String dedupKey = event.eventType() + ":" + event.actorId() + ":" + userId;
        if (deduplication.isDuplicate(dedupKey)) return;

        // 5. Resolve channels
        List<NotificationChannel> channels = prefs.getEnabledChannels(event.eventType());

        // 6. Render notification from template
        NotificationContent content = templateService.render(event.eventType(), event.payload(),
                                                              prefs.getLocale());

        // 7. Publish to channel-specific topics
        for (NotificationChannel channel : channels) {
            Notification notification = new Notification(
                UUID.randomUUID().toString(), userId, channel,
                getPriority(event.eventType()), content.title(), content.body(),
                content.data(), content.templateId(), Instant.now(), 0
            );
            kafka.send("notifications." + channel.name().toLowerCase(),
                       userId, notification);
        }
    }

    private List<String> resolveRecipients(NotificationEvent event) {
        return switch (event.eventType()) {
            case "POST_LIKED"      -> List.of(event.targetId()); // post owner
            case "NEW_FOLLOWER"    -> List.of(event.targetId());
            case "SYSTEM_BROADCAST"-> userService.getAllActiveUserIds(); // millions
            case "OTP_REQUEST"     -> List.of(event.actorId());
            default                -> List.of(event.targetId());
        };
    }
}
```

---

## Push Notification Worker (FCM / APNs)

```java
@Service
public class PushNotificationWorker {

    @Autowired private FcmService   fcmService;
    @Autowired private ApnsService  apnsService;
    @Autowired private DeviceService deviceService;
    @Autowired private RetryService  retryService;
    @Autowired private DeliveryTracker tracker;

    @KafkaListener(topics = "notifications.push", groupId = "push-worker",
                   concurrency = "30")
    public void onPushNotification(Notification notification) {
        List<DeviceToken> devices = deviceService.getTokens(notification.userId());

        for (DeviceToken device : devices) {
            try {
                DeliveryResult result = switch (device.platform()) {
                    case ANDROID -> fcmService.send(device.token(), notification);
                    case IOS     -> apnsService.send(device.token(), notification);
                };

                tracker.record(notification.notificationId(), device.platform(),
                               DeliveryStatus.DELIVERED);

            } catch (ThirdPartyUnavailableException e) {
                log.warn("Push provider down, scheduling retry for {}", notification.notificationId());
                retryService.scheduleRetry(notification);
            } catch (InvalidTokenException e) {
                log.info("Stale token, removing: {}", device.token());
                deviceService.removeToken(device.token()); // clean up stale tokens
            }
        }
    }
}
```

---

## Retry Service with Exponential Backoff

```java
@Service
public class RetryService {

    @Autowired private KafkaTemplate<String, Notification> kafka;
    @Autowired private StringRedisTemplate redis;

    private static final int MAX_RETRIES = 5;
    private static final long BASE_DELAY_MS = 1_000; // 1 second

    public void scheduleRetry(Notification notification) {
        int nextRetry = notification.retryCount() + 1;

        if (nextRetry > MAX_RETRIES) {
            sendToDLQ(notification);
            return;
        }

        // Exponential backoff: 1s, 2s, 4s, 8s, 16s
        long delayMs = BASE_DELAY_MS * (long) Math.pow(2, nextRetry - 1);

        Notification retried = notification.withRetryCount(nextRetry);

        // Delay via Redis (schedule key that expires after delay)
        String scheduleKey = "retry:scheduled:" + notification.notificationId();
        redis.opsForValue().set(scheduleKey,
            objectMapper.writeValueAsString(retried),
            Duration.ofMillis(delayMs));

        // A separate retry-scheduler polls Redis or use Kafka delay topic
        kafka.send(new ProducerRecord<>(
            "notifications." + notification.channel().name().toLowerCase(),
            null,
            System.currentTimeMillis() + delayMs,  // Kafka timestamp
            notification.userId(),
            retried
        ));
    }

    private void sendToDLQ(Notification notification) {
        kafka.send("notifications.dlq", notification);
        alertService.page("NOTIFICATION_DLQ", notification.notificationId());
    }
}
```

---

## Dead Letter Queue Processor

```java
@Service
public class DLQProcessor {

    @KafkaListener(topics = "notifications.dlq", groupId = "dlq-processor")
    public void processDLQ(Notification notification) {
        // 1. Persist failed notification for audit
        dlqRepository.save(new DLQRecord(notification, Instant.now(), "MAX_RETRIES_EXCEEDED"));

        // 2. For HIGH priority (e.g., OTP), page on-call immediately
        if (notification.priority() == NotificationPriority.HIGH) {
            alertService.pageOnCall("CRITICAL: OTP notification failed for user: "
                                    + notification.userId());
        }

        // 3. Attempt manual fallback (e.g., SMS if push failed)
        if (notification.channel() == NotificationChannel.PUSH) {
            fallbackToSms(notification);
        }
    }

    private void fallbackToSms(Notification notification) {
        String phone = userService.getPhoneNumber(notification.userId());
        if (phone != null) {
            smsService.sendRaw(phone, notification.body());
        }
    }
}
```

---

## Deduplication Service

```java
@Service
public class DeduplicationService {

    @Autowired private StringRedisTemplate redis;
    private static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    /**
     * Returns true if this notification was already sent (duplicate).
     * Uses Redis SET with NX to atomically check-and-set.
     */
    public boolean isDuplicate(String dedupKey) {
        String redisKey = "notif:dedup:" + dedupKey;
        Boolean isNew = redis.opsForValue().setIfAbsent(redisKey, "1", DEDUP_WINDOW);
        return Boolean.FALSE.equals(isNew); // true = already exists = duplicate
    }
}
```

---

## User Preference Service

```java
@Service
public class UserPreferenceService {

    @Autowired private UserPreferenceRepository repo;
    @Autowired private StringRedisTemplate redis;

    public UserPreference get(String userId) {
        // Cache preferences in Redis (updated rarely)
        String cacheKey = "user:prefs:" + userId;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) return deserialize(cached);

        UserPreference pref = repo.findByUserId(userId)
                .orElse(UserPreference.defaults(userId));
        redis.opsForValue().set(cacheKey, serialize(pref), Duration.ofMinutes(30));
        return pref;
    }

    public void updatePreference(String userId, String notificationType,
                                  NotificationChannel channel, boolean enabled) {
        repo.upsert(userId, notificationType, channel, enabled);
        redis.delete("user:prefs:" + userId); // invalidate cache
    }
}
```

---

## MySQL Schema

```sql
CREATE TABLE notifications (
    notification_id  VARCHAR(36) PRIMARY KEY,
    user_id          VARCHAR(36) NOT NULL,
    channel          ENUM('PUSH','EMAIL','SMS','IN_APP'),
    event_type       VARCHAR(100),
    title            VARCHAR(255),
    body             TEXT,
    status           ENUM('PENDING','SENT','DELIVERED','FAILED','DLQ'),
    retry_count      INT DEFAULT 0,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at          DATETIME,
    INDEX idx_user_status (user_id, status),
    INDEX idx_created  (created_at)
);

CREATE TABLE user_notification_preferences (
    user_id           VARCHAR(36),
    notification_type VARCHAR(100),
    channel           ENUM('PUSH','EMAIL','SMS','IN_APP'),
    is_enabled        BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (user_id, notification_type, channel)
);

CREATE TABLE device_tokens (
    token_id     VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL,
    device_token TEXT NOT NULL,
    platform     ENUM('ANDROID','IOS','WEB'),
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME,
    INDEX idx_user (user_id)
);

CREATE TABLE notification_dlq (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id  VARCHAR(36),
    user_id          VARCHAR(36),
    channel          VARCHAR(20),
    failure_reason   VARCHAR(255),
    payload          JSON,
    failed_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## Kafka Topics

| Topic | Partitions | Purpose |
|-------|-----------|---------|
| `raw.events` | 50 | Inbound events from all services |
| `notifications.push` | 30 | Push delivery queue |
| `notifications.email` | 10 | Email delivery queue |
| `notifications.sms` | 10 | SMS delivery queue |
| `notifications.inapp` | 20 | In-app notification queue |
| `notifications.dlq` | 5 | Dead letters after max retries |
| `notification.receipts` | 10 | Delivery tracking → Druid |

---

## Rate Limiting Per User

```java
// Prevent notification spam per user per channel
public boolean isRateLimitExceeded(String userId, NotificationChannel channel) {
    String key = "notif:rate:" + channel + ":" + userId;
    Long count = redis.opsForValue().increment(key);
    if (count == 1) redis.expire(key, Duration.ofHours(1));
    int limit = channel == NotificationChannel.SMS ? 5 : 50; // per hour
    return count > limit;
}
```

---

## Druid — Notification Analytics

```
Dimensions: eventType, channel, status, userSegment
Metrics:    deliveryCount, failureCount, retryCount, openRate (from receipts)
Granularity: per minute for real-time delivery dashboards
```

---

## Non-Functional Requirements

| Concern | Solution |
|---------|----------|
| Delivery guarantee | Kafka at-least-once + dedup Redis key |
| Fan-out for 50M users | Partition Kafka by userId; parallel consumers |
| High priority OTP | Separate Kafka topic with `HIGH` priority consumers |
| Stale device tokens | Catch `InvalidTokenException`, remove from DB |
| Provider outage | Retry with exponential backoff → DLQ → fallback channel |
| Spam prevention | Per-user rate limit in Redis |
| Audit trail | All notifications persisted in MySQL |

---

## Extension Points

- **Template versioning**: Elasticsearch for template search and A/B testing
- **Delivery receipts**: Webhook from FCM/SendGrid → receipt Kafka topic → Druid
- **Scheduling**: Cron-based marketing notifications via Quartz Scheduler
- **ScyllaDB**: Use for in-app notification inbox (high write throughput, per-user partition)
- **Elasticsearch**: Full-text search over notification history for support dashboards