
## WEBSOCKET
###  Core Concepts
* [x] **What is WebSocket and how does it differ from HTTP?**
    * **WebSocket** is a protocol that provides **full-duplex, persistent communication** between a client and a
      server over a **single TCP connection**. It is designed for **real-time, bidirectional data exchange**.

![Image](https://media.geeksforgeeks.org/wp-content/uploads/20250705152348042640/Request-and-Response-Cycle.webp)

![Image](https://substackcdn.com/image/fetch/%24s_%21-4x2%21%2Cf_auto%2Cq_auto%3Agood%2Cfl_progressive%3Asteep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fd58bb11f-3727-4ed2-bcbd-dbffe55069c7_1076x1086.png)



* Key Differences

| Aspect            | HTTP                              | WebSocket                        |
  | ----------------- | --------------------------------- | -------------------------------- |
| Connection        | Stateless, short-lived            | Stateful, long-lived             |
| Communication     | Client → Server only              | Client ↔ Server (bi-directional) |
| Overhead          | High (headers on every request)   | Low (minimal framing)            |
| Real-time support | Poor (needs polling/long polling) | Native real-time                 |
| Use cases         | REST APIs, web pages              | Chat, live updates, trading apps |

---



* **HTTP**:
  Client repeatedly calls `/status` every 2 seconds to check updates (polling).

* **WebSocket**:
  Client connects once, server **pushes updates instantly** when data changes.
* **Stateless** means the server does not store any client-specific session data between requests.
  Each request is independent and must contain all required information to be processed.
---
* [x] **What is full-duplex communication?**
    * Full-duplex communication means both sides can send and receive data at the same time, simultaneously.
###    Implementation
* [x] **What is STOMP protocol?**
    * STOMP is a messaging protocol that runs over WebSocket to provide structured, pub-sub communication.
* [x] **How to handle connection failures and reconnection?**
    * WebSocket connections can break due to **network issues, browser close, or server restart**.
    * The **client detects disconnection** using `onclose / onerror` and **automatically reconnects** after a delay.
    * A **heartbeat (ping–pong)** is used to detect dead connections early.
    * On reconnect, the client **re-authenticates** (token/userId) and the server **restores the session**.

* [x] **How to broadcast messages to multiple clients?**
    * In production, WebSocket messages are not broadcast blindly. Systems use room-based routing, partitioned topics, or ownership models so only relevant pods receive messages.
###    Scaling & Performance
* [x] **How to scale WebSocket connections horizontally?**
    * **Solution**: Sticky Sessions + Redis Pub/Sub

  ```
  Client → Load Balancer (sticky sessions) → WS Server Instance 1
                                          → WS Server Instance 2
                                          → WS Server Instance 3
                                               ↕ Redis Pub/Sub ↕
  ```

    * Key Components:
        1. **Sticky Sessions**: Load balancer routes each user to same server using IP hash or session cookies
        2. **Redis Pub/Sub**: Synchronizes messages across all server instances
        3. **Connection Registry**: Store `userId → serverId` mapping in Redis
        4. **Message Flow**:
            - User A (Server 1) sends message to User B (Server 2)
            - Server 1 publishes to Redis channel
            - Server 2 receives from Redis, forwards to User B

    * **Reconnection Handling**
        1. **Client**: Exponential backoff (1s, 2s, 4s, 8s), request missed messages on reconnect
        2. **Server**: Store undelivered messages in Redis with TTL, send on reconnect using sequence numbers

    * **Tech Stack**
        - **Load Balancer**: Nginx/ALB with sticky sessions
        - **Framework**: Socket.IO (built-in Redis adapter) or ws library
        - **Broker**: Redis Pub/Sub
        - **Storage**: PostgreSQL/MongoDB for persistence

    * **Scaling**
        - Add more WebSocket server instances
        - Use Redis Cluster for high availability
        - Auto-scale based on connection count

    * **Trade-off**: Slight latency increase for cross-server messages, but enables unlimited horizontal scaling.
---