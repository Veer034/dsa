# 2. DSP (Demand-Side Platform)

## **Project Structure**

```
dsp-service/
├── src/main/java/com/adserver/dsp/
│   ├── controller/
│   │   └── BidController.java
│   ├── service/
│   │   ├── BidService.java
│   │   ├── BudgetService.java
│   │   ├── TargetingService.java
│   │   └── PacingService.java
│   ├── model/
│   │   ├── Campaign.java
│   │   ├── TargetingRule.java
│   │   └── BidHistory.java
│   ├── repository/
│   │   ├── CampaignRepository.java
│   │   ├── TargetingRuleRepository.java
│   │   └── BidHistoryRepository.java
│   ├── dto/
│   │   ├── BidRequest.java
│   │   ├── BidResponse.java
│   │   └── CampaignDto.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   └── RestTemplateConfig.java
│   └── kafka/
│       └── EventConsumer.java
```

## **Database Schema (MySQL)**

```sql
-- Campaigns
CREATE TABLE campaigns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id VARCHAR(50) UNIQUE NOT NULL,
    advertiser_id VARCHAR(50) NOT NULL,
    campaign_name VARCHAR(200),
    daily_budget DECIMAL(10,4),
    total_budget DECIMAL(10,4),
    spent_today DECIMAL(10,4) DEFAULT 0,
    total_spent DECIMAL(10,4) DEFAULT 0,
    max_bid DECIMAL(10,4),
    start_date DATE,
    end_date DATE,
    status ENUM('ACTIVE', 'PAUSED', 'COMPLETED') DEFAULT 'ACTIVE',
    creative_id VARCHAR(50),  -- Reference to Advertiser Ad Server
    landing_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_advertiser (advertiser_id),
    INDEX idx_dates (start_date, end_date)
);

-- Targeting Rules
CREATE TABLE targeting_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id VARCHAR(50) UNIQUE NOT NULL,
    campaign_id VARCHAR(50) NOT NULL,
    target_type ENUM('GEO', 'DEVICE', 'INTEREST', 'DEMOGRAPHIC', 'KEYWORD') NOT NULL,
    target_operator ENUM('INCLUDE', 'EXCLUDE') DEFAULT 'INCLUDE',
    target_value VARCHAR(255),  -- JSON or comma-separated values
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_campaign (campaign_id),
    INDEX idx_type (target_type),
    FOREIGN KEY (campaign_id) REFERENCES campaigns(campaign_id) ON DELETE CASCADE
);

-- Bid History
CREATE TABLE bid_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bid_id VARCHAR(50) UNIQUE NOT NULL,
    campaign_id VARCHAR(50),
    auction_id VARCHAR(50),
    bid_amount DECIMAL(10,4),
    won BOOLEAN DEFAULT FALSE,
    final_price DECIMAL(10,4),  -- If won
    timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_campaign (campaign_id, timestamp),
    INDEX idx_auction (auction_id),
    INDEX idx_timestamp (timestamp)
) PARTITION BY RANGE (UNIX_TIMESTAMP(timestamp)) (
    PARTITION p_2025_01 VALUES LESS THAN (UNIX_TIMESTAMP('2025-02-01')),
    PARTITION p_2025_02 VALUES LESS THAN (UNIX_TIMESTAMP('2025-03-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- Budget Tracking (Daily aggregates)
CREATE TABLE budget_tracking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id VARCHAR(50),
    date DATE,
    spent DECIMAL(10,4) DEFAULT 0,
    impressions INT DEFAULT 0,
    clicks INT DEFAULT 0,
    conversions INT DEFAULT 0,
    UNIQUE KEY uk_campaign_date (campaign_id, date),
    INDEX idx_date (date)
);

-- Frequency Cap Tracking (in Redis, but backup in DB)
CREATE TABLE frequency_caps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id VARCHAR(50),
    user_id VARCHAR(50),
    impression_count INT DEFAULT 0,
    last_impression TIMESTAMP,
    date DATE,
    UNIQUE KEY uk_campaign_user_date (campaign_id, user_id, date),
    INDEX idx_date (date)
);
```

## **Entity Classes**

```java
// Campaign.java
package com.adserver.dsp.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
@Data
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "campaign_id", unique = true, nullable = false)
    private String campaignId;
    
    @Column(name = "advertiser_id")
    private String advertiserId;
    
    @Column(name = "campaign_name")
    private String campaignName;
    
    @Column(name = "daily_budget")
    private BigDecimal dailyBudget;
    
    @Column(name = "total_budget")
    private BigDecimal totalBudget;
    
    @Column(name = "spent_today")
    private BigDecimal spentToday;
    
    @Column(name = "total_spent")
    private BigDecimal totalSpent;
    
    @Column(name = "max_bid")
    private BigDecimal maxBid;
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;
    
    @Column(name = "creative_id")
    private String creativeId;  // Reference to Advertiser Ad Server
    
    @Column(name = "landing_url")
    private String landingUrl;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum Status {
        ACTIVE, PAUSED, COMPLETED
    }
}

// TargetingRule.java
package com.adserver.dsp.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "targeting_rules")
@Data
public class TargetingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "rule_id", unique = true)
    private String ruleId;
    
    @Column(name = "campaign_id")
    private String campaignId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private TargetType targetType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_operator")
    private TargetOperator targetOperator;
    
    @Column(name = "target_value")
    private String targetValue;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    public enum TargetType {
        GEO, DEVICE, INTEREST, DEMOGRAPHIC, KEYWORD
    }
    
    public enum TargetOperator {
        INCLUDE, EXCLUDE
    }
}

// BidHistory.java
package com.adserver.dsp.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bid_history")
@Data
public class BidHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "bid_id", unique = true)
    private String bidId;
    
    @Column(name = "campaign_id")
    private String campaignId;
    
    @Column(name = "auction_id")
    private String auctionId;
    
    @Column(name = "bid_amount")
    private BigDecimal bidAmount;
    
    @Column(name = "won")
    private Boolean won;
    
    @Column(name = "final_price")
    private BigDecimal finalPrice;
    
    @Column(name = "timestamp")
    private Instant timestamp;
}
```

## **DTOs**

```java
// BidRequest.java (OpenRTB-style - same as Publisher sent)
package com.adserver.dsp.dto;

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
    private String adTagUrl;  // Points to Advertiser Ad Server
    private String creativeId;
    private Integer priority;
}
```

## **Repositories**

```java
// CampaignRepository.java
package com.adserver.dsp.repository;

import com.adserver.dsp.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    
    @Query("SELECT c FROM Campaign c WHERE c.status = 'ACTIVE' " +
           "AND c.startDate <= :today AND c.endDate >= :today " +
           "AND c.spentToday < c.dailyBudget " +
           "AND c.totalSpent < c.totalBudget")
    List<Campaign> findActiveCampaignsWithBudget(LocalDate today);
}

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

// BidHistoryRepository.java
package com.adserver.dsp.repository;

import com.adserver.dsp.model.BidHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BidHistoryRepository extends JpaRepository<BidHistory, Long> {
}
```

## **Services**

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
        // Get targeting rules from cache or DB
        List<TargetingRule> rules = getTargetingRules(campaign.getCampaignId());
        
        if (rules.isEmpty()) {
            return true;  // No targeting = match all
        }
        
        for (TargetingRule rule : rules) {
            boolean matches = evaluateRule(rule, bidRequest);
            
            if (rule.getTargetOperator() == TargetingRule.TargetOperator.INCLUDE && !matches) {
                return false;  // Must match all INCLUDE rules
            }
            
            if (rule.getTargetOperator() == TargetingRule.TargetOperator.EXCLUDE && matches) {
                return false;  // Must not match any EXCLUDE rules
            }
        }
        
        return true;
    }
    
    private List<TargetingRule> getTargetingRules(String campaignId) {
        String cacheKey = "targeting:" + campaignId;
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
            case INTEREST -> true;  // Would need user profile data
            case DEMOGRAPHIC -> true;  // Would need user profile data
            case KEYWORD -> matchesKeyword(rule.getTargetValue(), bidRequest);
        };
    }
    
    private boolean matchesGeo(String targetValue, BidRequest bidRequest) {
        // targetValue could be: "US,CA,GB"
        String domain = bidRequest.getSite().getDomain();
        List<String> allowedCountries = Arrays.asList(targetValue.split(","));
        
        // Simple domain-based geo matching (in production, use IP geolocation)
        for (String country : allowedCountries) {
            if (domain.endsWith("." + country.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean matchesDevice(String targetValue, BidRequest bidRequest) {
        // targetValue: "mobile,tablet" or "desktop"
        Integer deviceType = bidRequest.getDevice().getDevicetype();
        List<String> allowedDevices = Arrays.asList(targetValue.split(","));
        
        String deviceName = switch (deviceType) {
            case 4 -> "mobile";
            case 5 -> "tablet";
            case 2 -> "desktop";
            default -> "unknown";
        };
        
        return allowedDevices.contains(deviceName);
    }
    
    private boolean matchesKeyword(String targetValue, BidRequest bidRequest) {
        // targetValue: "sports,news,tech"
        String pageUrl = bidRequest.getSite().getPage().toLowerCase();
        List<String> keywords = Arrays.asList(targetValue.split(","));
        
        for (String keyword : keywords) {
            if (pageUrl.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}

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
        String spentStr = (String) redisTemplate.opsForValue().get(dailyKey);
        BigDecimal spentToday = spentStr != null ? new BigDecimal(spentStr) : BigDecimal.ZERO;
        
        if (spentToday.add(bidAmount).compareTo(campaign.getDailyBudget()) > 0) {
            log.debug("Campaign {} exceeds daily budget", campaign.getCampaignId());
            return false;
        }
        
        // Check total budget
        String totalKey = "budget:total:" + campaign.getCampaignId();
        String totalSpentStr = (String) redisTemplate.opsForValue().get(totalKey);
        BigDecimal totalSpent = totalSpentStr != null ? new BigDecimal(totalSpentStr) : BigDecimal.ZERO;
        
        if (totalSpent.add(bidAmount).compareTo(campaign.getTotalBudget()) > 0) {
            log.debug("Campaign {} exceeds total budget", campaign.getCampaignId());
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
    }
    
    public void releaseBudget(Campaign campaign, BigDecimal amount) {
        // If bid didn't win, release reserved budget
        String dailyKey = "budget:daily:" + campaign.getCampaignId() + ":" + LocalDate.now();
        redisTemplate.opsForValue().increment(dailyKey, -amount.doubleValue());
        
        String totalKey = "budget:total:" + campaign.getCampaignId();
        redisTemplate.opsForValue().increment(totalKey, -amount.doubleValue());
    }
}

// PacingService.java
package com.adserver.dsp.service;

import com.adserver.dsp.model.Campaign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class PacingService {
    
    /**
     * Calculate bid adjustment based on pacing
     * Returns multiplier (0.5 to 1.5)
     */
    public BigDecimal calculatePacingMultiplier(Campaign campaign) {
        LocalDate today = LocalDate.now();
        
        // Calculate how much of the day has passed (0.0 to 1.0)
        LocalTime now = LocalTime.now();
        double hoursElapsed = now.getHour() + (now.getMinute() / 60.0);
        double dayProgress = hoursElapsed / 24.0;
        
        // Calculate how much budget should have been spent by now
        BigDecimal expectedSpend = campaign.getDailyBudget().multiply(BigDecimal.valueOf(dayProgress));
        
        // Actual spend
        BigDecimal actualSpend = campaign.getSpentToday();
        
        // Calculate pacing multiplier
        if (actualSpend.compareTo(expectedSpend) < 0) {
            // Under-pacing: bid more aggressively
            return BigDecimal.valueOf(1.2);
        } else if (actualSpend.compareTo(expectedSpend.multiply(BigDecimal.valueOf(1.2))) > 0) {
            // Over-pacing: bid less aggressively
            return BigDecimal.valueOf(0.8);
        }
        
        // On track
        return BigDecimal.ONE;
    }
}

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
    private final EventProducer eventProducer; // ADDED
    
    @Value("${dsp.id:dsp_internal}")
    private String dspId;
    
    @Value("${advertiser.adserver.url:http://localhost:8082}")
    private String advertiserAdServerUrl;
    
    public BidResponse processBidRequest(BidRequest bidRequest) {
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
        
        if (selectedCampaign == null || bestBid.compareTo(bidRequest.getImp().get(0).getBidfloor()) < 0) {
            log.debug("No valid bid for auction {}", bidRequest.getId());
            // Log no-bid to internal analytics
            publishBidEvent(null, bidRequest.getId(), BigDecimal.ZERO, false);
            return null;
        }
        
        // 3. Reserve budget (optimistic locking)
        budgetService.reserveBudget(selectedCampaign, bestBid);
        
        // 4. Create bid response
        String bidId = "bid_" + UUID.randomUUID().toString().replace("-", "");
        
        // 5. Save bid history (async)
        saveBidHistory(bidId, selectedCampaign, bidRequest.getId(), bestBid);
        
        // 6. Build ad tag URL pointing to Advertiser Ad Server
        String adTagUrl = buildAdTagUrl(selectedCampaign, bidRequest.getId());

        publishBidEvent(selectedCampaign.getCampaignId(), bidRequest.getId(), bestBid, true);

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
        List<Campaign> cached = (List<Campaign>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        
        List<Campaign> campaigns = campaignRepository.findActiveCampaignsWithBudget(LocalDate.now());
        redisTemplate.opsForValue().set(cacheKey, campaigns, 1, TimeUnit.MINUTES);
        
        return campaigns;
    }
    
    private BigDecimal calculateBid(Campaign campaign, BidRequest bidRequest) {
        // Base bid
        BigDecimal baseBid = campaign.getMaxBid();
        
        // Apply pacing multiplier
        BigDecimal pacingMultiplier = pacingService.calculatePacingMultiplier(campaign);
        BigDecimal adjustedBid = baseBid.multiply(pacingMultiplier);
        
        // Ensure bid is above floor price
        BigDecimal floorPrice = bidRequest.getImp().get(0).getBidfloor();
        if (adjustedBid.compareTo(floorPrice) < 0) {
            adjustedBid = floorPrice.multiply(BigDecimal.valueOf(1.01));  // Just above floor
        }
        
        // Cap at max bid
        if (adjustedBid.compareTo(campaign.getMaxBid()) > 0) {
            adjustedBid = campaign.getMaxBid();
        }
        
        return adjustedBid.setScale(4, RoundingMode.HALF_UP);
    }
    
    private boolean checkFrequencyCap(Campaign campaign, String userId) {
        if (userId == null || userId.isEmpty()) {
            return true;  // No user ID, can't cap
        }
        
        // Check daily frequency cap (e.g., max 5 impressions per user per day)
        String freqKey = "freq:" + campaign.getCampaignId() + ":" + userId + ":" + LocalDate.now();
        String countStr = (String) redisTemplate.opsForValue().get(freqKey);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        
        int maxFrequency = 5;  // Could be campaign-specific
        
        return count < maxFrequency;
    }
    
    private void incrementFrequency(Campaign campaign, String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }
        
        String freqKey = "freq:" + campaign.getCampaignId() + ":" + userId + ":" + LocalDate.now();
        redisTemplate.opsForValue().increment(freqKey, 1);
        redisTemplate.expire(freqKey, 1, TimeUnit.DAYS);
    }
    
    private String buildAdTagUrl(Campaign campaign, String auctionId) {
        // Ad tag URL points to Advertiser Ad Server
        return String.format("%s/api/v1/serve?creativeId=%s&campaignId=%s&auctionId=%s&landingUrl=%s",
                advertiserAdServerUrl,
                campaign.getCreativeId(),
                campaign.getCampaignId(),
                auctionId,
                campaign.getLandingUrl());
    }
    
    private void saveBidHistory(String bidId, Campaign campaign, String auctionId, BigDecimal bidAmount) {
        BidHistory history = new BidHistory();
        history.setBidId(bidId);
        history.setCampaignId(campaign.getCampaignId());
        history.setAuctionId(auctionId);
        history.setBidAmount(bidAmount);
        history.setWon(false);  // Will be updated later if won
        history.setTimestamp(Instant.now());
        
        bidHistoryRepository.save(history);
    }

    /**
     * NEW: Publish bid events to internal Kafka for analytics
     */
    private void publishBidEvent(String campaignId, String auctionId, BigDecimal bidAmount, boolean submitted) {
        BidEvent event = BidEvent.builder()
                .bidId("bid_" + UUID.randomUUID().toString())
                .campaignId(campaignId)
                .auctionId(auctionId)
                .bidAmount(bidAmount)
                .submitted(submitted)
                .timestamp(Instant.now())
                .build();

        eventProducer.sendBidEvent(event);
    }
}
```

## **Controller**

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
        
        BidResponse response = bidService.processBidRequest(bidRequest);
        
        long duration = System.currentTimeMillis() - startTime;
        log.debug("Bid processing took {}ms", duration);
        
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(response);
    }
}


// EventProducer.java - NEW for DSP
package com.adserver.dsp.kafka;

        import lombok.RequiredArgsConstructor;
        import lombok.extern.slf4j.Slf4j;
        import org.springframework.kafka.core.KafkaTemplate;
        import org.springframework.stereotype.Component;

/**
 * DSP Event Producer for INTERNAL analytics only
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String BID_EVENTS_TOPIC = "dsp-internal-bid-events";

    /**
     * Publish bid events for internal DSP analytics
     */
    public void sendBidEvent(BidEvent event) {
        try {
            kafkaTemplate.send(BID_EVENTS_TOPIC, event.getBidId(), event);
            log.debug("Published internal bid event: {}", event.getBidId());
        } catch (Exception e) {
            log.error("Failed to send bid event: {}", e.getMessage());
        }
    }
}

/**
 * Internal event for DSP's own analytics
 */
@Data
@Builder
public class BidEvent {
    private String bidId;
    private String campaignId;
    private String auctionId;
    private BigDecimal bidAmount;
    private Boolean submitted;
    private String targetingMatched;
    private Instant timestamp;
}

```


## **Application Properties**

```yaml
# application.yml
spring:
  application:
    name: dsp-service
  
  datasource:
    url: jdbc:mysql://localhost:3306/dsp_service?useSSL=false&serverTimezone=UTC
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 30
      minimum-idle: 5
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
  
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: dsp-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer

dsp:
  id: dsp_internal
  
advertiser:
  adserver:
    url: http://localhost:8082

server:
  port: 8081
```
