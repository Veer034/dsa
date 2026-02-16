

// ============================================================================
// ADVERTISER AD SERVER - Analytics Service & API
// ============================================================================

```java


// AdvertiserAnalyticsService.java
package com.adserver.advertiser.service;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdvertiserAnalyticsService {

    private final CqlSession scyllaSession;
    
    public CampaignPerformanceDto getCampaignPerformance(String campaignId, Instant startTime, Instant endTime) {
        String query = """
            SELECT campaign_id, hour, impressions, clicks, conversions, 
                   spend, ctr, cvr, cpc
            FROM advertiser_analytics.campaign_metrics_hourly
            WHERE campaign_id = ? AND hour >= ? AND hour <= ?
            """;
        
        ResultSet rs = scyllaSession.execute(query, campaignId, startTime, endTime);
        
        long totalImpressions = 0;
        long totalClicks = 0;
        long totalConversions = 0;
        BigDecimal totalSpend = BigDecimal.ZERO;
        List<CampaignHourlyMetric> hourlyData = new ArrayList<>();
        
        for (Row row : rs) {
            totalImpressions += row.getLong("impressions");
            totalClicks += row.getLong("clicks");
            totalConversions += row.getLong("conversions");
            totalSpend = totalSpend.add(row.getBigDecimal("spend"));
            
            hourlyData.add(CampaignHourlyMetric.builder()
                    .hour(row.getInstant("hour"))
                    .impressions(row.getLong("impressions"))
                    .clicks(row.getLong("clicks"))
                    .conversions(row.getLong("conversions"))
                    .spend(row.getBigDecimal("spend"))
                    .ctr(row.getBigDecimal("ctr"))
                    .cvr(row.getBigDecimal("cvr"))
                    .cpc(row.getBigDecimal("cpc"))
                    .build());
        }
        
        BigDecimal ctr = totalImpressions > 0
            ? BigDecimal.valueOf(totalClicks * 100.0 / totalImpressions).setScale(2, BigDecimal.ROUND_HALF_UP)
            : BigDecimal.ZERO;
        
        BigDecimal cvr = totalClicks > 0
            ? BigDecimal.valueOf(totalConversions * 100.0 / totalClicks).setScale(2, BigDecimal.ROUND_HALF_UP)
            : BigDecimal.ZERO;
        
        BigDecimal cpc = totalClicks > 0
            ? totalSpend.divide(BigDecimal.valueOf(totalClicks), 4, BigDecimal.ROUND_HALF_UP)
            : BigDecimal.ZERO;
        
        BigDecimal cpa = totalConversions > 0
            ? totalSpend.divide(BigDecimal.valueOf(totalConversions), 4, BigDecimal.ROUND_HALF_UP)
            : BigDecimal.ZERO;
        
        return CampaignPerformanceDto.builder()
                .campaignId(campaignId)
                .totalImpressions(totalImpressions)
                .totalClicks(totalClicks)
                .totalConversions(totalConversions)
                .totalSpend(totalSpend)
                .ctr(ctr)
                .cvr(cvr)
                .cpc(cpc)
                .cpa(cpa)
                .hourlyData(hourlyData)
                .build();
    }
    
    public List<CreativePerformanceDto> getCreativePerformance(Instant startDate, Instant endDate) {
        String query = """
            SELECT creative_id, date, impressions, clicks, ctr
            FROM advertiser_analytics.creative_performance
            WHERE date >= ? AND date <= ?
            ALLOW FILTERING
            """;
        
        ResultSet rs = scyllaSession.execute(query, startDate, endDate);
        List<CreativePerformanceDto> results = new ArrayList<>();
        
        for (Row row : rs) {
            results.add(CreativePerformanceDto.builder()
                    .creativeId(row.getString("creative_id"))
                    .impressions(row.getLong("impressions"))
                    .clicks(row.getLong("clicks"))
                    .ctr(row.getBigDecimal("ctr"))
                    .build());
        }
        
        return results;
    }
    
    public AdvertiserDashboardDto getDashboard(String advertiserId, Instant startTime, Instant endTime) {
        // Aggregate all campaigns for advertiser
        String query = """
            SELECT SUM(impressions) as impressions,
                   SUM(clicks) as clicks,
                   SUM(conversions) as conversions,
                   SUM(spend) as spend
            FROM advertiser_analytics.campaign_metrics_hourly
            WHERE hour >= ? AND hour <= ?
            ALLOW FILTERING
            """;
        
        ResultSet rs = scyllaSession.execute(query, startTime, endTime);
        Row row = rs.one();
        
        if (row != null) {
            long impressions = row.getLong("impressions");
            long clicks = row.getLong("clicks");
            long conversions = row.getLong("conversions");
            BigDecimal spend = row.getBigDecimal("spend");
            
            BigDecimal ctr = impressions > 0
                ? BigDecimal.valueOf(clicks * 100.0 / impressions).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
            
            BigDecimal cvr = clicks > 0
                ? BigDecimal.valueOf(conversions * 100.0 / clicks).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
            
            BigDecimal roas = conversions > 0 && spend.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(conversions).divide(spend, 4, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
            
            return AdvertiserDashboardDto.builder()
                    .totalImpressions(impressions)
                    .totalClicks(clicks)
                    .totalConversions(conversions)
                    .totalSpend(spend)
                    .avgCtr(ctr)
                    .avgCvr(cvr)
                    .roas(roas)
                    .build();
        }
        
        return AdvertiserDashboardDto.builder().build();
    }
}

// Advertiser DTOs
package com.adserver.advertiser.dto.analytics;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CampaignPerformanceDto {
private String campaignId;
private Long totalImpressions;
private Long totalClicks;
private Long totalConversions;
private BigDecimal totalSpend;
private BigDecimal ctr;      // Click-through rate
private BigDecimal cvr;      // Conversion rate
private BigDecimal cpc;      // Cost per click
private BigDecimal cpa;      // Cost per acquisition
private List<CampaignHourlyMetric> hourlyData;
}

@Data
@Builder
public class CampaignHourlyMetric {
private Instant hour;
private Long impressions;
private Long clicks;
private Long conversions;
private BigDecimal spend;
private BigDecimal ctr;
private BigDecimal cvr;
private BigDecimal cpc;
}

@Data
@Builder
public class CreativePerformanceDto {
private String creativeId;
private Long impressions;
private Long clicks;
private BigDecimal ctr;
}

@Data
@Builder
public class AdvertiserDashboardDto {
private Long totalImpressions;
private Long totalClicks;
private Long totalConversions;
private BigDecimal totalSpend;
private BigDecimal avgCtr;
private BigDecimal avgCvr;
private BigDecimal roas;  // Return on ad spend
}

// AdvertiserAnalyticsController.java
package com.adserver.advertiser.controller;

import com.adserver.advertiser.dto.analytics.*;
import com.adserver.advertiser.service.AdvertiserAnalyticsService;
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
public class AdvertiserAnalyticsController {

    private final AdvertiserAnalyticsService analyticsService;
    
    @GetMapping("/dashboard")
    public ResponseEntity<AdvertiserDashboardDto> getDashboard(
            @RequestParam String advertiserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        Instant start = startTime.toInstant(ZoneOffset.UTC);
        Instant end = endTime.toInstant(ZoneOffset.UTC);
        
        AdvertiserDashboardDto dashboard = analyticsService.getDashboard(advertiserId, start, end);
        return ResponseEntity.ok(dashboard);
    }
    
    @GetMapping("/campaigns/{campaignId}/performance")
    public ResponseEntity<CampaignPerformanceDto> getCampaignPerformance(
            @PathVariable String campaignId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        Instant start = startTime.toInstant(ZoneOffset.UTC);
        Instant end = endTime.toInstant(ZoneOffset.UTC);
        
        CampaignPerformanceDto performance = analyticsService.getCampaignPerformance(campaignId, start, end);
        return ResponseEntity.ok(performance);
    }
    
    @GetMapping("/creatives/performance")
    public ResponseEntity<List<CreativePerformanceDto>> getCreativePerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        Instant start = startDate.toInstant(ZoneOffset.UTC);
        Instant end = endDate.toInstant(ZoneOffset.UTC);
        
        List<CreativePerformanceDto> performance = analyticsService.getCreativePerformance(start, end);
        return ResponseEntity.ok(performance);
    }
}

// ============================================================================
// SCYLLA DB CONFIGURATION
// ============================================================================

// ScyllaDBConfig.java
package com.adserver.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
public class ScyllaDBConfig {

    @Value("${scylla.contact-points:localhost}")
    private String contactPoints;
    
    @Value("${scylla.port:9042}")
    private int port;
    
    @Value("${scylla.datacenter:datacenter1}")
    private String datacenter;
    
    @Value("${scylla.keyspace:publisher_analytics}")
    private String keyspace;
    
    @Bean
    public CqlSession scyllaSession() {
        return CqlSession.builder()
                .addContactPoint(new InetSocketAddress(contactPoints, port))
                .withLocalDatacenter(datacenter)
                .withKeyspace(keyspace)
                .build();
    }
}

```
// ============================================================================
// POM.XML DEPENDENCIES
// ============================================================================

```yaml
* <!-- ScyllaDB Driver -->
* <dependency>
*     <groupId>com.datastax.oss</groupId>
*     <artifactId>java-driver-core</artifactId>
*     <version>4.17.0</version>
* </dependency>
*
* <!-- Apache Flink (for stream processing) -->
* <dependency>
*     <groupId>org.apache.flink</groupId>
*     <artifactId>flink-streaming-java</artifactId>
*     <version>1.18.0</version>
* </dependency>
*
* <dependency>
*     <groupId>org.apache.flink</groupId>
*     <artifactId>flink-connector-kafka</artifactId>
*     <version>3.0.1-1.18</version>
* </dependency>
*/

```
```yaml


* Add to application.yml:

* scylla:
*   contact-points: localhost
*   port: 9042
*   datacenter: datacenter1
*   keyspace: publisher_analytics  # or advertiser_analytics
```