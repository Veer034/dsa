# HLD: Real-Time Chat App like WhatsApp

> **Experience Level:** 10+ Years Java | Spring Boot · Kafka · Redis · MySQL · Elasticsearch · ScyllaDB · Druid

---

## 🔁 Clarifying Questions (You → Interviewer)

| # | Question | Why It Matters |
|---|----------|----------------|
| 1 | 1:1 chat only, or also group chats? What's the max group size? | Fan-out complexity |
| 2 | Do we need real-time delivery or is slight delay acceptable? | WebSocket vs polling |
| 3 | Should messages be persisted permanently or have a retention window? | Storage sizing |
| 4 | Do we need end-to-end encryption (E2EE)? | Key management complexity |
| 5 | What are the delivery receipts needed — sent, delivered, read? | Message state machine |
| 6 | Do we need online/offline presence and last-seen? | Redis pub/sub or heartbeat |
| 7 | Should we support media (images, video, documents)? | Separate media service + CDN |
| 8 | What scale — DAU, messages/sec? | Sharding and infra decisions |
| 9 | Do we need message search? | Elasticsearch integration |

---

## 🔁 Expected Follow-up Questions (Interviewer → You)

- "How does a message reach User B if User B is offline?"
- "How do you prevent duplicate message delivery?"
- "How would you implement group message fan-out for a group of 10,000 members?"
- "Why ScyllaDB over MySQL for message storage?"
- "How does WebSocket connection persistence work across load balancer restarts?"
- "Walk me through the complete path of a message from send to read receipt."
- "How do you handle message ordering?"
- "How would you design the unread message count feature?"

---

## Scale Estimation

```
DAU:            500M users
Avg messages:   40 messages/user/day → 20B messages/day
Peak QPS:       ~500K messages/sec
Storage:        ~200 bytes/message × 20B = ~4 TB/day (ScyllaDB)
WebSocket conns: ~50M concurrent connections
```

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Clients                                    │
│              (iOS / Android / Web — WebSocket)                       │
└──────────┬──────────────────────────────────┬────────────────────────┘
           │ WebSocket                        │ REST (auth, media upload)
           ▼                                  ▼
┌────────────────────────┐        ┌────────────────────────┐
│   WebSocket Gateway     │        │   REST API Gateway      │
│   (Spring Boot WS)      │        │   (Spring Boot)         │
│   Stateful per conn     │        │   Stateless             │
└────────────┬───────────┘        └──────────┬─────────────┘
             │                               │
             ▼                               ▼
┌──────────────────────────────────────────────────────┐
│                   Message Service                     │
│  (Sends to Kafka, manages state, deduplication)       │
└──────────────────────┬───────────────────────────────┘
                       │
            ┌──────────▼──────────┐
            │    Kafka Cluster     │
            │  Topic: chat.messages│
            │  Partitioned by      │
            │  conversation_id     │
            └─────┬────────────────┘
                  │
     ┌────────────┼─────────────────────┐
     ▼            ▼                     ▼
┌─────────┐  ┌──────────┐       ┌────────────────┐
│Delivery │  │Persistence│       │ Search Indexer │
│ Service │  │ Service   │       │ (Elasticsearch)│
│(push to │  │(ScyllaDB) │       └────────────────┘
│WS or    │  └──────────┘
│push notif│
└────┬────┘
     │
     ├──→ Online: Route to WebSocket Gateway holding user's connection
     └──→ Offline: Push Notification Service (FCM/APNs)

Redis Pub/Sub: user:{userId}:inbox  (for routing between WS gateways)
Redis Hash:    user:{userId}:presence {status, lastSeen, gatewayId}
```

---

## Message Flow — Step by Step

```
1. User A sends message via WebSocket to WS Gateway A
2. WS Gateway A publishes to Kafka topic: chat.messages (partitioned by conversationId)
3. Kafka Delivery Consumer reads message:
   a. Checks Redis: is User B online?
      → YES: Redis Pub/Sub on channel user:{B}:inbox → WS Gateway B pushes to User B
      → NO:  Push Notification Service → FCM/APNs
4. Kafka Persistence Consumer writes to ScyllaDB
5. User B receives → sends DELIVERED receipt back via WS
6. User B opens chat → sends READ receipt
7. Receipts flow back to User A via same pipeline
```

---

## WebSocket Gateway (Spring Boot)

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
}
```

```java
@Controller
public class ChatController {

    @Autowired private MessageService     messageService;
    @Autowired private PresenceService    presenceService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessage message,
                            Principal principal) {
        message.setSenderId(principal.getName());
        message.setMessageId(UUID.randomUUID().toString());
        message.setTimestamp(Instant.now());
        messageService.process(message);
    }

    @MessageMapping("/chat.typing")
    public void typingIndicator(@Payload TypingEvent event, Principal principal) {
        // Broadcast to conversation participants via Redis Pub/Sub
        presenceService.broadcastTyping(event, principal.getName());
    }
}
```

---

## Message Service

```java
@Service
public class MessageService {

    @Autowired private KafkaTemplate<String, ChatMessage> kafka;
    @Autowired private StringRedisTemplate redis;

    private static final String DEDUP_KEY = "msg:dedup:";

    public void process(ChatMessage message) {
        // Idempotency check: prevent duplicate delivery (client retry)
        String dedupKey = DEDUP_KEY + message.getMessageId();
        Boolean isNew = redis.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofMinutes(10));
        if (Boolean.FALSE.equals(isNew)) return; // duplicate, discard

        // Set initial status
        message.setStatus(MessageStatus.SENT);

        // Publish to Kafka — partitioned by conversationId for ordering
        kafka.send("chat.messages", message.getConversationId(), message);
    }
}
```

---

## Delivery Service (Kafka Consumer)

```java
@Service
public class DeliveryConsumer {

    @Autowired private SimpMessagingTemplate ws;
    @Autowired private PushNotificationService push;
    @Autowired private PresenceService presence;

    @KafkaListener(topics = "chat.messages", groupId = "delivery-group",
                   concurrency = "10")
    public void onMessage(ChatMessage message) {
        List<String> recipients = getRecipients(message); // for group: all member IDs

        for (String userId : recipients) {
            PresenceInfo info = presence.getPresence(userId);
            if (info.isOnline()) {
                // Route via Redis Pub/Sub to correct WS Gateway
                ws.convertAndSendToUser(userId, "/queue/messages", message);
            } else {
                push.sendPushNotification(userId, message);
            }
        }
    }

    private List<String> getRecipients(ChatMessage msg) {
        if (msg.isGroupMessage()) {
            return groupService.getMembers(msg.getConversationId());
        }
        return List.of(msg.getRecipientId());
    }
}
```

---

## Presence Service (Redis)

```java
@Service
public class PresenceService {

    @Autowired private StringRedisTemplate redis;

    private static final String PRESENCE_KEY = "presence:";
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(30);

    public void markOnline(String userId, String gatewayId) {
        Map<String, String> data = Map.of(
            "status", "ONLINE",
            "gatewayId", gatewayId,
            "lastSeen", Instant.now().toString()
        );
        redis.opsForHash().putAll(PRESENCE_KEY + userId, data);
        redis.expire(PRESENCE_KEY + userId, HEARTBEAT_TTL);
    }

    public void heartbeat(String userId) {
        redis.expire(PRESENCE_KEY + userId, HEARTBEAT_TTL); // reset TTL
    }

    public void markOffline(String userId) {
        redis.opsForHash().put(PRESENCE_KEY + userId, "status", "OFFLINE");
        redis.opsForHash().put(PRESENCE_KEY + userId, "lastSeen", Instant.now().toString());
    }

    public PresenceInfo getPresence(String userId) {
        Map<Object, Object> data = redis.opsForHash().entries(PRESENCE_KEY + userId);
        return PresenceInfo.from(data);
    }

    public void broadcastTyping(TypingEvent event, String senderId) {
        redis.convertAndSend("typing:" + event.getConversationId(),
                             senderId + ":typing");
    }
}
```

---

## ScyllaDB — Message Storage

```cql
-- Partitioned by conversation_id, ordered by bucket (time-based) + message_id
CREATE TABLE chat.messages (
    conversation_id  UUID,
    bucket           INT,           -- YYYYMM for time bucketing
    message_id       TIMEUUID,      -- naturally time-ordered
    sender_id        TEXT,
    message_text     TEXT,
    media_url        TEXT,
    status           TEXT,          -- SENT, DELIVERED, READ
    created_at       TIMESTAMP,
    PRIMARY KEY ((conversation_id, bucket), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
  AND default_time_to_live = 31536000;  -- 1 year

-- Unread counts
CREATE TABLE chat.unread_counts (
    user_id          TEXT,
    conversation_id  UUID,
    unread_count     COUNTER,
    PRIMARY KEY (user_id, conversation_id)
);
```

> **Why ScyllaDB?** Sub-millisecond reads, handles 1M+ writes/sec, linear horizontal scaling, built-in TTL, ideal for time-series message patterns.

---

## Message Search (Elasticsearch)

```java
@Service
public class MessageSearchIndexer {

    @Autowired private ElasticsearchOperations esOps;

    @KafkaListener(topics = "chat.messages", groupId = "search-indexer")
    public void index(ChatMessage message) {
        MessageDocument doc = new MessageDocument(
            message.getMessageId(),
            message.getConversationId(),
            message.getSenderId(),
            message.getMessageText(),
            message.getTimestamp()
        );
        esOps.save(doc);
    }
}

// Elasticsearch index mapping
@Document(indexName = "chat_messages")
public class MessageDocument {
    @Id           private String messageId;
    @Field        private String conversationId;
    @Field        private String senderId;
    @Field(type = FieldType.Text, analyzer = "standard")
    private String messageText;
    @Field(type = FieldType.Date)
    private Instant timestamp;
}
```

---

## MySQL Schema (Users, Conversations)

```sql
CREATE TABLE users (
    user_id      VARCHAR(36) PRIMARY KEY,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    display_name VARCHAR(100),
    avatar_url   VARCHAR(255),
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conversations (
    conversation_id VARCHAR(36) PRIMARY KEY,
    type            ENUM('DIRECT','GROUP') NOT NULL,
    name            VARCHAR(100),               -- for groups
    created_by      VARCHAR(36),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conversation_members (
    conversation_id VARCHAR(36),
    user_id         VARCHAR(36),
    joined_at       DATETIME,
    role            ENUM('MEMBER','ADMIN') DEFAULT 'MEMBER',
    PRIMARY KEY (conversation_id, user_id),
    INDEX idx_user (user_id)
);
```

---

## Kafka Topics

| Topic | Partitioned By | Consumers |
|-------|---------------|-----------|
| `chat.messages` | `conversationId` | Delivery Service, Persistence Service, Search Indexer |
| `chat.receipts` | `userId` | Receipt Processor |
| `chat.presence` | `userId` | Presence Aggregator |
| `push.notifications` | `userId` | Push Notification Worker |

---

## Group Chat Fan-Out Strategy

```
Small groups (< 1000 members):
  → Fan-out on write: Delivery Service sends to each member individually

Large groups (> 1000 members):
  → Fan-out on read: Store single message; each client polls on open
  → Hybrid: write to Kafka; consumers do batch delivery
```

---

## Read Receipts & Message Status

```
SENT      → Message stored in Kafka / DB
DELIVERED → User device received it (WS ACK)
READ      → User opened conversation (client sends read event)
```

```java
public enum MessageStatus { SENT, DELIVERED, READ }

// Receipt update — atomic in ScyllaDB
session.execute(
    "UPDATE chat.messages SET status = ? WHERE conversation_id = ? AND bucket = ? AND message_id = ?",
    MessageStatus.READ.name(), conversationId, bucket, messageId
);
```

---

## Non-Functional Requirements

| Concern | Solution |
|---------|----------|
| Message ordering | Kafka partition per `conversationId` + TIMEUUID in ScyllaDB |
| Duplicate delivery | Redis `SETNX` dedup key with 10-min TTL |
| WS connection sticky routing | Redis Pub/Sub to route across WS gateways |
| Media uploads | S3/GCS pre-signed URL; CDN delivery |
| Offline messages | Stored in ScyllaDB; pulled on reconnect |
| E2EE | Signal Protocol; keys never on server |
| Horizontal WS scale | Stateless after Redis pub/sub routing |

---

## Extension Points

- **Voice/Video**: WebRTC signaling server + TURN/STUN
- **Reactions**: Store in separate ScyllaDB table
- **Disappearing messages**: TTL on ScyllaDB row
- **Druid analytics**: Message volume per hour, DAU by region from Kafka stream