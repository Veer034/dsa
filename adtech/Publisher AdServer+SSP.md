# Low-Level Design: Complete Programmatic Ad System

## **System Architecture**

```
Publisher Ad Server (SSP) ←→ DSP ←→ Advertiser Ad Server
         ↓                    ↓              ↓
      MySQL              MySQL          MySQL
      Redis              Redis          Redis
         ↓                    ↓              ↓
      Kafka ←────────────────┴──────────────┘
         ↓
    ScyllaDB (Events)
```

---

# 1. Publisher Ad Server (SSP Mode)

## **Tech Stack**
- Spring Boot
- MySQL (inventory, auctions)
- Redis (caching, rate limiting)
- Kafka (event streaming)
- ScyllaDB (analytics)

## **Project Structure**

```
publisher-ad-server/
├── src/main/java/com/adserver/publisher/
│   ├── controller/
│   │   └── AdRequestController.java
│   ├── service/
│   │   ├── AdRequestService.java
│   │   ├── AuctionService.java
│   │   └── DspClientService.java
│   ├── model/
│   │   ├── AdSlot.java
│   │   ├── DspPartner.java
│   │   ├── Auction.java
│   │   └── BidResponse.java
│   ├── repository/
│   │   ├── AdSlotRepository.java
│   │   ├── DspPartnerRepository.java
│   │   └── AuctionRepository.java
│   ├── dto/
│   │   ├── AdRequest.java
│   │   ├── BidRequest.java
│   │   └── AdResponse.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   ├── KafkaConfig.java
│   │   └── RestTemplateConfig.java
│   └── kafka/
│       └── EventProducer.java
```

## **Database Schema (MySQL)**

```sql
-- Ad Slots
CREATE TABLE ad_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_id VARCHAR(50) UNIQUE NOT NULL,
    page_type VARCHAR(50),
    position VARCHAR(50),
    width INT,
    height INT,
    floor_price DECIMAL(10,4),
    status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_slot_id (slot_id),
    INDEX idx_status (status)
);

-- DSP Partners
CREATE TABLE dsp_partners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dsp_id VARCHAR(50) UNIQUE NOT NULL,
    dsp_name VARCHAR(100),
    endpoint_url VARCHAR(255),
    is_internal BOOLEAN DEFAULT FALSE,
    priority INT DEFAULT 0,
    timeout_ms INT DEFAULT 100,
    status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_priority (priority DESC)
);

-- Auctions
CREATE TABLE auctions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    auction_id VARCHAR(50) UNIQUE NOT NULL,
    slot_id VARCHAR(50),
    user_id VARCHAR(50),
    page_url VARCHAR(500),
    device_type VARCHAR(20),
    winner_dsp_id VARCHAR(50),
    winning_bid DECIMAL(10,4),
    floor_price DECIMAL(10,4),
    num_bids INT,
    auction_timestamp TIMESTAMP(3),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_auction_id (auction_id),
    INDEX idx_timestamp (auction_timestamp),
    INDEX idx_slot (slot_id, auction_timestamp)
) PARTITION BY RANGE (UNIX_TIMESTAMP(auction_timestamp)) (
    PARTITION p_2025_01 VALUES LESS THAN (UNIX_TIMESTAMP('2025-02-01')),
    PARTITION p_2025_02 VALUES LESS THAN (UNIX_TIMESTAMP('2025-03-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- Bid Responses (temporary storage)
CREATE TABLE bid_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bid_id VARCHAR(50) UNIQUE NOT NULL,
    auction_id VARCHAR(50),
    dsp_id VARCHAR(50),
    bid_amount DECIMAL(10,4),
    ad_tag_url TEXT,
    creative_id VARCHAR(50),
    timestamp TIMESTAMP(3),
    INDEX idx_auction (auction_id),
    INDEX idx_timestamp (timestamp)
);

-- Impressions (tracking)
CREATE TABLE impressions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    impression_id VARCHAR(50) UNIQUE NOT NULL,
    auction_id VARCHAR(50),
    slot_id VARCHAR(50),
    dsp_id VARCHAR(50),
    revenue DECIMAL(10,4),
    timestamp TIMESTAMP(3),
    INDEX idx_auction (auction_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_dsp (dsp_id, timestamp)
) PARTITION BY RANGE (UNIX_TIMESTAMP(timestamp)) (
    PARTITION p_2025_01 VALUES LESS THAN (UNIX_TIMESTAMP('2025-02-01')),
    PARTITION p_2025_02 VALUES LESS THAN (UNIX_TIMESTAMP('2025-03-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

## **Entity Classes**

```java
// AdSlot.java
package com.adserver.publisher.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ad_slots")
@Data
public class AdSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "slot_id", unique = true, nullable = false)
    private String slotId;
    
    @Column(name = "page_type")
    private String pageType;
    
    @Column(name = "position")
    private String position;
    
    @Column(name = "width")
    private Integer width;
    
    @Column(name = "height")
    private Integer height;
    
    @Column(name = "floor_price")
    private BigDecimal floorPrice;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum Status {
        ACTIVE, INACTIVE
    }
}

// DspPartner.java
package com.adserver.publisher.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "dsp_partners")
@Data
public class DspPartner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "dsp_id", unique = true)
    private String dspId;
    
    @Column(name = "dsp_name")
    private String dspName;
    
    @Column(name = "endpoint_url")
    private String endpointUrl;
    
    @Column(name = "is_internal")
    private Boolean isInternal;
    
    @Column(name = "priority")
    private Integer priority;
    
    @Column(name = "timeout_ms")
    private Integer timeoutMs;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;
    
    public enum Status {
        ACTIVE, INACTIVE
    }
}

// Auction.java
package com.adserver.publisher.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "auctions")
@Data
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "auction_id", unique = true)
    private String auctionId;
    
    @Column(name = "slot_id")
    private String slotId;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "page_url")
    private String pageUrl;
    
    @Column(name = "device_type")
    private String deviceType;
    
    @Column(name = "winner_dsp_id")
    private String winnerDspId;
    
    @Column(name = "winning_bid")
    private BigDecimal winningBid;
    
    @Column(name = "floor_price")
    private BigDecimal floorPrice;
    
    @Column(name = "num_bids")
    private Integer numBids;
    
    @Column(name = "auction_timestamp")
    private Instant auctionTimestamp;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

## **DTOs**

```java
// AdRequest.java
package com.adserver.publisher.dto;

import lombok.Data;

@Data
public class AdRequest {
    private String slotId;
    private String userId;
    private String pageUrl;
    private String deviceType;
    private String userAgent;
    private String ipAddress;
}

// BidRequest.java (OpenRTB-style)
package com.adserver.publisher.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BidRequest {
    private String id;  // auction_id
    private List<Impression> imp;
    private Site site;
    private Device device;
    private User user;
    
    @Data
    public static class Impression {
        private String id;
        private Banner banner;
        private BigDecimal bidfloor;
        
        @Data
        public static class Banner {
            private Integer w;
            private Integer h;
            private Integer pos;
        }
    }
    
    @Data
    public static class Site {
        private String page;
        private String domain;
    }
    
    @Data
    public static class Device {
        private String ua;
        private String ip;
        private Integer devicetype;
    }
    
    @Data
    public static class User {
        private String id;
    }
}

// BidResponse.java
package com.adserver.publisher.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BidResponse {
    private String bidId;
    private String auctionId;
    private String dspId;
    private BigDecimal bidAmount;
    private String adTagUrl;
    private String creativeId;
    private Integer priority;
}

// AdResponse.java
package com.adserver.publisher.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AdResponse {
    private String adTagUrl;
    private String auctionId;
    private BigDecimal winningBid;
    private String dspId;
}
```

## **Repositories**

```java
// AdSlotRepository.java
package com.adserver.publisher.repository;

import com.adserver.publisher.model.AdSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AdSlotRepository extends JpaRepository<AdSlot, Long> {
    Optional<AdSlot> findBySlotIdAndStatus(String slotId, AdSlot.Status status);
}

// DspPartnerRepository.java
package com.adserver.publisher.repository;

import com.adserver.publisher.model.DspPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DspPartnerRepository extends JpaRepository<DspPartner, Long> {
    List<DspPartner> findByStatusOrderByPriorityDesc(DspPartner.Status status);
}

// AuctionRepository.java
package com.adserver.publisher.repository;

import com.adserver.publisher.model.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, Long> {
}
```

## **Configuration**

```java
// RedisConfig.java
package com.adserver.publisher.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}

// KafkaConfig.java
package com.adserver.publisher.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

// RestTemplateConfig.java
package com.adserver.publisher.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(100))
                .setReadTimeout(Duration.ofMillis(100))
                .build();
    }
}
```

## **Services**

```java
// DspClientService.java
package com.adserver.publisher.service;

import com.adserver.publisher.dto.BidRequest;
import com.adserver.publisher.dto.BidResponse;
import com.adserver.publisher.model.DspPartner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DspClientService {
    
    private final RestTemplate restTemplate;
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);
    
    public List<BidResponse> fetchBidsFromDsps(List<DspPartner> dsps, BidRequest bidRequest) {
        List<CompletableFuture<BidResponse>> futures = new ArrayList<>();
        
        for (DspPartner dsp : dsps) {
            CompletableFuture<BidResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return callDsp(dsp, bidRequest);
                } catch (Exception e) {
                    log.error("Error calling DSP {}: {}", dsp.getDspId(), e.getMessage());
                    return null;
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all responses (with timeout handled by RestTemplate)
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(response -> response != null && response.getBidAmount() != null)
                .toList();
    }
    
    private BidResponse callDsp(DspPartner dsp, BidRequest bidRequest) {
        try {
            // Call DSP endpoint
            BidResponse response = restTemplate.postForObject(
                    dsp.getEndpointUrl() + "/bid",
                    bidRequest,
                    BidResponse.class
            );
            
            if (response != null) {
                response.setDspId(dsp.getDspId());
                response.setPriority(dsp.getPriority());
            }
            
            return response;
        } catch (Exception e) {
            log.warn("DSP {} failed to respond: {}", dsp.getDspId(), e.getMessage());
            return null;
        }
    }
}

// AuctionService.java
package com.adserver.publisher.service;

import com.adserver.publisher.dto.BidResponse;
import com.adserver.publisher.model.AdSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuctionService {
    
    public Optional<BidResponse> selectWinner(List<BidResponse> bids, BigDecimal floorPrice) {
        if (bids == null || bids.isEmpty()) {
            return Optional.empty();
        }
        
        // Apply priority boost for internal DSPs
        bids.forEach(bid -> {
            BigDecimal effectiveBid = bid.getBidAmount();
            
            // 10% boost for high priority DSPs
            if (bid.getPriority() != null && bid.getPriority() > 5) {
                effectiveBid = effectiveBid.multiply(BigDecimal.valueOf(1.10));
            }
            
            bid.setBidAmount(effectiveBid);
        });
        
        // Select highest bid above floor price
        return bids.stream()
                .filter(bid -> bid.getBidAmount().compareTo(floorPrice) >= 0)
                .max(Comparator.comparing(BidResponse::getBidAmount));
    }
}

// AdRequestService.java
package com.adserver.publisher.service;

import com.adserver.publisher.dto.*;
import com.adserver.publisher.kafka.EventProducer;
import com.adserver.publisher.model.AdSlot;
import com.adserver.publisher.model.Auction;
import com.adserver.publisher.model.DspPartner;
import com.adserver.publisher.repository.AdSlotRepository;
import com.adserver.publisher.repository.AuctionRepository;
import com.adserver.publisher.repository.DspPartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdRequestService {
    
    private final AdSlotRepository adSlotRepository;
    private final DspPartnerRepository dspPartnerRepository;
    private final AuctionRepository auctionRepository;
    private final DspClientService dspClientService;
    private final AuctionService auctionService;
    private final EventProducer eventProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String HOUSE_AD_URL = "https://cdn.publisher.com/house-ad.jpg";
    
    public AdResponse handleAdRequest(AdRequest request) {
        // 1. Get slot from cache or DB
        AdSlot slot = getSlot(request.getSlotId());
        if (slot == null || slot.getStatus() != AdSlot.Status.ACTIVE) {
            return createHouseAdResponse();
        }
        
        // 2. Create auction
        String auctionId = "auction_" + UUID.randomUUID().toString().replace("-", "");
        
        // 3. Build bid request
        BidRequest bidRequest = buildBidRequest(auctionId, slot, request);
        
        // 4. Get active DSPs
        List<DspPartner> dsps = getActiveDsps();
        
        // 5. Fetch bids from all DSPs in parallel
        List<BidResponse> bids = dspClientService.fetchBidsFromDsps(dsps, bidRequest);
        
        // 6. Run auction
        Optional<BidResponse> winner = auctionService.selectWinner(bids, slot.getFloorPrice());
        
        if (winner.isEmpty()) {
            return createHouseAdResponse();
        }
        
        // 7. Save auction result
        saveAuction(auctionId, slot, request, winner.get(), bids.size());
        
        // 8. Publish event to Kafka
        publishAuctionEvent(auctionId, slot, winner.get());
        
        // 9. Return winner
        return AdResponse.builder()
                .adTagUrl(winner.get().getAdTagUrl())
                .auctionId(auctionId)
                .winningBid(winner.get().getBidAmount())
                .dspId(winner.get().getDspId())
                .build();
    }
    
    private AdSlot getSlot(String slotId) {
        // Try cache first
        String cacheKey = "slot:" + slotId;
        AdSlot cached = (AdSlot) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached;
        }
        
        // Get from DB
        Optional<AdSlot> slot = adSlotRepository.findBySlotIdAndStatus(slotId, AdSlot.Status.ACTIVE);
        
        if (slot.isPresent()) {
            // Cache for 5 minutes
            redisTemplate.opsForValue().set(cacheKey, slot.get(), 5, TimeUnit.MINUTES);
            return slot.get();
        }
        
        return null;
    }
    
    private List<DspPartner> getActiveDsps() {
        // Try cache first
        String cacheKey = "dsps:active";
        List<DspPartner> cached = (List<DspPartner>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        
        // Get from DB
        List<DspPartner> dsps = dspPartnerRepository.findByStatusOrderByPriorityDesc(DspPartner.Status.ACTIVE);
        
        // Cache for 2 minutes
        redisTemplate.opsForValue().set(cacheKey, dsps, 2, TimeUnit.MINUTES);
        
        return dsps;
    }
    
    private BidRequest buildBidRequest(String auctionId, AdSlot slot, AdRequest request) {
        BidRequest bidRequest = new BidRequest();
        bidRequest.setId(auctionId);
        
        // Impression
        BidRequest.Impression imp = new BidRequest.Impression();
        imp.setId("1");
        imp.setBidfloor(slot.getFloorPrice());
        
        BidRequest.Impression.Banner banner = new BidRequest.Impression.Banner();
        banner.setW(slot.getWidth());
        banner.setH(slot.getHeight());
        banner.setPos(1); // Above the fold
        imp.setBanner(banner);
        
        bidRequest.setImp(List.of(imp));
        
        // Site
        BidRequest.Site site = new BidRequest.Site();
        site.setPage(request.getPageUrl());
        site.setDomain(extractDomain(request.getPageUrl()));
        bidRequest.setSite(site);
        
        // Device
        BidRequest.Device device = new BidRequest.Device();
        device.setUa(request.getUserAgent());
        device.setIp(request.getIpAddress());
        device.setDevicetype(mapDeviceType(request.getDeviceType()));
        bidRequest.setDevice(device);
        
        // User
        BidRequest.User user = new BidRequest.User();
        user.setId(request.getUserId());
        bidRequest.setUser(user);
        
        return bidRequest;
    }
    
    private void saveAuction(String auctionId, AdSlot slot, AdRequest request, 
                            BidResponse winner, int numBids) {
        Auction auction = new Auction();
        auction.setAuctionId(auctionId);
        auction.setSlotId(slot.getSlotId());
        auction.setUserId(request.getUserId());
        auction.setPageUrl(request.getPageUrl());
        auction.setDeviceType(request.getDeviceType());
        auction.setWinnerDspId(winner.getDspId());
        auction.setWinningBid(winner.getBidAmount());
        auction.setFloorPrice(slot.getFloorPrice());
        auction.setNumBids(numBids);
        auction.setAuctionTimestamp(Instant.now());
        auction.setCreatedAt(LocalDateTime.now());
        
        auctionRepository.save(auction);
    }

    /**
     * Modified: Only publishes to internal Kafka for analytics
     * Does NOT notify DSP or Advertiser - they get info via HTTP responses
     */
    private void publishAuctionEvent(String auctionId, AdSlot slot, BidResponse winner, int totalBids) {
        AuctionEvent event = AuctionEvent.builder()
                .auctionId(auctionId)
                .slotId(slot.getSlotId())
                .winnerDspId(winner.getDspId())
                .winningBid(winner.getBidAmount())
                .floorPrice(slot.getFloorPrice())
                .totalBids(totalBids)
                .timestamp(Instant.now())
                .build();

        // This is ONLY for internal analytics (ScyllaDB pipeline)
        // Not for cross-service communication
        eventProducer.sendAuctionEvent(event);

        log.debug("Auction event logged internally for analytics");
    }
    
    private AdResponse createHouseAdResponse() {
        return AdResponse.builder()
                .adTagUrl(HOUSE_AD_URL)
                .auctionId("house_ad")
                .winningBid(BigDecimal.ZERO)
                .dspId("house")
                .build();
    }
    
    private String extractDomain(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private Integer mapDeviceType(String deviceType) {
        return switch (deviceType != null ? deviceType.toLowerCase() : "unknown") {
            case "mobile" -> 4;
            case "tablet" -> 5;
            case "desktop" -> 2;
            default -> 0;
        };
    }
}
```

## **Kafka Producer**

```java
// EventProducer.java
package com.adserver.publisher.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventProducer {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String AUCTION_TOPIC = "auction-events";
    private static final String IMPRESSION_TOPIC = "impression-events";

    /**
     * Publish auction events for INTERNAL analytics only
     * NOT for notifying DSPs or Advertiser Ad Servers
     */
    public void sendAuctionEvent(AuctionEvent event) {
        try {
            kafkaTemplate.send(AUCTION_TOPIC, event.getAuctionId(), event);
            log.debug("Published auction event to internal analytics: {}", event.getAuctionId());
        } catch (Exception e) {
            log.error("Failed to send auction event: {}", e.getMessage());
        }
    }

    /**
     * Publish impression events for INTERNAL analytics only
     * This is Publisher's own tracking, separate from Advertiser tracking
     */
    public void sendImpressionEvent(ImpressionEvent event) {
        try {
            kafkaTemplate.send(IMPRESSION_TOPIC, event.getImpressionId(), event);
            log.debug("Published impression event to internal analytics: {}", event.getImpressionId());
        } catch (Exception e) {
            log.error("Failed to send impression event: {}", e.getMessage());
        }
    }
}

// AuctionEvent.java
package com.adserver.publisher.kafka;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Internal event for Publisher's own analytics
 * NOT sent to external parties (DSP or Advertiser)
 */
@Data
@Builder
public class AuctionEvent {
    private String auctionId;
    private String slotId;
    private String winnerDspId;
    private BigDecimal winningBid;
    private BigDecimal floorPrice;
    private Integer totalBids;
    private String userId;
    private Instant timestamp;
}


// ImpressionEvent.java
package com.adserver.publisher.kafka;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Internal event for Publisher's own impression tracking
 * Advertiser tracks impressions separately via their own pixel
 */
@Data
@Builder
public class ImpressionEvent {
    private String impressionId;
    private String auctionId;
    private String slotId;
    private String dspId;
    private BigDecimal revenue;
    private String userId;
    private Instant timestamp;
}
```

## **Controller**

```java
// AdRequestController.java
package com.adserver.publisher.controller;

import com.adserver.publisher.dto.AdRequest;
import com.adserver.publisher.dto.AdResponse;
import com.adserver.publisher.kafka.EventProducer;
import com.adserver.publisher.kafka.ImpressionEvent;
import com.adserver.publisher.service.AdRequestService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Slf4j
@RequiredArgsConstructor
public class AdRequestController {
    
    private final AdRequestService adRequestService;
private final EventProducer eventProducer;
    
    @GetMapping("/ad-request")
    public ResponseEntity<AdResponse> getAd(
            @RequestParam String slotId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String pageUrl,
            @RequestParam(required = false) String deviceType,
            HttpServletRequest httpRequest
    ) {
        AdRequest request = new AdRequest();
        request.setSlotId(slotId);
        request.setUserId(userId);
        request.setPageUrl(pageUrl);
        request.setDeviceType(deviceType);
        request.setUserAgent(httpRequest.getHeader("User-Agent"));
        request.setIpAddress(httpRequest.getRemoteAddr());
        
        log.info("Ad request for slot: {}", slotId);
        
        AdResponse response = adRequestService.handleAdRequest(request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * MODIFIED: This tracks Publisher's impression only
     * Advertiser tracks separately via their own pixel
     */
    @GetMapping(value = "/track/impression", produces = MediaType.IMAGE_GIF_VALUE)
    public ResponseEntity<byte[]> trackImpression(
            @RequestParam String auctionId,
            @RequestParam String slotId,
            @RequestParam String dspId,
            @RequestParam BigDecimal revenue
    ) {
        String impressionId = "imp_" + UUID.randomUUID().toString().replace("-", "");

        // Log to internal Kafka for Publisher's own analytics
        ImpressionEvent event = ImpressionEvent.builder()
                .impressionId(impressionId)
                .auctionId(auctionId)
                .slotId(slotId)
                .dspId(dspId)
                .revenue(revenue)
                .timestamp(Instant.now())
                .build();

        eventProducer.sendImpressionEvent(event);

        log.debug("Publisher impression tracked internally: {}", impressionId);

        // Return 1x1 transparent GIF
        byte[] pixel = new byte[]{
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00,
                0x01, 0x00, (byte) 0x80, 0x00, 0x00, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x21,
                (byte) 0xF9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
                0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01,
                0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
        };

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_GIF)
                .body(pixel);
    }
}

}


// Publisher Analytics Processor using Apache Flink
```java

package com.adserver.publisher.analytics;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
* Real-time analytics processor using Apache Flink
* Processes auction and impression events from Kafka
  */
  @Slf4j
  public class PublisherAnalyticsProcessor {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public void startProcessing() throws Exception {
  StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
  env.setParallelism(4);

       // Kafka Source for Auction Events
       KafkaSource<String> auctionSource = KafkaSource.<String>builder()
               .setBootstrapServers("localhost:9092")
               .setTopics("auction-events")
               .setValueOnlyDeserializer(new SimpleStringSchema())
               .setGroupId("publisher-analytics")
               .build();
       
       // Kafka Source for Impression Events
       KafkaSource<String> impressionSource = KafkaSource.<String>builder()
               .setBootstrapServers("localhost:9092")
               .setTopics("impression-events")
               .setValueOnlyDeserializer(new SimpleStringSchema())
               .setGroupId("publisher-analytics")
               .build();
       
       // Process Auction Events - Calculate metrics per slot per hour
       DataStream<String> auctionStream = env.fromSource(
               auctionSource, 
               WatermarkStrategy.noWatermarks(), 
               "Auction Events"
       );
       
       auctionStream
               .map(event -> parseAuctionEvent(event))
               .keyBy(event -> event.getSlotId())
               .window(TumblingProcessingTimeWindows.of(Time.hours(1)))
               .aggregate(new AuctionMetricsAggregator())
               .addSink(new ScyllaDBSink("auction_metrics"));
       
       // Process Impression Events - Calculate revenue per slot
       DataStream<String> impressionStream = env.fromSource(
               impressionSource,
               WatermarkStrategy.noWatermarks(),
               "Impression Events"
       );
       
       impressionStream
               .map(event -> parseImpressionEvent(event))
               .keyBy(event -> event.getSlotId())
               .window(TumblingProcessingTimeWindows.of(Time.hours(1)))
               .aggregate(new ImpressionMetricsAggregator())
               .addSink(new ScyllaDBSink("impression_metrics"));
       
       env.execute("Publisher Analytics Pipeline");
  }

  private AuctionEvent parseAuctionEvent(String json) {
  try {
  return objectMapper.readValue(json, AuctionEvent.class);
  } catch (Exception e) {
  log.error("Failed to parse auction event", e);
  return null;
  }
  }

  private ImpressionEvent parseImpressionEvent(String json) {
  try {
  return objectMapper.readValue(json, ImpressionEvent.class);
  } catch (Exception e) {
  log.error("Failed to parse impression event", e);
  return null;
  }
  }
  }
```


-- Publisher Analytics Tables
```roomsql
CREATE TABLE IF NOT EXISTS publisher_analytics.auction_metrics_hourly (
slot_id text,
hour timestamp,
total_auctions bigint,
total_bids bigint,
avg_winning_bid decimal,
max_winning_bid decimal,
min_winning_bid decimal,
fill_rate decimal,
avg_floor_price decimal,
PRIMARY KEY (slot_id, hour)
) WITH CLUSTERING ORDER BY (hour DESC);

CREATE TABLE IF NOT EXISTS publisher_analytics.impression_metrics_hourly (
slot_id text,
hour timestamp,
total_impressions bigint,
total_revenue decimal,
avg_cpm decimal,
unique_users bigint,
PRIMARY KEY (slot_id, hour)
) WITH CLUSTERING ORDER BY (hour DESC);

CREATE TABLE IF NOT EXISTS publisher_analytics.dsp_performance (
dsp_id text,
date date,
total_bids bigint,
total_wins bigint,
win_rate decimal,
total_spend decimal,
avg_bid decimal,
PRIMARY KEY (dsp_id, date)
) WITH CLUSTERING ORDER BY (date DESC);

-- Advertiser Analytics Tables

CREATE TABLE IF NOT EXISTS advertiser_analytics.campaign_metrics_hourly (
campaign_id text,
hour timestamp,
impressions bigint,
clicks bigint,
conversions bigint,
spend decimal,
ctr decimal,
cvr decimal,
cpc decimal,
PRIMARY KEY (campaign_id, hour)
) WITH CLUSTERING ORDER BY (hour DESC);

CREATE TABLE IF NOT EXISTS advertiser_analytics.creative_performance (
creative_id text,
date date,
impressions bigint,
clicks bigint,
ctr decimal,
PRIMARY KEY (creative_id, date)
) WITH CLUSTERING ORDER BY (date DESC);

```

// ============================================================================
// PUBLISHER AD SERVER - Analytics Service & API
// ============================================================================

```java

// AnalyticsService.java
package com.adserver.publisher.service;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublisherAnalyticsService {

    private final CqlSession scyllaSession;
    
    public SlotPerformanceDto getSlotPerformance(String slotId, Instant startTime, Instant endTime) {
        String query = """
            SELECT slot_id, hour, total_auctions, total_bids, avg_winning_bid, 
                   fill_rate, total_impressions, total_revenue, avg_cpm
            FROM publisher_analytics.auction_metrics_hourly
            WHERE slot_id = ? AND hour >= ? AND hour <= ?
            """;
        
        ResultSet rs = scyllaSession.execute(query, slotId, startTime, endTime);
        
        long totalAuctions = 0;
        long totalImpressions = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        List<HourlyMetric> hourlyData = new ArrayList<>();
        
        for (Row row : rs) {
            totalAuctions += row.getLong("total_auctions");
            totalImpressions += row.getLong("total_impressions");
            totalRevenue = totalRevenue.add(row.getBigDecimal("total_revenue"));
            
            hourlyData.add(HourlyMetric.builder()
                    .hour(row.getInstant("hour"))
                    .auctions(row.getLong("total_auctions"))
                    .impressions(row.getLong("total_impressions"))
                    .revenue(row.getBigDecimal("total_revenue"))
                    .avgCpm(row.getBigDecimal("avg_cpm"))
                    .fillRate(row.getBigDecimal("fill_rate"))
                    .build());
        }
        
        BigDecimal fillRate = totalAuctions > 0 
            ? BigDecimal.valueOf(totalImpressions).divide(BigDecimal.valueOf(totalAuctions), 4, BigDecimal.ROUND_HALF_UP)
            : BigDecimal.ZERO;
        
        BigDecimal avgCpm = totalImpressions > 0
            ? totalRevenue.divide(BigDecimal.valueOf(totalImpressions), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(1000))
            : BigDecimal.ZERO;
        
        return SlotPerformanceDto.builder()
                .slotId(slotId)
                .totalAuctions(totalAuctions)
                .totalImpressions(totalImpressions)
                .totalRevenue(totalRevenue)
                .fillRate(fillRate)
                .avgCpm(avgCpm)
                .hourlyData(hourlyData)
                .build();
    }
    
    public List<DspPerformanceDto> getDspPerformance(Instant startDate, Instant endDate) {
        String query = """
            SELECT dsp_id, date, total_bids, total_wins, win_rate, 
                   total_spend, avg_bid
            FROM publisher_analytics.dsp_performance
            WHERE date >= ? AND date <= ?
            ALLOW FILTERING
            """;
        
        ResultSet rs = scyllaSession.execute(query, startDate, endDate);
        List<DspPerformanceDto> results = new ArrayList<>();
        
        for (Row row : rs) {
            results.add(DspPerformanceDto.builder()
                    .dspId(row.getString("dsp_id"))
                    .totalBids(row.getLong("total_bids"))
                    .totalWins(row.getLong("total_wins"))
                    .winRate(row.getBigDecimal("win_rate"))
                    .totalSpend(row.getBigDecimal("total_spend"))
                    .avgBid(row.getBigDecimal("avg_bid"))
                    .build());
        }
        
        return results;
    }
    
    public DashboardSummaryDto getDashboardSummary(Instant startTime, Instant endTime) {
        // Aggregate across all slots
        String query = """
            SELECT SUM(total_auctions) as auctions,
                   SUM(total_impressions) as impressions,
                   SUM(total_revenue) as revenue
            FROM publisher_analytics.impression_metrics_hourly
            WHERE hour >= ? AND hour <= ?
            ALLOW FILTERING
            """;
        
        ResultSet rs = scyllaSession.execute(query, startTime, endTime);
        Row row = rs.one();
        
        if (row != null) {
            long auctions = row.getLong("auctions");
            long impressions = row.getLong("impressions");
            BigDecimal revenue = row.getBigDecimal("revenue");
            
            BigDecimal fillRate = auctions > 0
                ? BigDecimal.valueOf(impressions * 100.0 / auctions)
                : BigDecimal.ZERO;
            
            BigDecimal avgCpm = impressions > 0
                ? revenue.divide(BigDecimal.valueOf(impressions), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(1000))
                : BigDecimal.ZERO;
            
            return DashboardSummaryDto.builder()
                    .totalAuctions(auctions)
                    .totalImpressions(impressions)
                    .totalRevenue(revenue)
                    .fillRate(fillRate)
                    .avgCpm(avgCpm)
                    .build();
        }
        
        return DashboardSummaryDto.builder().build();
    }
}

// DTOs for Analytics
package com.adserver.publisher.dto.analytics;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class SlotPerformanceDto {
private String slotId;
private Long totalAuctions;
private Long totalImpressions;
private BigDecimal totalRevenue;
private BigDecimal fillRate;
private BigDecimal avgCpm;
private List<HourlyMetric> hourlyData;
}

@Data
@Builder
public class HourlyMetric {
private Instant hour;
private Long auctions;
private Long impressions;
private BigDecimal revenue;
private BigDecimal avgCpm;
private BigDecimal fillRate;
}

@Data
@Builder
public class DspPerformanceDto {
private String dspId;
private Long totalBids;
private Long totalWins;
private BigDecimal winRate;
private BigDecimal totalSpend;
private BigDecimal avgBid;
}

@Data
@Builder
public class DashboardSummaryDto {
private Long totalAuctions;
private Long totalImpressions;
private BigDecimal totalRevenue;
private BigDecimal fillRate;
private BigDecimal avgCpm;
}

// AnalyticsController.java
package com.adserver.publisher.controller;

import com.adserver.publisher.dto.analytics.*;
import com.adserver.publisher.service.PublisherAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class PublisherAnalyticsController {

    private final PublisherAnalyticsService analyticsService;
    
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardSummaryDto> getDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        Instant start = startTime.toInstant(ZoneOffset.UTC);
        Instant end = endTime.toInstant(ZoneOffset.UTC);
        
        DashboardSummaryDto summary = analyticsService.getDashboardSummary(start, end);
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/slots/{slotId}/performance")
    public ResponseEntity<SlotPerformanceDto> getSlotPerformance(
            @PathVariable String slotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        Instant start = startTime.toInstant(ZoneOffset.UTC);
        Instant end = endTime.toInstant(ZoneOffset.UTC);
        
        SlotPerformanceDto performance = analyticsService.getSlotPerformance(slotId, start, end);
        return ResponseEntity.ok(performance);
    }
    
    @GetMapping("/dsps/performance")
    public ResponseEntity<List<DspPerformanceDto>> getDspPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        Instant start = startDate.toInstant(ZoneOffset.UTC);
        Instant end = endDate.toInstant(ZoneOffset.UTC);
        
        List<DspPerformanceDto> performance = analyticsService.getDspPerformance(start, end);
        return ResponseEntity.ok(performance);
    }
}

```

## **Application Properties**

```yaml
# application.yml
spring:
  application:
    name: publisher-ad-server
  
  datasource:
    url: jdbc:mysql://localhost:3306/publisher_adserver?useSSL=false&serverTimezone=UTC
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
  
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
  
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: 1
      retries: 3

server:
  port: 8080
  
logging:
  level:
    com.adserver.publisher: INFO
    org.springframework.web: INFO
```

## **pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>
    
    <groupId>com.adserver</groupId>
    <artifactId>publisher-ad-server</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <java.version>17</java.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        
        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```
