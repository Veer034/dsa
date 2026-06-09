# Design WhatsApp / Chat System
> Tests: WebSocket management, message delivery guarantees, offline handling, group chat, end-to-end encryption awareness, presence.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| 1-1 chat only, or group chat too? | Group chat multiplies fan-out complexity |
| Max group size? | 10 vs 256 vs 1000 members changes design |
| Message delivery guarantees? | At-least-once vs exactly-once, ordering |
| Media support (images, video)? | Separate media pipeline needed |
| Do we need read receipts (✓✓)? | State machine for delivery/read status |
| Online presence (last seen)? | Separate presence service |
| E2E encryption? | Key exchange protocol (Signal Protocol) — mention awareness |
| How many concurrent connections? | Drives WebSocket server sizing |

**Typical answer:** 1-1 + group (max 256 members), 500M DAU, 100B messages/day, media yes, read receipts yes, presence yes.

---

## Scale Estimation

```
Messages:   100B/day → ~1.15M messages/sec
Active connections: 500M DAU, assume 50% online at peak → 250M concurrent WebSocket connections
Servers needed: 1 WS server handles ~50K connections → need ~5000 WS servers
Storage: 100B * 200 bytes avg = 20 TB/day text (+ media in object store)
```

---

## HLD Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                  Client A           Client B                │
│               (Mobile/Web)       (Mobile/Web)               │
└────────┬──────────────────────────────────┬─────────────────┘
         │ WebSocket                        │ WebSocket
┌────────▼──────────────────────────────────▼─────────────────┐
│              WebSocket Gateway Layer                         │
│         [NGINX / Custom WS Server (Netty)]                   │
│         Consistent Hashing → User → WS Node                 │
└────────┬──────────────────────────────────┬─────────────────┘
         │                                  │
┌────────▼──────────┐             ┌─────────▼──────────┐
│  Chat Service     │             │  Presence Service  │
│  [Java/SBoot]     │             │  [Java/SBoot]      │
└────────┬──────────┘             └─────────┬──────────┘
         │                                  │
         │                         {Redis Pub/Sub}
         │                         last_seen:{userId}
┌────────▼──────────┐
│  <Kafka Topics>   │
│  msg.send         │
│  msg.delivered    │
│  msg.read         │
└────────┬──────────┘
         │
┌────────▼────────────────────────────────────────────────┐
│               Message Processor (Consumer Group)         │
│  → Route to recipient WS node                           │
│  → Store to DB if offline                               │
│  → Handle group fan-out                                 │
└────────┬─────────────────────────────────┬──────────────┘
         │                                 │
┌────────▼────────────┐          ┌─────────▼──────────────┐
│   ((ScyllaDB))      │          │  ((MySQL))             │
│   messages table    │          │  users, groups,        │
│   (chat_id, msg_id) │          │  group_members         │
└─────────────────────┘          └────────────────────────┘
         │
┌────────▼────────────┐
│  S3 / Azure Blob    │  ← Media storage
│  + CDN for delivery │
└─────────────────────┘
```

---

## Key Design Decisions

### 1. WebSocket Connection Management

```
Each user maintains a persistent WebSocket connection to a WS Gateway node.
The problem: if User A and User B are on different WS nodes, how does A's message reach B?

Solution: Pub/Sub via Redis
  → On connect: node subscribes to channel user:{userId} in Redis Pub/Sub
  → On message: Chat Service publishes to Redis channel user:{recipientId}
  → The WS node that has recipient's connection receives it and pushes to client

Routing table: {userId → wsNodeId} stored in Redis
  → On connect: SET ws_node:{userId} = "node-42"  EX 300
  → Heartbeat refreshes TTL every 60 seconds
  → On disconnect: DEL ws_node:{userId}
```

### 2. Message Delivery States (The ✓✓ Problem)

```
State machine:
  SENT      → message stored in DB, delivered to Kafka
  DELIVERED → recipient's device ACK'd receipt (device online, WS received)
  READ      → recipient explicitly viewed the message

Implementation:
  message_status table:
    message_id | recipient_id | status | updated_at

  Flow:
    Sender sends → SENT ✓
    Recipient's WS node delivers → device sends ACK → DELIVERED ✓✓
    Recipient opens chat → sends READ event → READ ✓✓ (blue)

  Offline case:
    Message stored in DB with status SENT
    On reconnect → device fetches pending messages
    → sends DELIVERED bulk ACK
```

### 3. Message Storage — ScyllaDB

```
Why ScyllaDB?
→ Message writes are append-only (perfect for LSM-tree in ScyllaDB)
→ Read pattern: "give me last N messages in chat X" → partition by chat_id
→ Need millions of writes/sec globally

Schema:
  messages (
      chat_id    UUID   PARTITION KEY,   ← 1-1: sorted pair of user IDs, group: group_id
      message_id TIMEUUID CLUSTERING KEY DESC,
      sender_id  UUID,
      content    TEXT,
      type       TEXT,   ← 'text', 'image', 'video', 'audio'
      media_url  TEXT,
      status     TEXT,
      created_at TIMESTAMP
  ) WITH default_time_to_live = 31536000  -- 1 year, archive older

Read: SELECT * FROM messages WHERE chat_id = ? ORDER BY message_id DESC LIMIT 50
```

### 4. Group Chat Fan-out

```
Group with 256 members, 1 message:
  → Store ONE message in messages table (chat_id = group_id)
  → Fetch group member list from MySQL (cached in Redis Set: group_members:{groupId})
  → For each online member: publish to Redis channel user:{memberId}
  → For each offline member: message is in DB, fetched on reconnect

Why not fan-out the message to each user's inbox?
  → At 256 members and millions of groups, storage explodes
  → Single copy + member list is efficient

Group member list cache:
  Redis Set: group_members:{groupId} → Set of userId
  Invalidated on add/remove member event
```

### 5. Offline Message Sync

```
On client reconnect:
  1. Client sends: { lastSeenMessageId: <id>, chatIds: [...active chats] }
  2. Server queries ScyllaDB: 
     SELECT * FROM messages WHERE chat_id = ? AND message_id > lastSeenId
  3. Return missed messages in bulk
  4. Client sends bulk DELIVERED ACK

Push Notifications (FCM / APNs):
  → When recipient is offline (no WS connection)
  → Notification Service subscribes to msg.send Kafka topic
  → Checks if recipient online (Redis ws_node:{userId} exists)
  → If offline: send FCM/APNs push with message preview
```

---

## LLD — Send Message Flow

```
Client A sends: { to: userB, content: "Hey!", type: "text" }

WebSocket Gateway:
  1. Receive frame, parse message
  2. Validate auth (JWT from connection handshake)
  3. Generate message_id (Snowflake/TIMEUUID)
  4. Publish to Kafka: msg.send topic

Message Processor (Kafka Consumer):
  5. Store to ScyllaDB: INSERT INTO messages (...)
  6. Update message status: SENT
  7. Lookup: is recipient online? Check Redis: GET ws_node:{userB}
     
     If ONLINE:
       → Publish to Redis channel: user:{userB}
       → Recipient's WS node pushes to Client B
       → Client B sends ACK → status = DELIVERED

     If OFFLINE:
       → Send FCM/APNs push notification
       → Message stays in ScyllaDB, fetched on reconnect

  8. Publish ACK back to Client A: { messageId, status: SENT }
```

---

## Presence Service

```
Online detection:
  → WebSocket connect → SET presence:{userId} = "online" EX 60
  → Heartbeat ping every 30s → EXPIRE refresh
  → WebSocket disconnect → DEL presence:{userId}

Last seen:
  → On disconnect → SET last_seen:{userId} = timestamp
  → Stored in Redis (TTL 30 days) + async flush to MySQL

Privacy:
  → "Last seen" visibility settings in MySQL user_settings
  → Presence service checks before returning to requester

Scale problem:
  → 500M users: storing presence in single Redis = fine
  → Pub/Sub for presence changes: user subscribes to presence updates of their contacts
  → Batched: when you open a chat, fetch presence of top 20 contacts at once
```

---

## Media Handling

```
Upload flow (separate from message send):
  1. Client requests upload URL: POST /media/upload-url
  2. Server returns pre-signed S3/Azure Blob URL
  3. Client uploads directly to object store (bypasses our servers)
  4. Client sends message with { type: "image", mediaUrl: "cdn.app.com/img/abc.jpg" }

CDN:
  → Media served via CloudFront / Azure CDN
  → Thumbnails generated by Lambda/Azure Function on upload event
  → Original stored in S3, thumbnail at s3/thumbnails/

Virus scan:
  → Async: ClamAV or cloud-native scan after upload
  → If malicious → delete from S3, push notification to sender
```

---

## Code Skeleton — WebSocket Handler

```java
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session); // from JWT in header
        sessionRegistry.register(userId, session);
        redisTemplate.opsForValue().set("ws_node:" + userId, nodeId, Duration.ofMinutes(5));
        presenceService.setOnline(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ChatMessage msg = objectMapper.readValue(message.getPayload(), ChatMessage.class);
        msg.setMessageId(snowflake.nextId());
        msg.setSenderId(extractUserId(session));
        kafkaTemplate.send("msg.send", msg.getChatId(), msg);  // async
        // ACK back to sender immediately
        session.sendMessage(new TextMessage(ack(msg.getMessageId(), "SENT")));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractUserId(session);
        sessionRegistry.deregister(userId);
        redisTemplate.delete("ws_node:" + userId);
        presenceService.setOffline(userId);
    }
}
```

---

## Interview Tips

- The **WebSocket routing problem** (A and B on different nodes) and the **Redis Pub/Sub** solution is the key insight — bring it up yourself.
- **ScyllaDB over Cassandra** — same CQL, but ScyllaDB has lower latency (C++ vs JVM, no GC pauses). Mention this.
- Mention **Signal Protocol for E2E encryption** — you don't need to implement it, just say "keys exchanged on registration, server only sees encrypted blobs."
- **Exactly-once delivery is hard** — acknowledge it. At-least-once with idempotency on client (dedupe by message_id) is the pragmatic choice.
- Mention **backpressure on Kafka consumers** — if group fan-out is slow, consumers should be partitioned by chat_id for parallelism.
