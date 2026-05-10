Great question! Let me break down AdTech terminology and the complete ecosystem flow.

---
**At Zee scale, our platform handled ~100K QPS on average, with burst traffic up to ~700–800K QPS during synchronized events.
Ad-decision traffic itself was a subset of that, peaking around 200–300K QPS.**

## **Part 1: Essential AdTech Keywords/Acronyms**

### **Pricing Models**
- **CPM** (Cost Per Mille/Thousand): Advertiser pays per 1000 impressions
- **CPC** (Cost Per Click): Pay only when user clicks ad
- **CPA** (Cost Per Action/Acquisition): Pay when user completes action (signup, purchase)
- **CPV** (Cost Per View): For video ads, pay per view
- **CPI** (Cost Per Install): For app install campaigns
- **eCPM** (Effective CPM): Revenue per 1000 impressions regardless of pricing model
    - Formula: `(Total Revenue / Total Impressions) × 1000`

### **Performance Metrics**
- **CTR** (Click-Through Rate): `(Clicks / Impressions) × 100`
- **CVR** (Conversion Rate): `(Conversions / Clicks) × 100`
- **ROI** (Return on Investment): Revenue vs spend
- **ROAS** (Return on Ad Spend): `Revenue / Ad Spend`
- **VTR** (View-Through Rate): For video completion
- **Viewability**: Percentage of ad actually seen by users (MRC standard: 50% pixels visible for 1 sec)

### **Auction & Bidding**
- **RTB** (Real-Time Bidding): Automated auction for each ad impression in milliseconds
- **Header Bidding**: Publishers let multiple ad exchanges bid simultaneously before ad server call
- **Waterfall**: Sequential auction (deprecated, replaced by header bidding)
- **Floor Price**: Minimum bid price publisher will accept
- **First Price Auction**: Highest bidder pays their bid
- **Second Price Auction**: Highest bidder pays second-highest bid + $0.01
- **Win Rate**: Percentage of bids won vs total bids placed

### **Platform Types**
- **DSP** (Demand-Side Platform): Tool for advertisers to buy ads (e.g., Google DV360, The Trade Desk)
- **SSP** (Supply-Side Platform): Tool for publishers to sell inventory (e.g., Google Ad Manager, Magnite)
- **DMP** (Data Management Platform): Collects and manages audience data
- **CDP** (Customer Data Platform): First-party customer data management
- **Ad Exchange**: Marketplace connecting DSPs and SSPs (e.g., Google AdX, OpenX)
- **Ad Network**: Aggregates inventory from publishers, sells to advertisers
- **Ad Server**: Serves ads, tracks performance (e.g., Google Ad Manager, Sizmek)

### **Inventory & Formats**
- **Display**: Banner ads (300×250, 728×90, etc.)
- **Video**: In-stream (pre-roll, mid-roll, post-roll), out-stream
- **Native**: Ads matching content format
- **CTV/OTT** (Connected TV/Over-The-Top): Streaming TV ads
- **Audio**: Podcast, streaming audio ads
- **Interstitial**: Full-screen ads between content
- **Rewarded**: User gets reward for watching (common in games)

### **Targeting**
- **Contextual**: Based on page content (privacy-friendly)
- **Behavioral**: Based on user browsing history
- **Retargeting/Remarketing**: Show ads to users who visited before
- **Lookalike**: Target users similar to existing customers
- **Geo-targeting**: Location-based
- **Demographic**: Age, gender, income
- **Affinity**: Interest-based segments

### **Measurement & Attribution**
- **Impression**: Ad displayed
- **Click**: User clicks ad
- **Conversion**: User completes goal action
- **Attribution Window**: Time period to credit conversion (7-day, 30-day)
- **Last-Click Attribution**: Credit last ad clicked
- **Multi-Touch Attribution (MTA)**: Credit multiple touchpoints
- **Incrementality**: Lift caused by ads vs organic
- **Brand Lift**: Increase in brand awareness/consideration

### **Privacy & Identity**
- **Cookie**: Browser identifier for tracking
- **Third-Party Cookie**: Cross-site tracking (being deprecated)
- **First-Party Cookie**: Same-site tracking
- **UID2/ID5**: Alternative identity solutions post-cookie
- **FLoC/Topics** (Google): Privacy sandbox initiatives
- **GDPR** (General Data Protection Regulation): EU privacy law
- **CCPA/CPRA** (California Consumer Privacy Act): California privacy law
- **Consent Management Platform (CMP)**: Manages user consent
- **PII** (Personally Identifiable Information): Data that identifies individual

### **Fraud & Quality**
- **IVT** (Invalid Traffic): Bot traffic, fraud
- **SIVT** (Sophisticated IVT): Advanced fraud
- **Brand Safety**: Avoiding ads on harmful content
- **Viewability**: MRC standard (50% pixels, 1 sec display / 2 sec video)
- **Ad Verification**: Third-party validation (IAS, DoubleVerify, MOAT)
- **Click Fraud**: Fake clicks to drain budgets
- **Domain Spoofing**: Pretending to be premium publisher

### **Technical Protocols**
- **OpenRTB**: Standard protocol for RTB communication
- **VAST** (Video Ad Serving Template): XML for video ads
- **VPAID** (Video Player-Ad Interface Definition): Interactive video ads
- **MRAID** (Mobile Rich Media Ad Interface): Mobile ad standard
- **Prebid**: Open-source header bidding wrapper
- **AMP** (Accelerated Mobile Pages): Fast mobile pages with ads

### **Programmatic Types**
- **Open Auction**: Anyone can bid
- **Private Marketplace (PMP)**: Invite-only auction
- **Programmatic Guaranteed (PG)**: Automated direct deal, fixed price
- **Preferred Deal**: First look at inventory, fixed price, no guarantee

### **Revenue/Business**
- **Fill Rate**: `(Impressions Served / Ad Requests) × 100`
- **Ad Load**: Number of ads per page
- **Time-to-Fill**: How fast ad is served
- **Yield**: Revenue optimization
- **Discrepancy**: Difference in reporting between systems
- **Passback**: When no ad fills, request sent to backup source

---

## **Part 2: AdTech Ecosystem Flow**

Let me explain how a single ad impression flows through the ecosystem:

### **The Complete Flow (Step-by-Step)**

```
USER VISITS WEBSITE
       ↓
[1] Publisher Ad Server Request
       ↓
[2] Header Bidding (Parallel Auctions)
       ↓
[3] Ad Server Decision
       ↓
[4] Real-Time Bidding (RTB)
       ↓
[5] Ad Delivery
       ↓
[6] Tracking & Attribution
       ↓
[7] Billing & Reporting
```

---

### **Detailed Flow with Components**

#### **Step 1: User Visits Publisher Website**
```
User → Publisher Website (e.g., News site, blog)
```
- User loads webpage
- Page contains ad slots (div tags with ad placement code)
- JavaScript tag triggers ad request

---

#### **Step 2: Header Bidding (Pre-Auction)**
```
Publisher's Prebid.js
    ↓
Calls multiple SSPs simultaneously
    ↓
SSP 1 (e.g., Magnite)
SSP 2 (e.g., PubMatic)
SSP 3 (e.g., Index Exchange)
    ↓
Each SSP runs mini-auction with connected DSPs
    ↓
Returns bids to publisher
```

**What happens:**
- Publishers use **Prebid.js** (header bidding wrapper)
- Sends bid request to 5-10 SSPs at once (parallel)
- Each SSP has relationships with multiple DSPs
- SSPs ask DSPs: "Want to bid on this impression?"
- All bids return to publisher in ~200-300ms

**Bid Request Contains:**
- User info (cookies, device, location)
- Page context (URL, keywords, content category)
- Ad slot size (300×250, 728×90)
- Floor price

---

#### **Step 3: Ad Server Decision (Primary Auction)**
```
Publisher Ad Server (e.g., Google Ad Manager)
    ↓
Compares:
- Direct sold campaigns (guaranteed)
- Header bidding bids
- Ad network bids
    ↓
Selects highest paying option
```

**Decision hierarchy:**
1. **Sponsorship/Direct deals** (if exist, highest priority)
2. **Programmatic Guaranteed** (PG deals)
3. **Private Marketplace** (PMP) bids from header bidding
4. **Open Auction** bids
5. **House ads** (fallback if nothing fills)

---

#### **Step 4: Real-Time Bidding (If no header bidding winner)**
```
Ad Server → Ad Exchange (e.g., Google AdX)
    ↓
Ad Exchange broadcasts to DSPs
    ↓
DSP 1 (The Trade Desk)
DSP 2 (Google DV360)
DSP 3 (Amazon DSP)
    ↓
Each DSP:
  - Checks targeting rules
  - Consults DMP for user data
  - Runs ML model for bid price
  - Submits bid
    ↓
Ad Exchange runs auction (first/second price)
    ↓
Winner pays, gets ad slot
```

**Timing:** All happens in **~100 milliseconds**

**DSP Decision Process:**
```
Bid Request arrives
    ↓
Is user in target audience? (age, location, interests)
    ↓ YES
Is budget available for campaign?
    ↓ YES
What's predicted CTR/CVR for this user? (ML model)
    ↓
Calculate bid: eCPA × predicted CVR = Bid Price
    ↓
Submit bid to exchange
```

---

#### **Step 5: Ad Delivery**
```
Winning DSP → Ad Creative URL
    ↓
Publisher Ad Server fetches creative
    ↓
Ad renders on user's browser
    ↓
Tracking pixel fires (impression counted)
```

**What gets delivered:**
- **Display ad**: Image + click URL
- **Video ad**: VAST XML pointing to video file
- **Native ad**: Headline + image + description

**Impression Tracking:**
- Publisher counts impression
- DSP counts impression
- Third-party verification (IAS, DoubleVerify) counts
- Discrepancy resolution later

---

#### **Step 6: User Interaction & Tracking**
```
User sees ad (Viewability pixel checks if 50% visible)
    ↓
User clicks ad
    ↓
Click tracker fires (multiple redirects)
    ↓
User lands on advertiser website
    ↓
User converts (purchase, signup)
    ↓
Conversion pixel fires
```

**Tracking chain:**
```
Ad Click
  → DSP tracker (records click)
  → Ad verification tracker
  → Advertiser's website
  → Conversion (tracked by advertiser pixel)
```

---

#### **Step 7: Attribution & Billing**
```
End of day: Systems reconcile
    ↓
Publisher: How many impressions served?
DSP: How many clicks/conversions?
    ↓
Attribution model determines credit
    ↓
Billing calculations
    ↓
Reports generated
```

**Money flow:**
```
Advertiser pays DSP: $10 CPM
    ↓
DSP takes cut: $2 (20%)
    ↓
Ad Exchange takes cut: $1 (10%)
    ↓
SSP takes cut: $0.50 (5%)
    ↓
Publisher receives: $6.50 CPM (65%)
```

---

## **Key Player Ecosystem Map**

```
DEMAND SIDE (Advertisers)
    ↓
Advertiser (Nike, Amazon)
    ↓
Agency Trading Desk
    ↓
DSP (The Trade Desk, Google DV360)
    ↓
├─ DMP (Oracle, Lotame) [Audience data]
├─ Ad Verification (IAS, DoubleVerify)
└─ Attribution Platform (Adjust, AppsFlyer)
    ↓
━━━━━━━ AD EXCHANGE ━━━━━━━
    ↓
SSP (Magnite, PubMatic)
    ↓
Publisher (CNN, ESPN)
    ↓
SUPPLY SIDE (Publishers)
```

---

## **Common Integration Patterns**

### **Pattern 1: Direct Deal**
```
Advertiser → directly contacts → Publisher
    ↓
Agreement on price, volume
    ↓
Campaign setup in Publisher Ad Server
    ↓
No auction, guaranteed delivery
```

### **Pattern 2: Programmatic Guaranteed**
```
Advertiser → DSP → sends deal ID → SSP → Publisher
    ↓
Fixed price, automated delivery
    ↓
No auction, but uses programmatic pipes
```

### **Pattern 3: Open RTB**
```
Any advertiser can bid
    ↓
Real-time auction every impression
    ↓
Highest bid wins
```

---

## **Modern Trends (What's changing)**

### **1. Retail Media Networks (Target's Play)**
```
Retailer (Target, Walmart, Amazon)
    ↓
Own Ad Platform (Roundel for Target)
    ↓
First-party purchase data (very valuable)
    ↓
Brands buy ads on retailer properties
    ↓
Closed-loop attribution (ad → purchase)
```

**Why it matters:**
- Retailers have purchase data (not just browsing)
- Privacy-compliant (first-party data)
- Growing 25%+ yearly

### **2. Post-Cookie World**
```
Third-party cookies dying (2024-2025)
    ↓
Alternatives:
  - Contextual targeting (no user tracking)
  - First-party data (own website visitors)
  - Universal IDs (UID2, ID5)
  - Google Topics API
  - Publisher cohorts
```

### **3. CTV/OTT Growth**
```
User watches Netflix/Hulu/Disney+
    ↓
Ad-supported tier
    ↓
Programmatic video ads
    ↓
Same RTB flow as display
```

---

## **Interview Pro Tips**

When discussing AdTech flow, emphasize:

✅ **"At Zee, our ad server sat between SSP and publisher..."**
- Shows you know where components fit

✅ **"We optimized for sub-100ms response time because RTB timeouts at 120ms..."**
- Shows understanding of real-world constraints

✅ **"We handled frequency capping at ad server level, not DSP, because..."**
- Shows architectural thinking

✅ **"Post-cookie, we're moving to contextual + first-party data..."**
- Shows you're current with industry
# AdTech Platform – Pod Sizing & HPA Configuration

> **Revised for 5 Million Transactions Per Minute (TPM)**

---

## Traffic Baseline

| Metric | Value |
|---|---|
| **Total Platform TPM** | 5,000,000 |
| **Total QPS** | ~83,333 (5M ÷ 60) |
| **Peak QPS (1.5× headroom)** | ~125,000 |

All pod counts are calculated for **peak QPS** with HPA target at **60–70% CPU / QPS**.

---

## Traffic Distribution Across Services

The 5M TPM is split across services based on real-world AdTech traffic patterns:

| Service | % of Traffic | TPM | Peak QPS |
|---|---|---|---|
| **Ad Decision (Ad Server)** | 30% | 1,500,000 | ~37,500 |
| **Impression Tracking** | 28% | 1,400,000 | ~35,000 |
| **Analytics / Event Ingest** | 20% | 1,000,000 | ~25,000 |
| **Fraud / Validation** | 10% | 500,000 | ~12,500 |
| **Click Tracking** | 6% | 300,000 | ~7,500 |
| **Conversion / Postback** | 3% | 150,000 | ~3,750 |
| **Campaign / Metadata API** | 2% | 100,000 | ~2,500 |
| **Reporting / Dashboard** | 1% | 50,000 | ~1,250 |
| **Total** | **100%** | **5,000,000** | **~125,000** |

> **Why this split?** Ad decisions trigger impressions (1:1 ratio roughly). Clicks are ~10% of impressions. Conversions are ~2–3% of clicks. Analytics captures all events. Fraud checks run on a subset.

---

## Key Assumptions

- Pod size: **2 vCPU / 4–6 GB RAM**, Spring Boot + Netty/Undertow
- HPA target: **60–70% CPU**, or QPS-based
- Numbers are **per region** (peak load)
- Avg processing time: Ad Decision 10–20 ms | Tracking 1–3 ms

---

## Core AdTech Services – Pod Count

### 1️⃣ Ad Decision Service (Ad Server)

| Metric | Value |
|---|---|
| Peak QPS | ~37,500 |
| QPS per pod | 1,200 – 1,500 |
| **Pods required** | **25 – 32** |
| Why | Business logic, ML scoring, budget checks, cache lookups |

> Most compute-intensive service despite lower pod count vs original — because QPS is calibrated to 5M TPM.

---

### 2️⃣ Impression Tracking Service

| Metric | Value |
|---|---|
| Peak QPS | ~35,000 |
| QPS per pod | 5,000 – 7,000 |
| **Pods required** | **5 – 7** |
| Why | Write-only, async fire-and-forget to Kafka |

---

### 3️⃣ Analytics / Event Ingestion Service

| Metric | Value |
|---|---|
| Peak QPS | ~25,000 |
| QPS per pod | 6,000 – 8,000 |
| **Pods required** | **4 – 5** |
| Why | Kafka producer, lightweight aggregation |

---

### 4️⃣ Fraud / Validation Service

| Metric | Value |
|---|---|
| Peak QPS | ~12,500 |
| QPS per pod | 2,000 – 2,500 |
| **Pods required** | **5 – 7** |
| Why | CPU-heavy rule evaluation, IP reputation checks |

---

### 5️⃣ Click Tracking Service

| Metric | Value |
|---|---|
| Peak QPS | ~7,500 |
| QPS per pod | 4,000 – 5,000 |
| **Pods required** | **2 – 3** |
| Why | Simple async writes, Redis dedup check |

---

### 6️⃣ Conversion / Postback Service

| Metric | Value |
|---|---|
| Peak QPS | ~3,750 |
| QPS per pod | 1,500 – 2,000 |
| **Pods required** | **2 – 3** |
| Why | External HTTP callbacks to advertisers, latency-tolerant |

---

## Supporting Services

### 7️⃣ Campaign / Metadata API

| Metric | Value |
|---|---|
| Peak QPS | ~2,500 |
| QPS per pod | 1,000 – 1,500 |
| **Pods required** | **2 – 3** |
| Why | Admin CRUD, heavy cache (Redis), low write frequency |

---

### 8️⃣ Reporting / Dashboard Backend

| Metric | Value |
|---|---|
| Peak QPS | ~1,250 |
| QPS per pod | 500 – 800 |
| **Pods required** | **2 – 3** |
| Why | Complex aggregations from OLAP/ClickHouse |

---

## 🔢 Summary Table

| Service | % Traffic | Peak QPS | QPS/Pod | Pods (Peak) |
|---|---|---|---|---|
| **Ad Decision** | 30% | 37,500 | 1,200–1,500 | **25–32** |
| Impression Tracking | 28% | 35,000 | 5,000–7,000 | **5–7** |
| Analytics Ingest | 20% | 25,000 | 6,000–8,000 | **4–5** |
| Fraud / Validation | 10% | 12,500 | 2,000–2,500 | **5–7** |
| Click Tracking | 6% | 7,500 | 4,000–5,000 | **2–3** |
| Conversion / Postback | 3% | 3,750 | 1,500–2,000 | **2–3** |
| Campaign / Metadata | 2% | 2,500 | 1,000–1,500 | **2–3** |
| Reporting API | 1% | 1,250 | 500–800 | **2–3** |
| **Total** | **100%** | **~125,000** | — | **~47–63 pods/region** |

---

## HPA Configurations

### 1. Ad Decision – CPU + QPS Combined (Recommended)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ad-decision-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ad-decision
  minReplicas: 10
  maxReplicas: 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 65
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "1200"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
      - type: Pods
        value: 5
        periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 120
```

---

### 2. Impression Tracking – QPS-Based

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: impression-tracking-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: impression-tracking
  minReplicas: 3
  maxReplicas: 12
  metrics:
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "5000"
```

---

### 3. Analytics Ingest – QPS-Based

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: analytics-ingest-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: analytics-ingest
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "6000"
```

---

### 4. Fraud / Validation – CPU-Based (Compute-Heavy)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: fraud-validation-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: fraud-validation
  minReplicas: 3
  maxReplicas: 12
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 60
```

---

### 5. Click Tracking – QPS-Based

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: click-tracking-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: click-tracking
  minReplicas: 2
  maxReplicas: 6
  metrics:
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "4000"
```

---

### 6. Conversion / Postback – CPU-Based (IO-Tolerant)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: conversion-postback-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: conversion-postback
  minReplicas: 2
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

---

### 7. Campaign / Metadata API – CPU-Based

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: campaign-metadata-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: campaign-metadata
  minReplicas: 2
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 65
```

---

### 8. Reporting / Dashboard – CPU-Based

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: reporting-dashboard-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: reporting-dashboard
  minReplicas: 2
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 65
```

---

## HPA Strategy by Service Type

| Service | Metric | Target | Reason |
|---|---|---|---|
| Ad Decision | QPS + CPU | 1,200/pod + 65% | Bursty, latency-sensitive |
| Impression Tracking | QPS | 5,000/pod | IO-bound, async |
| Analytics Ingest | QPS | 6,000/pod | Kafka producer, lightweight |
| Fraud / Validation | CPU | 60% | Rule engine is CPU-heavy |
| Click Tracking | QPS | 4,000/pod | Traffic spikes, IO-bound |
| Conversion / Postback | CPU | 70% | Low volume, latency-tolerant |
| Campaign / Metadata | CPU | 65% | Cache-heavy, low traffic |
| Reporting API | CPU | 65% | OLAP reads, low concurrency |

---

## Important Notes (Interview Signal)

- Pod counts above are **peak numbers** — normal load runs at 40–60% of this
- **Autoscaling absorbs bursts** — minReplicas keep baseline warm
- **5M TPM = ~83K avg QPS = ~125K peak QPS** with 1.5× safety headroom
- Ad Decision dominates compute cost despite ~30% traffic share due to complex logic
- Tracking/ingest services handle high QPS efficiently via async Kafka writes

---

## Interview-Ready One-Liner

> "At 5 million transactions per minute, our AdTech stack runs roughly 50–65 Spring Boot pods per region at peak, with Ad Decision taking the bulk of pods due to ML scoring and budget checks, while high-QPS tracking services stay lean thanks to async Kafka writes and minimal business logic."

**Key Differences: Ad Server, SSP, DSP**

**1. Ownership:**
- **Publisher's Ad Server**: Owned by publisher (manages their site)
- **SSP**: Used by publisher (sells their inventory)
- **DSP**: Used by advertiser (buys inventory)
- **Advertiser's Ad Server**: Owned by advertiser (tracks their campaigns)

**2. Primary Job:**
- **Publisher's Ad Server**: Delivers ads to users on publisher's website
- **SSP**: Runs auctions to sell publisher's ad space
- **DSP**: Bids in auctions to buy ad space
- **Advertiser's Ad Server**: Stores creatives, provides tracking URLs, verifies delivery

**3. What They Configure:**
- **Publisher's Ad Server**: Frequency caps, ad rotation, slot management
- **SSP**: Floor prices, demand partners, block lists
- **DSP**: Targeting criteria, bid amounts, budgets
- **Advertiser's Ad Server**: Creative versions, tracking pixels, attribution

**4. Communication Flow:**
```
User visits site → Publisher Ad Server → SSP (auction) → DSP (bids) 
→ DSP wins → Publisher Ad Server gets redirect URL 
→ Calls Advertiser Ad Server → Creative delivered
```

**5. Why Both Ad Servers Exist:**
- **Publisher's**: Controls what shows on THEIR site
- **Advertiser's**: Tracks performance across ALL sites independently

**Simple Analogy:**
- Publisher Ad Server = Theater screen (shows the movie)
- SSP = Ticket marketplace (sells seats)
- DSP = Ticket buyer (buys seats)
- Advertiser Ad Server = Movie studio (provides film, tracks ticket sales across all theaters)
---
## **Quick Reference Cheat Sheet**

**Money flow:** Advertiser → Agency → DSP → Exchange → SSP → Publisher

**Data flow:** User → Publisher → SSP → Exchange → DSP → Bid decision

**Time:** Total RTB cycle = 80-120ms (hard timeout)

**Key metrics:** CPM (price), CTR (engagement), CVR (conversion), Viewability (quality)

**Privacy:** GDPR/CCPA compliance, cookie deprecation, contextual targeting

---

Want me to explain any specific component in more detail? Or do a mock scenario like "User clicks Nike ad on ESPN.com - trace the full flow"?