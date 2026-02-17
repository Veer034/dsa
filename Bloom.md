# Bloom Filters in Distributed Systems
## Complete Guide for Multi-Pod Service Architectures

---

## 1. What is a Bloom Filter?

**Bloom Filter** is a space-efficient probabilistic data structure that tests whether an element is a member of a set.

### Key Properties:
- **Space Efficient**: Uses bits instead of storing actual data (90-95% less memory than HashSet)
- **Fast**: O(k) lookup where k = number of hash functions (typically 3-7)
- **Probabilistic**:
    - ✅ **Never produces false negatives** ("definitely NOT in set")
    - ⚠️ **May produce false positives** ("might be in set")
- **Immutable**: Cannot delete items once added

### Basic Example:
```
Question: "Has user123 been rate-limited in the last hour?"

Without Bloom Filter:
- Store all rate-limited users in Redis/Database
- Memory: 1M users × 16 bytes = 16 MB

With Bloom Filter:
- Store only probabilistic bits
- Memory: 1M users × 10 bits = 1.25 MB (87% savings!)
- Trade-off: 1% false positive (1 in 100 users wrongly flagged)
```

---

## 2. How Bloom Filters Work

### Internal Structure:

```
┌─────────────────────────────────────────────────────────────┐
│           Bloom Filter (Bit Array: 16 bits)                 │
│  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐          │
│  │0 │0 │0 │0 │0 │0 │0 │0 │0 │0 │0 │0 │0 │0 │0 │0 │  Empty   │
│  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘          │
│   0  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15           │
└─────────────────────────────────────────────────────────────┘

Step 1: ADD "user@example.com"
─────────────────────────────────
Hash1("user@example.com") % 16 = 3
Hash2("user@example.com") % 16 = 9
Hash3("user@example.com") % 16 = 14

┌─────────────────────────────────────────────────────────────┐
│  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐          │
│  │0 │0 │0 │1 │0 │0 │0 │0 │0 │1 │0 │0 │0 │0 │1 │0 │          │
│  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘          │
│            ↑                 ↑              ↑               │
│           bit3              bit9          bit14             │
└─────────────────────────────────────────────────────────────┘

Step 2: CHECK "user@example.com"
─────────────────────────────────
Check bits 3, 9, 14 → ALL are 1 → "MIGHT EXIST" ✓

Step 3: CHECK "other@example.com"
─────────────────────────────────
Hash1("other@example.com") % 16 = 5
Hash2("other@example.com") % 16 = 9
Hash3("other@example.com") % 16 = 12

Check bits 5, 9, 12:
- bit5 = 0 ← AT LEAST ONE is 0
→ "DEFINITELY DOES NOT EXIST" ✓✓

Step 4: ADD "admin@example.com"
─────────────────────────────────
Hash1("admin@example.com") % 16 = 3  (already 1)
Hash2("admin@example.com") % 16 = 7
Hash3("admin@example.com") % 16 = 14 (already 1)

┌─────────────────────────────────────────────────────────────┐
│  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐          │
│  │0 │0 │0 │1 │0 │0 │0 │1 │0 │1 │0 │0 │0 │0 │1 │0 │          │
│  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘          │
│            ↑           ↑     ↑              ↑               │
│           (shared)    new  (shared)      (shared)           │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Bloom Filters in Distributed Systems

### Challenge: Multiple Service Pods

```
┌─────────────────────────────────────────────────────────────┐
│                   Load Balancer                             │
└───────────────┬──────────────┬──────────────┬───────────────┘
                │              │              │
        ┌───────▼──────┐ ┌─────▼──────┐ ┌────▼───────┐
        │   Pod 1      │ │   Pod 2    │ │   Pod 3    │
        │              │ │            │ │            │
        │ Bloom Filter │ │Bloom Filter│ │Bloom Filter│
        │  (Local)     │ │  (Local)   │ │  (Local)   │
        └──────────────┘ └────────────┘ └────────────┘

PROBLEM: Each pod has its own Bloom Filter!
- User request to Pod 1: "user123" → NOT FOUND
- User request to Pod 2: "user123" → FOUND
- Inconsistent results across pods ❌
```

### Solution Architectures:

---

## 4. Architecture Pattern 1: Centralized Bloom Filter (Redis/Memcached)

**Best For**: Small to medium datasets, shared state needed across all pods

```
┌────────────────────────────────────────────────────────────┐
│                   Load Balancer                            │
└───────────────┬──────────────┬──────────────┬──────────────┘
                │              │              │
        ┌───────▼──────┐ ┌─────▼──────┐ ┌────▼───────┐
        │   Pod 1      │ │   Pod 2    │ │   Pod 3    │
        │              │ │            │ │            │
        │   (No BF)    │ │  (No BF)   │ │  (No BF)   │
        └───────┬──────┘ └─────┬──────┘ └────┬───────┘
                │              │             │
                └──────────────┼─────────────┘
                               │
                    ┌──────────▼───────────┐
                    │   Redis/Memcached    │
                    │                      │
                    │   Bloom Filter Bits  │
                    │   (Centralized)      │
                    └──────────────────────┘
```

### Implementation (Redis):

**Maven Dependencies**:
```xml
<dependencies>
    <!-- Spring Boot Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- Lettuce for Redis Bloom commands -->
    <dependency>
        <groupId>io.lettuce</groupId>
        <artifactId>lettuce-core</artifactId>
    </dependency>
    
    <!-- RedisBloom Java client -->
    <dependency>
        <groupId>com.redislabs</groupId>
        <artifactId>jrebloom</artifactId>
        <version>2.1.0</version>
    </dependency>
</dependencies>
```

**Configuration Class**:
```java
package com.example.bloomfilter.config;

import io.rebloom.client.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisBloomConfig {
    
    @Value("${redis.host:localhost}")
    private String redisHost;
    
    @Value("${redis.port:6379}")
    private int redisPort;
    
    @Bean
    public Client redisBloomClient() {
        return new Client(redisHost, redisPort);
    }
}
```

**Service Implementation**:
```java
package com.example.bloomfilter.service;

import io.rebloom.client.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisBloomFilterService {
    
    private final Client redisBloomClient;
    private static final String RATE_LIMIT_KEY = "rate_limit:users";
    private static final double ERROR_RATE = 0.01; // 1% false positive
    private static final long CAPACITY = 1_000_000L;
    
    /**
     * Initialize Bloom filter (call once on startup)
     */
    public void initializeBloomFilter() {
        try {
            redisBloomClient.createFilter(RATE_LIMIT_KEY, CAPACITY, ERROR_RATE);
            log.info("Bloom filter initialized: {}", RATE_LIMIT_KEY);
        } catch (Exception e) {
            log.warn("Bloom filter already exists or initialization failed: {}", e.getMessage());
        }
    }
    
    /**
     * Add user to rate limit
     */
    public void addUser(String email) {
        try {
            redisBloomClient.add(RATE_LIMIT_KEY, email);
            log.debug("Added user to Bloom filter: {}", email);
        } catch (Exception e) {
            log.error("Error adding user to Bloom filter", e);
        }
    }
    
    /**
     * Check if user is rate limited
     * @return true if rate limited (or false positive), false if definitely not
     */
    public boolean isRateLimited(String email) {
        try {
            boolean exists = redisBloomClient.exists(RATE_LIMIT_KEY, email);
            if (exists) {
                log.debug("User is rate limited (or false positive): {}", email);
            }
            return exists;
        } catch (Exception e) {
            log.error("Error checking Bloom filter", e);
            return false; // Fail open
        }
    }
    
    /**
     * Batch add multiple users
     */
    public void addMultipleUsers(String... emails) {
        try {
            redisBloomClient.addMulti(RATE_LIMIT_KEY, emails);
            log.debug("Added {} users to Bloom filter", emails.length);
        } catch (Exception e) {
            log.error("Error batch adding users to Bloom filter", e);
        }
    }
    
    /**
     * Get Bloom filter statistics
     */
    public void logBloomFilterInfo() {
        try {
            // Note: Info command may not be available in all RedisBloom versions
            log.info("Bloom filter key: {}, capacity: {}, error rate: {}", 
                     RATE_LIMIT_KEY, CAPACITY, ERROR_RATE);
        } catch (Exception e) {
            log.error("Error getting Bloom filter info", e);
        }
    }
}
```

**REST Controller**:
```java
package com.example.bloomfilter.controller;

import com.example.bloomfilter.service.RedisBloomFilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ratelimit")
@RequiredArgsConstructor
public class RateLimitController {
    
    private final RedisBloomFilterService bloomFilterService;
    
    @PostMapping("/check")
    public ResponseEntity<String> checkRateLimit(@RequestParam String email) {
        if (bloomFilterService.isRateLimited(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limited (or false positive)");
        }
        
        // Add user to rate limit for next hour
        bloomFilterService.addUser(email);
        return ResponseEntity.ok("Request allowed");
    }
    
    @GetMapping("/status")
    public ResponseEntity<String> getStatus(@RequestParam String email) {
        boolean rateLimited = bloomFilterService.isRateLimited(email);
        return ResponseEntity.ok(rateLimited ? "Rate limited" : "Not rate limited");
    }
}
```

**Application Properties**:
```properties
# application.properties
redis.host=redis.example.com
redis.port=6379
spring.application.name=bloom-filter-service
```

**Redis Commands**:
```bash
# Create Bloom filter
BF.RESERVE rate_limit:users 0.01 1000000

# Add item
BF.ADD rate_limit:users "user123@example.com"

# Check existence
BF.EXISTS rate_limit:users "user123@example.com"

# Multi-add
BF.MADD rate_limit:users "user1" "user2" "user3"

# Get info
BF.INFO rate_limit:users

# Example output:
# Capacity: 1000000
# Size: 1198891 (bits)
# Number of filters: 1
# Number of items inserted: 50000
# Expansion rate: 2
```

**Pros**:
- ✅ Consistent across all pods
- ✅ No synchronization needed
- ✅ Easy to implement

**Cons**:
- ❌ Network latency (Redis call per check)
- ❌ Single point of failure (mitigate with Redis cluster)
- ❌ Extra infrastructure cost

---

## 5. Architecture Pattern 2: Local Bloom Filter + Periodic Sync

**Best For**: Read-heavy workloads, can tolerate eventual consistency

```
┌───────────────────────────────────────────────────────────┐
│                 Message Queue (Kafka/RabbitMQ)            │
│                "bloom_filter_updates" topic               │
└─────┬──────────────────┬──────────────────┬───────────────┘
      │                  │                  │
      ▼                  ▼                  ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Pod 1     │    │   Pod 2     │    │   Pod 3     │
│ ┌─────────┐ │    │ ┌─────────┐ │    │ ┌─────────┐ │
│ │ Bloom   │ │    │ │ Bloom   │ │    │ │ Bloom   │ │
│ │ Filter  │ │    │ │ Filter  │ │    │ │ Filter  │ │
│ │ (Local) │ │    │ │ (Local) │ │    │ │ (Local) │ │
│ └─────────┘ │    │ └─────────┘ │    │ └─────────┘ │
│     ↓       │    │     ↓       │    │     ↓       │
│  Publish    │    │  Subscribe  │    │  Subscribe  │
└─────────────┘    └─────────────┘    └─────────────┘

Flow:
1. Pod 1 adds "user123" to local Bloom filter
2. Pod 1 publishes event: {"action": "add", "item": "user123"}
3. All pods (including Pod 1) receive event
4. All pods update their local Bloom filters
```

### Implementation (Kafka + Spring Boot):

**Maven Dependencies**:
```xml
<dependencies>
    <!-- Spring Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    
    <!-- Google Guava for Bloom Filter -->
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <version>32.1.3-jre</version>
    </dependency>
    
    <!-- Jackson for JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

**Kafka Configuration**:
```java
package com.example.bloomfilter.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {
    
    @Value("${kafka.bootstrap.servers:localhost:9092}")
    private String bootstrapServers;
    
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "bloom-filter-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
```

**Event Models**:
```java
package com.example.bloomfilter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BloomFilterEvent {
    private String action;  // "add" or "remove"
    private String item;
    private long timestamp;
}
```

**Distributed Bloom Filter Service**:
```java
package com.example.bloomfilter.service;

import com.example.bloomfilter.model.BloomFilterEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class DistributedBloomFilterService {
    
    private static final String TOPIC = "bloom-filter-updates";
    private static final int EXPECTED_INSERTIONS = 1_000_000;
    private static final double FALSE_POSITIVE_RATE = 0.01;
    
    private BloomFilter<String> bloomFilter;
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @PostConstruct
    public void init() {
        // Initialize local Bloom filter
        bloomFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            EXPECTED_INSERTIONS,
            FALSE_POSITIVE_RATE
        );
        log.info("Distributed Bloom Filter initialized with capacity: {}, FP rate: {}", 
                 EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);
    }
    
    /**
     * Add item to Bloom filter and broadcast to all pods
     */
    public void add(String item) {
        // Add to local Bloom filter
        bloomFilter.put(item);
        
        // Broadcast to all pods via Kafka
        try {
            BloomFilterEvent event = new BloomFilterEvent("add", item, System.currentTimeMillis());
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, eventJson);
            log.debug("Broadcasted add event for item: {}", item);
        } catch (Exception e) {
            log.error("Error broadcasting Bloom filter event", e);
        }
    }
    
    /**
     * Check if item exists in local Bloom filter
     */
    public boolean mightContain(String item) {
        boolean exists = bloomFilter.mightContain(item);
        if (exists) {
            log.debug("Item might exist (or false positive): {}", item);
        }
        return exists;
    }
    
    /**
     * Kafka listener to sync updates from other pods
     */
    @KafkaListener(topics = TOPIC, groupId = "bloom-filter-group")
    public void handleBloomFilterUpdate(String message) {
        try {
            BloomFilterEvent event = objectMapper.readValue(message, BloomFilterEvent.class);
            
            if ("add".equals(event.getAction())) {
                // Update local Bloom filter
                bloomFilter.put(event.getItem());
                log.debug("Synced add event from another pod: {}", event.getItem());
            }
        } catch (Exception e) {
            log.error("Error processing Bloom filter update", e);
        }
    }
    
    /**
     * Get approximate size (for monitoring)
     */
    public long getApproximateSize() {
        // Guava BloomFilter doesn't expose size directly
        // Return expected insertions as approximation
        return EXPECTED_INSERTIONS;
    }
}
```

**REST Controller with Distributed Bloom Filter**:
```java
package com.example.bloomfilter.controller;

import com.example.bloomfilter.service.DistributedBloomFilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/distributed/ratelimit")
@RequiredArgsConstructor
public class DistributedRateLimitController {
    
    private final DistributedBloomFilterService bloomFilterService;
    
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> checkRateLimit(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        
        if (bloomFilterService.mightContain(email)) {
            response.put("rateLimited", true);
            response.put("message", "Rate limited (or false positive - check authoritative source)");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        
        // Add user to rate limit
        bloomFilterService.add(email);
        response.put("rateLimited", false);
        response.put("message", "Request allowed");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        boolean rateLimited = bloomFilterService.mightContain(email);
        response.put("email", email);
        response.put("rateLimited", rateLimited);
        response.put("approximateSize", bloomFilterService.getApproximateSize());
        return ResponseEntity.ok(response);
    }
}
```

**Application Properties**:
```properties
# application.yml
spring:
  application:
    name: distributed-bloom-filter-service
  kafka:
    bootstrap-servers: kafka:9092
    consumer:
      group-id: bloom-filter-group
      auto-offset-reset: latest
    producer:
      acks: all
      retries: 3

kafka:
  bootstrap:
    servers: kafka:9092

logging:
  level:
    com.example.bloomfilter: DEBUG
```

**Pros**:
- ✅ Fast local checks (no network latency)
- ✅ Scales horizontally
- ✅ No single point of failure

**Cons**:
- ❌ Eventual consistency (small time window where pods disagree)
- ❌ More complex implementation
- ❌ Requires message queue infrastructure

---

## 6. Architecture Pattern 3: Partitioned Bloom Filters

**Best For**: Very large datasets, need to scale beyond single filter

```
Hash-based Partitioning:
┌─────────────────────────────────────────────────────────┐
│  User Request: "user123@example.com"                    │
│  Partition = hash("user123@example.com") % 3 = 1        │
└───────────────────────┬─────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
    ┌───▼────┐     ┌────▼───┐     ┌────▼───┐
    │ Pod 1  │     │ Pod 2  │     │ Pod 3  │
    │        │     │        │     │        │
    │ BF[0]  │     │ BF[1]  │     │ BF[2]  │
    │        │     │◄──────┐│     │        │
    │users:  │     │users: ││     │users:  │
    │A-H     │     │I-P    ││     │Q-Z     │
    └────────┘     └────────┘     └────────┘
                           │
                 Request routed to Pod 2
                 (partition 1 holds this user)
```

### Implementation (Consistent Hashing):

```java
package com.example.bloomfilter.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PartitionedBloomFilterService {
    
    private static final int NUM_PARTITIONS = 10;
    private static final int CAPACITY_PER_PARTITION = 100_000;
    private static final double FALSE_POSITIVE_RATE = 0.01;
    
    private List<BloomFilter<String>> partitions;
    private HashFunction hashFunction;
    
    @PostConstruct
    public void init() {
        partitions = new ArrayList<>(NUM_PARTITIONS);
        hashFunction = Hashing.murmur3_128();
        
        // Initialize each partition
        for (int i = 0; i < NUM_PARTITIONS; i++) {
            BloomFilter<String> partition = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                CAPACITY_PER_PARTITION,
                FALSE_POSITIVE_RATE
            );
            partitions.add(partition);
        }
        
        log.info("Initialized {} partitions, capacity per partition: {}", 
                 NUM_PARTITIONS, CAPACITY_PER_PARTITION);
    }
    
    /**
     * Determine which partition this item belongs to
     */
    private int getPartitionIndex(String item) {
        int hashCode = hashFunction.hashString(item, StandardCharsets.UTF_8).asInt();
        return Math.abs(hashCode % NUM_PARTITIONS);
    }
    
    /**
     * Add item to appropriate partition
     */
    public void add(String item) {
        int partitionIndex = getPartitionIndex(item);
        BloomFilter<String> partition = partitions.get(partitionIndex);
        partition.put(item);
        log.debug("Added item to partition {}: {}", partitionIndex, item);
    }
    
    /**
     * Check if item exists in appropriate partition
     */
    public boolean mightContain(String item) {
        int partitionIndex = getPartitionIndex(item);
        BloomFilter<String> partition = partitions.get(partitionIndex);
        boolean exists = partition.mightContain(item);
        
        if (exists) {
            log.debug("Item might exist in partition {}: {}", partitionIndex, item);
        }
        return exists;
    }
    
    /**
     * Get partition index for a given item (useful for routing)
     */
    public int getPartitionForItem(String item) {
        return getPartitionIndex(item);
    }
}
```

**REST Controller with Partition Info**:
```java
package com.example.bloomfilter.controller;

import com.example.bloomfilter.service.PartitionedBloomFilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/partitioned/ratelimit")
@RequiredArgsConstructor
public class PartitionedRateLimitController {
    
    private final PartitionedBloomFilterService bloomFilterService;
    
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> checkRateLimit(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        int partition = bloomFilterService.getPartitionForItem(email);
        
        response.put("partition", partition);
        
        if (bloomFilterService.mightContain(email)) {
            response.put("rateLimited", true);
            response.put("message", "Rate limited in partition " + partition);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        
        bloomFilterService.add(email);
        response.put("rateLimited", false);
        response.put("message", "Request allowed, added to partition " + partition);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/partition/{email}")
    public ResponseEntity<Map<String, Object>> getPartitionInfo(@PathVariable String email) {
        Map<String, Object> response = new HashMap<>();
        int partition = bloomFilterService.getPartitionForItem(email);
        boolean exists = bloomFilterService.mightContain(email);
        
        response.put("email", email);
        response.put("partition", partition);
        response.put("exists", exists);
        
        return ResponseEntity.ok(response);
    }
}
```

**With Sticky Routing**:
```yaml
# Kubernetes Service with session affinity
apiVersion: v1
kind: Service
metadata:
  name: api-service
spec:
  selector:
    app: api
  ports:
    - port: 80
      targetPort: 8080
  sessionAffinity: ClientIP  # Route same client to same pod
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 3600
```

---

## 7. Real-World Use Cases

### Use Case 1: Rate Limiting

```java
package com.example.bloomfilter.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Service
public class RateLimiterService {
    
    private static final int CAPACITY = 1_000_000;
    private static final double FALSE_POSITIVE_RATE = 0.01;
    
    private BloomFilter<String> currentHour;
    private BloomFilter<String> lastHour;
    private long currentHourTimestamp;
    
    @PostConstruct
    public void init() {
        currentHour = createBloomFilter();
        lastHour = createBloomFilter();
        currentHourTimestamp = Instant.now().getEpochSecond();
        log.info("RateLimiter initialized");
    }
    
    private BloomFilter<String> createBloomFilter() {
        return BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            CAPACITY,
            FALSE_POSITIVE_RATE
        );
    }
    
    /**
     * Check if user is rate limited
     * @param userId User identifier
     * @return true if rate limited (or false positive), false otherwise
     */
    public boolean isRateLimited(String userId) {
        rotateFiltersIfNeeded();
        
        // Check if user already made request in current hour
        if (currentHour.mightContain(userId)) {
            log.warn("User {} is rate limited (or false positive)", userId);
            return true; // Definitely rate limited (or 1% false positive)
        }
        
        // Not rate limited - add to current hour
        currentHour.put(userId);
        log.debug("User {} added to rate limiter", userId);
        return false;
    }
    
    /**
     * Rotate Bloom filters every hour
     */
    private void rotateFiltersIfNeeded() {
        long now = Instant.now().getEpochSecond();
        long hourInSeconds = 3600;
        
        if (now - currentHourTimestamp > hourInSeconds) {
            log.info("Rotating Bloom filters - new hour started");
            lastHour = currentHour;
            currentHour = createBloomFilter();
            currentHourTimestamp = now;
        }
    }
    
    /**
     * Scheduled rotation every hour (backup to manual rotation)
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    public void scheduledRotation() {
        log.info("Scheduled Bloom filter rotation triggered");
        rotateFiltersIfNeeded();
    }
}
```

**Controller for Rate Limiting**:
```java
package com.example.bloomfilter.controller;

import com.example.bloomfilter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    
    private final RateLimiterService rateLimiterService;
    
    @GetMapping("/resource")
    public ResponseEntity<Map<String, Object>> getResource(@RequestParam String userId) {
        Map<String, Object> response = new HashMap<>();
        
        if (rateLimiterService.isRateLimited(userId)) {
            response.put("error", "Too many requests");
            response.put("message", "Rate limit exceeded. Try again later.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        
        // Process request
        response.put("data", "Your resource data");
        response.put("userId", userId);
        return ResponseEntity.ok(response);
    }
}
```

**Enable Scheduling in Main Application**:
```java
package com.example.bloomfilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BloomFilterApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BloomFilterApplication.class, args);
    }
}
```

### Use Case 2: Deduplication in Event Processing

```java
package com.example.bloomfilter.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Service
public class EventDeduplicatorService {
    
    private static final int CAPACITY = 10_000_000;
    private static final double FALSE_POSITIVE_RATE = 0.001; // 0.1% for critical dedup
    private static final long WINDOW_MINUTES = 60;
    
    private BloomFilter<String> bloomFilter;
    private long windowStartTimestamp;
    
    @PostConstruct
    public void init() {
        bloomFilter = createBloomFilter();
        windowStartTimestamp = Instant.now().getEpochSecond();
        log.info("EventDeduplicator initialized with {}min window", WINDOW_MINUTES);
    }
    
    private BloomFilter<String> createBloomFilter() {
        return BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            CAPACITY,
            FALSE_POSITIVE_RATE
        );
    }
    
    /**
     * Check if event is duplicate
     * @param eventId Unique event identifier
     * @return true if duplicate (or false positive), false if new event
     */
    public boolean isDuplicate(String eventId) {
        rotateFilterIfNeeded();
        
        if (bloomFilter.mightContain(eventId)) {
            log.warn("Duplicate event detected (or false positive): {}", eventId);
            return true; // Likely duplicate
        }
        
        // New event - add to filter
        bloomFilter.put(eventId);
        log.debug("New event processed: {}", eventId);
        return false;
    }
    
    /**
     * Rotate filter based on time window
     */
    private void rotateFilterIfNeeded() {
        long now = Instant.now().getEpochSecond();
        long windowSeconds = WINDOW_MINUTES * 60;
        
        if (now - windowStartTimestamp > windowSeconds) {
            log.info("Rotating Bloom filter - new time window started");
            bloomFilter = createBloomFilter();
            windowStartTimestamp = now;
        }
    }
    
    /**
     * Scheduled rotation
     */
    @Scheduled(fixedRate = 60000) // Check every minute
    public void scheduledCheck() {
        rotateFilterIfNeeded();
    }
}
```

**Kafka Consumer with Deduplication**:
```java
package com.example.bloomfilter.consumer;

import com.example.bloomfilter.model.Event;
import com.example.bloomfilter.service.EventDeduplicatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {
    
    private final EventDeduplicatorService deduplicator;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(topics = "events", groupId = "event-processor")
    public void consumeEvent(String message) {
        try {
            Event event = objectMapper.readValue(message, Event.class);
            String eventId = event.getId();
            
            // Check for duplicate
            if (deduplicator.isDuplicate(eventId)) {
                log.info("Skipping duplicate event: {}", eventId);
                return;
            }
            
            // Process new event
            processEvent(event);
            
        } catch (Exception e) {
            log.error("Error processing event", e);
        }
    }
    
    private void processEvent(Event event) {
        log.info("Processing event: {}", event.getId());
        // Your business logic here
    }
}
```

**Event Model**:
```java
package com.example.bloomfilter.model;

import lombok.Data;

@Data
public class Event {
    private String id;
    private String type;
    private String payload;
    private long timestamp;
}
```

### Use Case 3: Cache Optimization

```java
package com.example.bloomfilter.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SmartCacheService {
    
    private static final int BLOOM_CAPACITY = 10_000_000;
    private static final double FALSE_POSITIVE_RATE = 0.01;
    private static final int CACHE_MAX_SIZE = 100_000;
    private static final int CACHE_EXPIRE_MINUTES = 60;
    
    private BloomFilter<String> bloomFilter;
    private Cache<String, Object> cache;
    
    @PostConstruct
    public void init() {
        // Initialize Bloom filter
        bloomFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            BLOOM_CAPACITY,
            FALSE_POSITIVE_RATE
        );
        
        // Initialize Guava cache with eviction
        cache = CacheBuilder.newBuilder()
            .maximumSize(CACHE_MAX_SIZE)
            .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .recordStats()
            .build();
        
        log.info("SmartCache initialized with Bloom filter");
    }
    
    /**
     * Get value from cache with Bloom filter optimization
     */
    public Object get(String key) {
        // Fast negative check with Bloom filter
        if (!bloomFilter.mightContain(key)) {
            log.debug("Key definitely not in cache (Bloom filter): {}", key);
            return null; // Definitely not in cache
        }
        
        // Might be in cache (or 1% false positive)
        Object value = cache.getIfPresent(key);
        if (value == null) {
            log.debug("False positive from Bloom filter for key: {}", key);
        }
        return value;
    }
    
    /**
     * Put value in cache and update Bloom filter
     */
    public void put(String key, Object value) {
        cache.put(key, value);
        bloomFilter.put(key);
        log.debug("Added to cache: {}", key);
    }
    
    /**
     * Delete from cache (note: cannot remove from Bloom filter)
     */
    public void delete(String key) {
        cache.invalidate(key);
        // Cannot remove from Bloom filter!
        // This is acceptable - false positives just cause extra cache lookup
        log.debug("Removed from cache (Bloom filter still contains): {}", key);
    }
    
    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        return cache.stats().toString();
    }
}
```

**Controller with Smart Cache**:
```java
package com.example.bloomfilter.controller;

import com.example.bloomfilter.service.SmartCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
public class CacheController {
    
    private final SmartCacheService cacheService;
    
    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> getCachedValue(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();
        Object value = cacheService.get(key);
        
        if (value != null) {
            response.put("key", key);
            response.put("value", value);
            response.put("cached", true);
            return ResponseEntity.ok(response);
        }
        
        response.put("key", key);
        response.put("cached", false);
        response.put("message", "Not in cache");
        return ResponseEntity.ok(response);
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> setCachedValue(
            @RequestParam String key, 
            @RequestParam String value) {
        
        cacheService.put(key, value);
        
        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("message", "Value cached successfully");
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{key}")
    public ResponseEntity<Map<String, Object>> deleteCachedValue(@PathVariable String key) {
        cacheService.delete(key);
        
        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("message", "Value removed from cache");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> response = new HashMap<>();
        response.put("stats", cacheService.getCacheStats());
        return ResponseEntity.ok(response);
    }
}
```

---

## 8. Configuration & Tuning

### Memory vs False Positive Rate

```
┌─────────────────────────────────────────────────────────┐
│  For 1 Million Items:                                   │
│                                                          │
│  FP Rate    Bits/Item    Total Memory    Use Case      │
│  ────────────────────────────────────────────────────── │
│  10%        4.8 bits     600 KB          Testing only   │
│  1%         9.6 bits     1.2 MB          Most common    │
│  0.1%       14.4 bits    1.8 MB          Critical data  │
│  0.01%      19.2 bits    2.4 MB          Financial      │
│                                                          │
│  Formula: m = -n * ln(p) / (ln(2))^2                    │
│  where: m = bits, n = items, p = FP rate               │
└─────────────────────────────────────────────────────────┘
```

### Optimal Configuration Examples:

```java
// High-throughput rate limiting (tolerate 1% FP)
BloomFilter<String> rateLimitFilter = BloomFilter.create(
    Funnels.stringFunnel(StandardCharsets.UTF_8),
    10_000_000,  // 10M users
    0.01         // 1% false positive rate
);
// Memory: ~12 MB for 10M users

// Financial fraud detection (need 0.01% FP)
BloomFilter<String> fraudFilter = BloomFilter.create(
    Funnels.stringFunnel(StandardCharsets.UTF_8),
    1_000_000,   // 1M transactions
    0.0001       // 0.01% false positive rate
);
// Memory: ~2.4 MB for 1M transactions

// Cache key lookup (1% FP acceptable)
BloomFilter<String> cacheFilter = BloomFilter.create(
    Funnels.stringFunnel(StandardCharsets.UTF_8),
    100_000_000, // 100M keys
    0.01         // 1% false positive rate
);
// Memory: ~120 MB for 100M keys
```

**Configuration Properties**:
```properties
# application.yml
bloom-filter:
  rate-limit:
    capacity: 10000000
    error-rate: 0.01
    window-minutes: 60
  
  deduplication:
    capacity: 100000000
    error-rate: 0.001
    window-minutes: 60
  
  cache:
    capacity: 10000000
    error-rate: 0.01
    max-cache-size: 100000
    expire-minutes: 60
```

**Configuration Class**:
```java
package com.example.bloomfilter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "bloom-filter")
public class BloomFilterProperties {
    
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private DeduplicationConfig deduplication = new DeduplicationConfig();
    private CacheConfig cache = new CacheConfig();
    
    @Data
    public static class RateLimitConfig {
        private int capacity = 1_000_000;
        private double errorRate = 0.01;
        private int windowMinutes = 60;
    }
    
    @Data
    public static class DeduplicationConfig {
        private int capacity = 10_000_000;
        private double errorRate = 0.001;
        private int windowMinutes = 60;
    }
    
    @Data
    public static class CacheConfig {
        private int capacity = 10_000_000;
        private double errorRate = 0.01;
        private int maxCacheSize = 100_000;
        private int expireMinutes = 60;
    }
}
```

---

## 9. Kubernetes Deployment Example

```yaml
# ConfigMap for Bloom filter settings
apiVersion: v1
kind: ConfigMap
metadata:
  name: bloom-filter-config
data:
  BLOOM_CAPACITY: "1000000"
  BLOOM_ERROR_RATE: "0.01"
  BLOOM_SYNC_ENABLED: "true"
  REDIS_HOST: "redis-service"
  KAFKA_BROKERS: "kafka:9092"

---
# Deployment with Bloom filter
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api
  template:
    metadata:
      labels:
        app: api
    spec:
      containers:
      - name: api
        image: bloom-filter-service:latest
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 8081
          name: actuator
        env:
        - name: BLOOM_FILTER_CAPACITY
          valueFrom:
            configMapKeyRef:
              name: bloom-filter-config
              key: BLOOM_CAPACITY
        - name: BLOOM_FILTER_ERROR_RATE
          valueFrom:
            configMapKeyRef:
              name: bloom-filter-config
              key: BLOOM_ERROR_RATE
        - name: REDIS_HOST
          valueFrom:
            configMapKeyRef:
              name: bloom-filter-config
              key: REDIS_HOST
        - name: KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            configMapKeyRef:
              name: bloom-filter-config
              key: KAFKA_BROKERS
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: JAVA_OPTS
          value: "-Xms256m -Xmx512m -XX:+UseG1GC"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"  # Reserve memory for Bloom filter + JVM
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"

---
# Redis for centralized Bloom filter
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis-bloom
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redislabs/rebloom:latest
        ports:
        - containerPort: 6379
        volumeMounts:
        - name: redis-storage
          mountPath: /data
      volumes:
      - name: redis-storage
        persistentVolumeClaim:
          claimName: redis-pvc
```

---

## 10. Performance Comparison

```
Scenario: Check if 1M users are rate-limited
Request rate: 10,000 requests/second
Setup: 3 pods, 1M unique users

┌─────────────────────────────────────────────────────────┐
│  Approach              Latency    Memory    Accuracy    │
│  ──────────────────────────────────────────────────────│
│  Database Lookup       50-100ms   N/A       100%        │
│  Redis HashSet         5-10ms     16 MB     100%        │
│  Redis Bloom Filter    2-5ms      1.2 MB    99% (1% FP) │
│  Local Bloom Filter    <0.1ms     1.2 MB    99% (1% FP) │
│  (with sync)                      per pod               │
└─────────────────────────────────────────────────────────┘

Cost Analysis (for 1M users):
- Database: ~$200/month (RDS instance)
- Redis HashSet: ~$100/month (memory)
- Redis Bloom: ~$20/month (memory)
- Local Bloom: ~$5/month (pod memory) × 3 pods = $15/month
```

---

## 11. Common Pitfalls & Solutions

### Pitfall 1: Cannot Delete Items
```java
// ❌ WRONG: Trying to delete from Bloom filter
BloomFilter<String> bloom = BloomFilter.create(
    Funnels.stringFunnel(StandardCharsets.UTF_8), 1000, 0.01);
bloom.put("user123");
// bloom.remove("user123");  // This method doesn't exist!

// ✅ SOLUTION 1: Use time-windowed approach
@Service
public class TTLBloomFilterService {
    
    private BloomFilter<String> current;
    private long timestamp;
    private final int ttlSeconds = 3600;
    
    @PostConstruct
    public void init() {
        current = createFilter();
        timestamp = Instant.now().getEpochSecond();
    }
    
    private BloomFilter<String> createFilter() {
        return BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            1_000_000,
            0.01
        );
    }
    
    public void add(String item) {
        checkAndRotate();
        current.put(item);
    }
    
    public boolean contains(String item) {
        checkAndRotate();
        return current.mightContain(item);
    }
    
    private void checkAndRotate() {
        long now = Instant.now().getEpochSecond();
        if (now - timestamp > ttlSeconds) {
            // Reset filter after TTL
            current = createFilter();
            timestamp = now;
            log.info("Bloom filter rotated");
        }
    }
}

// ✅ SOLUTION 2: Use multiple time-bucketed filters
@Service
public class TimeBucketedBloomFilterService {
    
    private Map<String, BloomFilter<String>> buckets = new ConcurrentHashMap<>();
    private final int bucketDurationMinutes = 10;
    
    private String getCurrentBucket() {
        LocalDateTime now = LocalDateTime.now();
        int bucket = now.getMinute() / bucketDurationMinutes;
        String key = now.format(DateTimeFormatter.ofPattern("yyyyMMddHH")) + bucket;
        return key;
    }
    
    public void add(String item) {
        String bucketKey = getCurrentBucket();
        BloomFilter<String> filter = buckets.computeIfAbsent(bucketKey, 
            k -> BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8), 
                100_000, 
                0.01
            )
        );
        filter.put(item);
        
        // Clean old buckets
        cleanOldBuckets();
    }
    
    private void cleanOldBuckets() {
        String currentBucket = getCurrentBucket();
        buckets.keySet().removeIf(key -> !key.equals(currentBucket));
    }
}
```

### Pitfall 2: Filter Size Grows Unbounded
```java
// ❌ WRONG: Adding unlimited items beyond capacity
BloomFilter<String> bloom = BloomFilter.create(
    Funnels.stringFunnel(StandardCharsets.UTF_8),
    1000,  // Capacity: 1000
    0.01
);
for (int i = 0; i < 10000; i++) {  // Adding 10x capacity!
    bloom.put("user" + i);
}
// False positive rate increases dramatically!

// ✅ SOLUTION: Use multiple Bloom filters or size monitoring
@Service
public class ScalableBloomFilterService {
    
    private List<BloomFilter<String>> filters = new ArrayList<>();
    private static final int FILTER_CAPACITY = 1_000_000;
    private static final double ERROR_RATE = 0.01;
    private int currentFilterSize = 0;
    
    @PostConstruct
    public void init() {
        filters.add(createNewFilter());
    }
    
    private BloomFilter<String> createNewFilter() {
        return BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            FILTER_CAPACITY,
            ERROR_RATE
        );
    }
    
    public void add(String item) {
        // Check if current filter is approaching capacity
        if (currentFilterSize >= FILTER_CAPACITY * 0.9) {
            // Create new filter
            filters.add(createNewFilter());
            currentFilterSize = 0;
            log.info("Created new Bloom filter. Total filters: {}", filters.size());
        }
        
        // Add to latest filter
        BloomFilter<String> currentFilter = filters.get(filters.size() - 1);
        currentFilter.put(item);
        currentFilterSize++;
    }
    
    public boolean mightContain(String item) {
        // Check all filters
        for (BloomFilter<String> filter : filters) {
            if (filter.mightContain(item)) {
                return true;
            }
        }
        return false;
    }
    
    public int getFilterCount() {
        return filters.size();
    }
}
```

### Pitfall 3: Not Handling False Positives
```java
// ❌ WRONG: Treating Bloom filter as 100% accurate
@Service
public class BadRateLimiterService {
    
    private BloomFilter<String> bloomFilter;
    
    public boolean shouldBlock(String userId) {
        if (bloomFilter.mightContain(userId)) {
            blockUser(userId);  // Might block innocent users (false positive)!
            return true;
        }
        return false;
    }
}

// ✅ SOLUTION: Two-phase check with authoritative source
@Service
@RequiredArgsConstructor
public class SmartRateLimiterService {
    
    private final BloomFilter<String> bloomFilter;
    private final RedisTemplate<String, String> redisTemplate;
    
    public boolean shouldBlock(String userId) {
        // Phase 1: Fast check with Bloom filter
        if (!bloomFilter.mightContain(userId)) {
            // Definitely NOT rate limited
            return false;
        }
        
        // Phase 2: Bloom filter says "might be rate limited"
        // Confirm with authoritative source (Redis/Database)
        String redisKey = "rate_limit:" + userId;
        Boolean isActuallyRateLimited = redisTemplate.hasKey(redisKey);
        
        if (Boolean.TRUE.equals(isActuallyRateLimited)) {
            // Confirmed rate limited
            log.warn("User {} is rate limited (confirmed)", userId);
            return true;
        } else {
            // False positive from Bloom filter
            log.debug("False positive from Bloom filter for user: {}", userId);
            return false;
        }
    }
    
    public void addToRateLimit(String userId) {
        // Add to both Bloom filter and authoritative source
        bloomFilter.put(userId);
        String redisKey = "rate_limit:" + userId;
        redisTemplate.opsForValue().set(redisKey, "1", 1, TimeUnit.HOURS);
    }
}

// Alternative: Use Bloom filter for optimization only
@Service
@RequiredArgsConstructor
public class OptimizedCacheService {
    
    private final BloomFilter<String> bloomFilter;
    private final Cache<String, Object> cache;
    
    public Object get(String key) {
        // Bloom filter as optimization to skip cache lookup
        if (!bloomFilter.mightContain(key)) {
            // Definitely not in cache - skip expensive lookup
            return null;
        }
        
        // Might be in cache - do the lookup
        // False positives just mean an extra cache lookup (acceptable)
        return cache.getIfPresent(key);
    }
}
```

---

## 12. Quick Reference

### When to Use Bloom Filters

✅ **Use When:**
- Need to quickly check membership in large sets
- Can tolerate false positives (but not false negatives)
- Memory is limited
- Need extremely fast lookups
- Examples: Rate limiting, deduplication, cache optimization

❌ **Don't Use When:**
- Need 100% accuracy
- Need to delete items frequently
- Need to store actual data (Bloom filters only store bits)
- Dataset is small (just use HashMap)

### Libraries by Language

```xml
<!-- Java - Google Guava (Most Popular) -->
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>32.1.3-jre</version>
</dependency>

<!-- Usage -->
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;

BloomFilter<String> bloom = BloomFilter.create(
    Funnels.stringFunnel(StandardCharsets.UTF_8),
    1_000_000,  // expected insertions
    0.01        // false positive rate
);

bloom.put("user123");
boolean exists = bloom.mightContain("user123");
```

```xml
<!-- Java - Redis Bloom (JReBloom) -->
<dependency>
    <groupId>com.redislabs</groupId>
    <artifactId>jrebloom</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Usage -->
import io.rebloom.client.Client;

Client client = new Client("localhost", 6379);
client.createFilter("myFilter", 1000000, 0.01);
client.add("myFilter", "user123");
boolean exists = client.exists("myFilter", "user123");
```

Other Languages:
```python
# Python
pip install pybloom-live
from pybloom_live import BloomFilter

# Go
go get github.com/bits-and-blooms/bloom/v3
import "github.com/bits-and-blooms/bloom/v3"

# Node.js
npm install bloom-filters
const { BloomFilter } = require('bloom-filters');

# Rust
bloom = "0.3"
use bloom::BloomFilter;
```

---

## 13. Monitoring & Observability

**Maven Dependencies**:
```xml
<dependencies>
    <!-- Spring Boot Actuator for metrics -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- Micrometer for Prometheus -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
</dependencies>
```

**Monitored Bloom Filter Service**:
```java
package com.example.bloomfilter.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class MonitoredBloomFilterService {
    
    private final MeterRegistry meterRegistry;
    private BloomFilter<String> bloomFilter;
    
    // Metrics
    private Counter itemsAddedCounter;
    private Counter checkHitCounter;
    private Counter checkMissCounter;
    private long estimatedSize = 0;
    
    private static final int CAPACITY = 1_000_000;
    private static final double ERROR_RATE = 0.01;
    
    public MonitoredBloomFilterService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void init() {
        bloomFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            CAPACITY,
            ERROR_RATE
        );
        
        // Initialize metrics
        itemsAddedCounter = Counter.builder("bloom_filter_items_total")
            .description("Total items added to Bloom filter")
            .register(meterRegistry);
        
        checkHitCounter = Counter.builder("bloom_filter_checks_total")
            .tag("result", "hit")
            .description("Total Bloom filter checks that returned 'might exist'")
            .register(meterRegistry);
        
        checkMissCounter = Counter.builder("bloom_filter_checks_total")
            .tag("result", "miss")
            .description("Total Bloom filter checks that returned 'definitely not exists'")
            .register(meterRegistry);
        
        // Size gauge
        Gauge.builder("bloom_filter_size_bytes", this, service -> {
            // Approximate: bits_per_item * items / 8
            double bitsPerItem = -Math.log(ERROR_RATE) / Math.pow(Math.log(2), 2);
            return (bitsPerItem * estimatedSize) / 8;
        })
        .description("Estimated memory used by Bloom filter in bytes")
        .register(meterRegistry);
        
        Gauge.builder("bloom_filter_capacity", this, service -> CAPACITY)
            .description("Bloom filter capacity")
            .register(meterRegistry);
        
        log.info("Monitored Bloom filter initialized");
    }
    
    public void add(String item) {
        bloomFilter.put(item);
        itemsAddedCounter.increment();
        estimatedSize++;
        log.debug("Added item to Bloom filter: {}", item);
    }
    
    public boolean mightContain(String item) {
        boolean result = bloomFilter.mightContain(item);
        
        if (result) {
            checkHitCounter.increment();
        } else {
            checkMissCounter.increment();
        }
        
        return result;
    }
}
```

**Application Properties for Actuator**:
```properties
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
```

**Prometheus Scrape Configuration**:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'spring-boot-bloom-filter'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080']
```

**Grafana Dashboard Queries**:
```promql
# Rate of items added per second
rate(bloom_filter_items_total[5m])

# Total checks (hits)
rate(bloom_filter_checks_total{result="hit"}[5m])

# Total checks (misses)
rate(bloom_filter_checks_total{result="miss"}[5m])

# False positive rate estimate
rate(bloom_filter_checks_total{result="hit"}[5m]) / 
  (rate(bloom_filter_checks_total{result="hit"}[5m]) + 
   rate(bloom_filter_checks_total{result="miss"}[5m]))

# Memory usage
bloom_filter_size_bytes

# Capacity utilization
bloom_filter_items_total / bloom_filter_capacity * 100
```

**Custom Health Indicator**:
```java
package com.example.bloomfilter.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class BloomFilterHealthIndicator implements HealthIndicator {
    
    private final MonitoredBloomFilterService bloomFilterService;
    private static final long CAPACITY = 1_000_000;
    
    public BloomFilterHealthIndicator(MonitoredBloomFilterService bloomFilterService) {
        this.bloomFilterService = bloomFilterService;
    }
    
    @Override
    public Health health() {
        long estimatedSize = bloomFilterService.getEstimatedSize();
        double utilizationPercent = (estimatedSize * 100.0) / CAPACITY;
        
        if (utilizationPercent > 90) {
            return Health.down()
                .withDetail("utilizationPercent", utilizationPercent)
                .withDetail("status", "Bloom filter near capacity")
                .build();
        } else if (utilizationPercent > 75) {
            return Health.up()
                .withDetail("utilizationPercent", utilizationPercent)
                .withDetail("status", "Warning: Bloom filter filling up")
                .build();
        } else {
            return Health.up()
                .withDetail("utilizationPercent", utilizationPercent)
                .withDetail("status", "Bloom filter healthy")
                .build();
        }
    }
}
```

---

## Summary

**Bloom Filters in Multi-Pod Architecture:**
1. **Centralized (Redis)**: Simplest, consistent, but network overhead
2. **Local + Sync (Kafka)**: Fast, scalable, eventual consistency
3. **Partitioned**: Best for huge datasets, requires routing

**Key Metrics:**
- False Positive Rate: 0.01 (1%) is standard
- Memory: ~10 bits per item for 1% FP
- Speed: <0.1ms local, 2-5ms Redis

**Best Practices:**
- Use time-windowed filters for TTL
- Monitor false positive rate
- Have fallback for false positives
- Choose architecture based on consistency needs