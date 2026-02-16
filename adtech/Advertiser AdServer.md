# 2. DSP (Demand-Side Platform) - Complete Code (Continued)

## **1. Main Application**

```java
// DspApplication.java
package com.adserver.dsp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DspApplication {
    public static void main(String[] args) {
        SpringApplication.run(DspApplication.class, args);
    }
}
```

---

## **2. Entity Classes**

```java
// Campaign.java
package com.adserver.dsp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Campaign {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "campaign_id", unique = true, nullable = false, length = 50)
    private String campaignId;
    
    @Column(name = "advertiser_id", nullable = false, length = 50)
    private String advertiserId;
    
    @Column(name = "campaign_name", length = 200)
    private String campaignName;
    
    @Column(name = "daily_budget", precision = 10, scale = 4)
    private BigDecimal dailyBudget;
    
    @Column(name = "total_budget", precision = 10, scale = 4)
    private BigDecimal totalBudget;
    
    @Column(name = "spent_today", precision = 10, scale = 4)
    private BigDecimal spentToday = BigDecimal.ZERO;
    
    @Column(name = "total_spent", precision = 10, scale = 4)
    private BigDecimal totalSpent = BigDecimal.ZERO;
    
    @Column(name = "max_bid", precision = 10, scale = 4)
    private BigDecimal maxBid;
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private Status status = Status.ACTIVE;
    
    @Column(name = "creative_id", length = 50)
    private String creativeId;
    
    @Column(name = "landing_url", length = 500)
    private String landingUrl;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum Status {
        ACTIVE, PAUSED, COMPLETED
    }
}
```

```java
// TargetingRule.java
package com.adserver.dsp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "targeting_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TargetingRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "rule_id", unique = true, length = 50)
    private String ruleId;
    
    @Column(name = "campaign_id", nullable = false, length = 50)
    private String campaignId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_operator", length = 20)
    private TargetOperator targetOperator = TargetOperator.INCLUDE;
    
    @Column(name = "target_value", length = 255)
    private String targetValue;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum TargetType {
        GEO, DEVICE, INTEREST, DEMOGRAPHIC, KEYWORD
    }
    
    public enum TargetOperator {
        INCLUDE, EXCLUDE
    }
}
```

```java
// BidHistory.java
package com.adserver.dsp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bid_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BidHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "bid_id", unique = true, length = 50)
    private String bidId;
    
    @Column(name = "campaign_id", length = 50)
    private String campaignId;
    
    @Column(name = "auction_id", length = 50)
    private String auctionId;
    
    @Column(name = "bid_amount", precision = 10, scale = 4)
    private BigDecimal bidAmount;
    
    @Column(name = "won")
    private Boolean won = false;
    
    @Column(name = "final_price", precision = 10, scale = 4)
    private BigDecimal finalPrice;
    
    @Column(name = "timestamp", columnDefinition = "TIMESTAMP(3)")
    private Instant timestamp;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
```

---

## **3. DTOs**

```java
// BidRequest.java
package com.adserver.dsp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BidRequest {
    
    private String id;  // auction_id
    private List<Impression> imp;
    private Site site;
    private Device device;
    private User user;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Impression {
        private String id;
        private Banner banner;
        private BigDecimal bidfloor;
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Banner {
            private Integer w;
            private Integer h;
            private Integer pos;
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Site {
        private String page;
        private String domain;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Device {
        private String ua;
        private String ip;
        private Integer devicetype;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String id;
    }
}
```

```java
// BidResponse.java
package com.adserver.dsp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidResponse {
    
    private String bidId;
    private String auctionId;
    private String dspId;
    private BigDecimal bidAmount;
    private String adTagUrl;
    private String creativeId;
    private Integer priority;
}
```

---

## **4. Repositories**

```java
// CampaignRepository.java
package com.adserver.dsp.repository;

import com.adserver.dsp.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    
    Optional<Campaign> findByCampaignId(String campaignId);
    
    @Query("SELECT c FROM Campaign c WHERE c.status = 'ACTIVE' " +
           "AND c.startDate <= :today AND c.endDate >= :today " +
           "AND c.spentToday < c.dailyBudget " +
           "AND c.totalSpent < c.totalBudget")
    List<Campaign> findActiveCampaignsWithBudget(@Param("today") LocalDate today);
}
```

```java
// TargetingRuleRepository.java
package com.adserver.dsp.repository;

import com.adserver.dsp.model.TargetingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TargetingRuleRepository extends JpaRepository<TargetingRule, Long> {
    
    List<TargetingRule> findByCampaignId(String campaignId);
}
```

```java
// BidHistoryRepository.java
package com.adserver.dsp.repository;

import com.adserver.dsp.model.BidHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BidHistoryRepository extends JpaRepository<BidHistory, Long> {
    
    Optional<BidHistory> findByBidId(String bidId);
}
```

---

## **5. Configuration Classes**

```java
// RedisConfig.java
package com.adserver.dsp.config;

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
        
        // Key serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Value serializer
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }
}
```

```java
// KafkaConfig.java
package com.adserver.dsp.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    // Producer Configuration
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    // Consumer Configuration
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "dsp-service");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
```

```java
// AsyncConfig.java
package com.adserver.dsp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("dsp-async-");
        executor.initialize();
        return executor;
    }
}
```

---

## **6. Service Classes**

```java
// TargetingService.java
package com.adserver.dsp.service;

import com.adserver.dsp.dto.BidRequest;
import com.adserver.dsp.model.Campaign;
import com.adserver.dsp.model.TargetingRule;
import com.adserver.dsp.repository.TargetingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TargetingService {
    
    private final TargetingRuleRepository targetingRuleRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public boolean matchesTargeting(Campaign campaign, BidRequest bidRequest) {
        List<TargetingRule> rules = getTargetingRules(campaign.getCampaignId());
        
        if (rules.isEmpty()) {
            return true;  // No targeting rules = match all
        }
        
        for (TargetingRule rule : rules) {
            boolean matches = evaluateRule(rule, bidRequest);
            
            if (rule.getTargetOperator() == TargetingRule.TargetOperator.INCLUDE && !matches) {
                log.debug("Failed INCLUDE rule: {} for campaign {}", 
                    rule.getTargetType(), campaign.getCampaignId());
                return false;
            }
            
            if (rule.getTargetOperator() == TargetingRule.TargetOperator.EXCLUDE && matches) {
                log.debug("Failed EXCLUDE rule: {} for campaign {}", 
                    rule.getTargetType(), campaign.getCampaignId());
                return false;
            }
        }
        
        return true;
    }
    
    private List<TargetingRule> getTargetingRules(String campaignId) {
        String cacheKey = "targeting:" + campaignId;
        
        @SuppressWarnings("unchecked")
        List<TargetingRule> cached = (List<TargetingRule>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached;
        }
        
        List<TargetingRule> rules = targetingRuleRepository.findByCampaignId(campaignId);
        redisTemplate.opsForValue().set(cacheKey, rules, 10, TimeUnit.MINUTES);
        
        return rules;
    }
    
    private boolean evaluateRule(TargetingRule rule, BidRequest bidRequest) {
        return switch (rule.getTargetType()) {
            case GEO -> matchesGeo(rule.getTargetValue(), bidRequest);
            case DEVICE -> matchesDevice(rule.getTargetValue(), bidRequest);
            case INTEREST -> true;  // Would require user profile data
            case DEMOGRAPHIC -> true;  // Would require user profile data
            case KEYWORD -> matchesKeyword(rule.getTargetValue(), bidRequest);
        };
    }
    
    private boolean matchesGeo(String targetValue, BidRequest bidRequest) {
        if (targetValue == null || bidRequest.getSite() == null) {
            return false;
        }
        
        String domain = bidRequest.getSite().getDomain();
        if (domain == null) {
            return false;
        }
        
        List<String> allowedCountries = Arrays.asList(targetValue.split(","));
        
        for (String country : allowedCountries) {
            if (domain.endsWith("." + country.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean matchesDevice(String targetValue, BidRequest bidRequest) {
        if (targetValue == null || bidRequest.getDevice() == null) {
            return false;
        }
        
        Integer deviceType = bidRequest.getDevice().getDevicetype();
        if (deviceType == null) {
            return false;
        }
        
        List<String> allowedDevices = Arrays.asList(targetValue.split(","));
        
        String deviceName = switch (deviceType) {
            case 4 -> "mobile";
            case 5 -> "tablet";
            case 2 -> "desktop";
            default -> "unknown";
        };
        
        return allowedDevices.contains(deviceName.trim());
    }
    
    private boolean matchesKeyword(String targetValue, BidRequest bidRequest) {
        if (targetValue == null || bidRequest.getSite() == null || bidRequest.getSite().getPage() == null) {
            return false;
        }
        
        String pageUrl = bidRequest.getSite().getPage().toLowerCase();
        List<String> keywords = Arrays.asList(targetValue.split(","));
        
        for (String keyword : keywords) {
            if (pageUrl.contains(keyword.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
```

```java
// BudgetService.java
package com.adserver.dsp.service;

import com.adserver.dsp.model.Campaign;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class BudgetService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public boolean hasBudget(Campaign campaign, BigDecimal bidAmount) {
        // Check daily budget
        String dailyKey = "budget:daily:" + campaign.getCampaignId() + ":" + LocalDate.now();
        Object spentObj = redisTemplate.opsForValue().get(dailyKey);
        BigDecimal spentToday = spentObj != null ? 
            new BigDecimal(spentObj.toString()) : BigDecimal.ZERO;
        
        if (spentToday.add(bidAmount).compareTo(campaign.getDailyBudget()) > 0) {
            log.debug("Campaign {} exceeds daily budget. Spent: {}, Budget: {}", 
                campaign.getCampaignId(), spentToday, campaign.getDailyBudget());
            return false;
        }
        
        // Check total budget
        String totalKey = "budget:total:" + campaign.getCampaignId();
        Object totalSpentObj = redisTemplate.opsForValue().get(totalKey);
        BigDecimal totalSpent = totalSpentObj != null ? 
            new BigDecimal(totalSpentObj.toString()) : BigDecimal.ZERO;
        
        if (totalSpent.add(bidAmount).compareTo(campaign.getTotalBudget()) > 0) {
            log.debug("Campaign {} exceeds total budget. Spent: {}, Budget: {}", 
                campaign.getCampaignId(), totalSpent, campaign.getTotalBudget());
            return false;
        }
        
        return true;
    }
    
    public void reserveBudget(Campaign campaign, BigDecimal amount) {
        // Increment daily spend
        String dailyKey = "budget:daily:" + campaign.getCampaignId() + ":" + LocalDate.now();
        redisTemplate.opsForValue().increment(dailyKey, amount.doubleValue());
        redisTemplate.expire(dailyKey, 1, TimeUnit.DAYS);
        
        // Increment total spend
        String totalKey = "budget:total:" + campaign.getCampaignId();
        redisTemplate.opsForValue().increment(totalKey, amount.doubleValue());
        
        log.debug("Reserved budget {} for campaign {}", amount, campaign.getCampaignId());
    }
    
    public void releaseBudget(Campaign campaign, BigDecimal amount) {
        // Decrement if bid didn't win
        String dailyKey = "budget:daily:" + campaign.getCampaignId() + ":" + LocalDate.now();
        redisTemplate.opsForValue().increment(dailyKey, -amount.doubleValue());
        
        String totalKey = "budget:total:" + campaign.getCampaignId();
        redisTemplate.opsForValue().increment(totalKey, -amount.doubleValue());
        
        log.debug("Released budget {} for campaign {}", amount, campaign.getCampaignId());
    }
}
```

```java
// PacingService.java
package com.adserver.dsp.service;

import com.adserver.dsp.model.Campaign;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;

@Service
@Slf4j
public class PacingService {
    
    /**
     * Calculate bid adjustment multiplier based on budget pacing
     * Returns multiplier between 0.5 and 1.5
     */
    public BigDecimal calculatePacingMultiplier(Campaign campaign) {
        // Calculate how much of the day has passed (0.0 to 1.0)
        LocalTime now = LocalTime.now();
        double hoursElapsed = now.getHour() + (now.getMinute() / 60.0);
        double dayProgress = hoursElapsed / 24.0;
        
        // Calculate expected spend at this point in the day
        BigDecimal expectedSpend = campaign.getDailyBudget()
            .multiply(BigDecimal.valueOf(dayProgress));
        
        // Actual spend so far
        BigDecimal actualSpend = campaign.getSpentToday();
        
        // Calculate pacing multiplier
        if (actualSpend.compareTo(expectedSpend) < 0) {
            // Under-pacing: bid more aggressively (up to 20% boost)
            log.debug("Campaign {} under-pacing. Expected: {}, Actual: {}", 
                campaign.getCampaignId(), expectedSpend, actualSpend);
            return BigDecimal.valueOf(1.2);
            
        } else if (actualSpend.compareTo(expectedSpend.multiply(BigDecimal.valueOf(1.2))) > 0) {
            // Over-pacing: bid less aggressively (20% reduction)
            log.debug("Campaign {} over-pacing. Expected: {}, Actual: {}", 
                campaign.getCampaignId(), expectedSpend, actualSpend);
            return BigDecimal.valueOf(0.8);
        }
        
        // On track
        return BigDecimal.ONE;
    }
}
```

```java
// BidService.java
package com.adserver.dsp.service;

import com.adserver.dsp.dto.BidRequest;
import com.adserver.dsp.dto.BidResponse;
import com.adserver.dsp.model.BidHistory;
import com.adserver.dsp.model.Campaign;
import com.adserver.dsp.repository.BidHistoryRepository;
import com.adserver.dsp.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class BidService {
    
    private final CampaignRepository campaignRepository;
    private final BidHistoryRepository bidHistoryRepository;
    private final TargetingService targetingService;
    private final BudgetService budgetService;
    private final PacingService pacingService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${dsp.id:dsp_internal}")
    private String dspId;
    
    @Value("${advertiser.adserver.url:http://localhost:8082}")
    private String advertiserAdServerUrl;
    
    public BidResponse processBidRequest(BidRequest bidRequest) {
        log.debug("Processing bid request for auction: {}", bidRequest.getId());
        
        // 1. Get eligible campaigns
        List<Campaign> campaigns = getEligibleCampaigns();
        
        if (campaigns.isEmpty()) {
            log.debug("No eligible campaigns for auction {}", bidRequest.getId());
            return null;
        }
        
        // 2. Find best matching campaign
        Campaign selectedCampaign = null;
        BigDecimal bestBid = BigDecimal.ZERO;
        
        for (Campaign campaign : campaigns) {
            // Check targeting
            if (!targetingService.matchesTargeting(campaign, bidRequest)) {
                continue;
            }
            
            // Check frequency cap
            if (!checkFrequencyCap(campaign, bidRequest.getUser().getId())) {
                continue;
            }
            
            // Calculate bid
            BigDecimal bid = calculateBid(campaign, bidRequest);
            
            // Check budget
            if (!budgetService.hasBudget(campaign, bid)) {
                continue;
            }
            
            if (bid.compareTo(bestBid) > 0) {
                bestBid = bid;
                selectedCampaign = campaign;
            }
        }
        
        if (selectedCampaign == null || 
            bestBid.compareTo(bidRequest.getImp().get(0).getBidfloor()) < 0) {
            log.debug("No valid bid for auction {}. Best bid: {}, Floor: {}", 
                bidRequest.getId(), bestBid, bidRequest.getImp().get(0).getBidfloor());
            return null;
        }
        
        // 3. Reserve budget (optimistic locking)
        budgetService.reserveBudget(selectedCampaign, bestBid);
        
        // 4. Create bid response
        String bidId = "bid_" + UUID.randomUUID().toString().replace("-", "");
        
        // 5. Save bid history (async)
        saveBidHistoryAsync(bidId, selectedCampaign, bidRequest.getId(), bestBid);
        
        // 6. Build ad tag URL pointing to Advertiser Ad Server
        String adTagUrl = buildAdTagUrl(selectedCampaign, bidRequest.getId());
        
        log.info("Bid submitted: auctionId={}, campaignId={}, bid={}", 
            bidRequest.getId(), selectedCampaign.getCampaignId(), bestBid);
        
        return BidResponse.builder()
                .bidId(bidId)
                .auctionId(bidRequest.getId())
                .dspId(dspId)
                .bidAmount(bestBid)
                .adTagUrl(adTagUrl)
                .creativeId(selectedCampaign.getCreativeId())
                .build();
    }
    
    private List<Campaign> getEligibleCampaigns() {
        String cacheKey = "campaigns:eligible:" + LocalDate.now();
        
        @SuppressWarnings("unchecked")
        List<Campaign> cached = (List<Campaign>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        
        List<Campaign> campaigns = campaignRepository
            .findActiveCampaignsWithBudget(LocalDate.now());
        
        redisTemplate.opsForValue().set(cacheKey, campaigns, 1, TimeUnit.MINUTES);
        
        return campaigns;
    }
    
    private BigDecimal calculateBid(Campaign campaign, BidRequest bidRequest) {
        // Base bid from campaign
        BigDecimal baseBid = campaign.getMaxBid();
        
        // Apply pacing multiplier
        BigDecimal pacingMultiplier = pacingService.calculatePacingMultiplier(campaign);
        BigDecimal adjustedBid = baseBid.multiply(pacingMultiplier);
        
        // Ensure bid is above floor price
        BigDecimal floorPrice = bidRequest.getImp().get(0).getBidfloor();
        if (adjustedBid.compareTo(floorPrice) < 0) {
            adjustedBid = floorPrice.multiply(BigDecimal.valueOf(1.01));
        }
        
        // Cap at max bid
        if (adjustedBid.compareTo(campaign.getMaxBid()) > 0) {
            adjustedBid = campaign.getMaxBid();
        }
        
        return adjustedBid.setScale(4, RoundingMode.HALF_UP);
    }
    
    private boolean checkFrequencyCap(Campaign campaign, String userId) {
        if (userId == null || userId.isEmpty()) {
            return true;
        }
        
        String freqKey = "freq:" + campaign.getCampaignId() + ":" + userId + ":" + LocalDate.now();
        Object countObj = redisTemplate.opsForValue().get(freqKey);
        int count = countObj != null ? Integer.parseInt(countObj.toString()) : 0;
        
        int maxFrequency = 5;  // Max 5 impressions per day
        
        return count < maxFrequency;
    }
    
    private String buildAdTagUrl(Campaign campaign, String auctionId) {
        return String.format("%s/api/v1/serve?creativeId=%s&campaignId=%s&auctionId=%s&landingUrl=%s",
                advertiserAdServerUrl,
                campaign.getCreativeId(),
                campaign.getCampaignId(),
                auctionId,
                campaign.getLandingUrl());
    }
    
    @Async
    protected void saveBidHistoryAsync(String bidId, Campaign campaign, 
                                       String auctionId, BigDecimal bidAmount) {
        try {
            BidHistory history = new BidHistory();
            history.setBidId(bidId);
            history.setCampaignId(campaign.getCampaignId());
            history.setAuctionId(auctionId);
            history.setBidAmount(bidAmount);
            history.setWon(false);
            history.setTimestamp(Instant.now());
            
            bidHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Error saving bid history: {}", e.getMessage());
        }
    }
}
```

---

## **7. Controller**

```java
// BidController.java
package com.adserver.dsp.controller;

import com.adserver.dsp.dto.BidRequest;
import com.adserver.dsp.dto.BidResponse;
import com.adserver.dsp.service.BidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Slf4j
@RequiredArgsConstructor
public class BidController {
    
    private final BidService bidService;
    
    @PostMapping("/bid")
    public ResponseEntity<BidResponse> handleBidRequest(@RequestBody BidRequest bidRequest) {
        log.debug("Received bid request for auction: {}", bidRequest.getId());
        
        long startTime = System.currentTimeMillis();
        
        try {
            BidResponse response = bidService.processBidRequest(bidRequest);
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Bid processing completed in {}ms", duration);
            
            if (response == null) {
                return ResponseEntity.noContent().build();
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing bid request: {}", e.getMessage(), e);
            return ResponseEntity.noContent().build();
        }
    }
}
```

---

## **8. Kafka Consumer**

```java
// EventConsumer.java
package com.adserver.dsp.kafka;

import com.adserver.dsp.model.BidHistory;
import com.adserver.dsp.repository.BidHistoryRepository;
import com.adserver.dsp.service.BudgetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {
    
    private final BidHistoryRepository bidHistoryRepository;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(topics = "auction-events", groupId = "dsp-service")
    public void consumeAuctionEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            
            String auctionId = event.get("auctionId").asText();
            String winnerDspId = event.get("winnerDspId").asText();
            
            log.info("Auction {} won by DSP: {}", auctionId, winnerDspId);
            
            // Update bid history if we participated
            // (Could be enhanced to release budget if we lost)
            
        } catch (Exception e) {
            log.error("Error processing auction event: {}", e.getMessage());
        }
    }
}
```

## **9. Kafka Producer**

```java

// EventProducer.java - NEW for Advertiser
package com.adserver.advertiser.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Advertiser Ad Server Event Producer for INTERNAL analytics only
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventProducer {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String IMPRESSION_TOPIC = "advertiser-impressions";
    private static final String CLICK_TOPIC = "advertiser-clicks";
    
    /**
     * Track impressions for advertiser's own analytics
     */
    public void sendImpressionEvent(ImpressionEvent event) {
        try {
            kafkaTemplate.send(IMPRESSION_TOPIC, event.getImpressionId(), event);
            log.debug("Published impression event: {}", event.getImpressionId());
        } catch (Exception e) {
            log.error("Failed to send impression event: {}", e.getMessage());
        }
    }
    
    /**
     * Track clicks for advertiser's own analytics
     */
    public void sendClickEvent(ClickEvent event) {
        try {
            kafkaTemplate.send(CLICK_TOPIC, event.getClickId(), event);
            log.debug("Published click event: {}", event.getClickId());
        } catch (Exception e) {
            log.error("Failed to send click event: {}", e.getMessage());
        }
    }
}

// ImpressionEvent.java - NEW
package com.adserver.advertiser.kafka;

        import lombok.Builder;
        import lombok.Data;
        import java.time.Instant;

/**
 * Advertiser's own impression tracking
 */
@Data
@Builder
public class ImpressionEvent {
    private String impressionId;
    private String creativeId;
    private String campaignId;
    private String auctionId;
    private String userId;
    private Instant timestamp;
}

// ClickEvent.java - NEW
package com.adserver.advertiser.kafka;

        import lombok.Builder;
        import lombok.Data;
        import java.time.Instant;

/**
 * Advertiser's own click tracking
 */
@Data
@Builder
public class ClickEvent {
    private String clickId;
    private String impressionId;
    private String creativeId;
    private String campaignId;
    private String landingUrl;
    private Instant timestamp;
}

```
---

## **9. Application Properties**

```yaml
# application.yml
spring:
  application:
    name: dsp-service
  
  datasource:
    url: jdbc:mysql://localhost:3306/dsp_service?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 30
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true
  
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
    consumer:
      group-id: dsp-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: 1

# DSP Configuration
dsp:
  id: dsp_internal

# Advertiser Ad Server URL
advertiser:
  adserver:
    url: http://localhost:8082

server:
  port: 8081

logging:
  level:
    com.adserver.dsp: DEBUG
    org.springframework.web: INFO
    org.springframework.kafka: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

---

## **10. pom.xml**

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
        <relativePath/>
    </parent>
    
    <groupId>com.adserver</groupId>
    <artifactId>dsp-service</artifactId>
    <version>1.0.0</version>
    <name>DSP Service</name>
    <description>Demand-Side Platform for Ad Bidding</description>
    
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
        
        <!-- MySQL Driver -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
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
        
        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```
