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

---
Below is a **practical, interview-safe pod sizing** for a **Zee-scale AdTech platform**, assuming **all services are Spring Boot**, **stateless**, autoscaled on CPU + QPS.

I’ll base this on the **numbers we agreed**:

* **Peak total QPS**: ~700–800K
* **Peak ad-decision QPS**: ~200–300K

---

## Key assumptions (say this if asked)

* Pod: **2 vCPU / 4–6 GB RAM**
* Spring Boot + Netty / Undertow
* Avg processing time:

  * Ad decision: 10–20 ms
  * Tracking endpoints: 1–3 ms
* HPA target: ~60–70% CPU
* Numbers are **per region**

---

## Core AdTech services – pod count

### 1️⃣ Ad Decision Service (most expensive)

| Metric            | Value                                |
| ----------------- | ------------------------------------ |
| Peak QPS          | 200K – 300K                          |
| QPS per pod       | 1.5K – 2K                            |
| **Pods required** | **120 – 180**                        |
| Why               | Business logic, cache, budget checks |

> This is the **hardest service to scale**

---

### 2️⃣ Impression Tracking Service

| Metric            | Value                             |
| ----------------- | --------------------------------- |
| Peak QPS          | 150K – 200K                       |
| QPS per pod       | 5K – 8K                           |
| **Pods required** | **25 – 40**                       |
| Why               | Write-only, async fire-and-forget |

---

### 3️⃣ Click Tracking Service

| Metric            | Value                     |
| ----------------- | ------------------------- |
| Peak QPS          | 10K – 25K                 |
| QPS per pod       | 4K – 6K                   |
| **Pods required** | **4 – 6**                 |
| Why               | Low volume, simple writes |

---

### 4️⃣ Conversion / Postback Service

| Metric            | Value                                |
| ----------------- | ------------------------------------ |
| Peak QPS          | 2K – 5K                              |
| QPS per pod       | 2K                                   |
| **Pods required** | **2 – 3**                            |
| Why               | External callbacks, latency-tolerant |

---

### 5️⃣ Analytics / Event Ingestion Service

| Metric            | Value                         |
| ----------------- | ----------------------------- |
| Peak QPS          | 100K – 150K                   |
| QPS per pod       | 6K – 10K                      |
| **Pods required** | **15 – 25**                   |
| Why               | Kafka producer, minimal logic |

---

## Supporting AdTech services

### 6️⃣ Campaign / Metadata API

| Metric   | Value               |
| -------- | ------------------- |
| QPS      | <5K                 |
| **Pods** | **3 – 5**           |
| Why      | Admin + cache-heavy |

---

### 7️⃣ Fraud / Validation Service

| Metric      | Value           |
| ----------- | --------------- |
| QPS         | 30K – 60K       |
| QPS per pod | 2K – 3K         |
| **Pods**    | **15 – 20**     |
| Why         | CPU-heavy rules |

---

### 8️⃣ Reporting / Dashboard Backend

| Metric   | Value                   |
| -------- | ----------------------- |
| QPS      | <2K                     |
| **Pods** | **3 – 4**               |
| Why      | Reads from Redis / OLAP |

---

## 🔢 Summary table (what interviewers like)

| Service             | Pods (Peak)                  |
| ------------------- | ---------------------------- |
| **Ad Decision**     | **120 – 180**                |
| Impression Tracking | 25 – 40                      |
| Click Tracking      | 4 – 6                        |
| Conversion          | 2 – 3                        |
| Analytics Ingest    | 15 – 25                      |
| Fraud / Validation  | 15 – 20                      |
| Campaign / Metadata | 3 – 5                        |
| Reporting API       | 3 – 4                        |
| **Total**           | **~190 – 280 pods / region** |

---

## Important clarification (very senior signal)

* These are **peak numbers**
* Normal traffic runs at **40–60% of this**
* Autoscaling absorbs bursts
* No single service handles “all QPS”

---

## Interview-ready one-liner

> “At peak, a Zee-scale AdTech stack runs a few hundred Spring Boot pods per region, with the Ad Decision service dominating pod count due to strict latency and complex logic.”

Below is a **clear, production-style explanation** of **HPA rules using CPU vs QPS**, with **exact YAML examples** and **when to use which**.

---

## 1️⃣ CPU-based HPA (default, simple, reliable)

### When to use

* Spring Boot services
* CPU-bound or mixed workload
* You don’t want custom metrics infra

### How it works

HPA scales pods based on **average CPU utilization**.

---

### Example: CPU-based HPA (Ad Decision)

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
  minReplicas: 20
  maxReplicas: 200
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 65
```

### Meaning

* If avg CPU > **65%** → scale out
* If avg CPU < **65%** → scale in
* Simple, stable, widely used

---

### Pros / Cons

| Pros           | Cons                   |
| -------------- | ---------------------- |
| Easy to set    | Reacts after CPU rises |
| No extra infra | Not traffic-aware      |
| Stable         | Slower for bursts      |

---

## 2️⃣ QPS-based HPA (best for AdTech)

### When to use

* Traffic spikes (live events)
* IO-heavy services
* Ad decision / tracking endpoints

### Requirement

* Expose **QPS metric** (Prometheus / custom metrics API)

---

### Example: QPS-based HPA

Assume:

* One pod can safely handle **1500 QPS**

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
  minReplicas: 20
  maxReplicas: 250
  metrics:
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "1500"
```

### Meaning

* If avg QPS per pod > **1500** → scale out
* Scales **before CPU saturates**

---

## 3️⃣ Combined CPU + QPS (recommended)

This is **best practice** for AdTech.

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ad-decision-hpa
spec:
  minReplicas: 20
  maxReplicas: 250
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ad-decision
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
        averageValue: "1500"
```

### Behavior

* Scale if **either** CPU or QPS breaches
* Protects against:

  * CPU saturation
  * IO wait spikes

---

## 4️⃣ Why QPS beats CPU for Ad Decision

| Scenario              | CPU HPA | QPS HPA    |
| --------------------- | ------- | ---------- |
| SSAI burst            | ❌ late  | ✅ early    |
| IO wait spike         | ❌ blind | ✅ accurate |
| Low CPU, high latency | ❌       | ✅          |
| Predictable scaling   | ⚠️      | ✅          |

That’s why **AdTech teams prefer QPS-based HPA**.

---

## 5️⃣ Practical numbers (use these)

| Service             | Metric | Target           |
| ------------------- | ------ | ---------------- |
| Ad Decision         | QPS    | 1200–1500 / pod  |
| Impression tracking | QPS    | 6000–8000 / pod  |
| Analytics ingest    | QPS    | 8000–10000 / pod |
| Fraud service       | CPU    | 60%              |

---

## 6️⃣ Interview-ready explanation (use this)

> “We primarily scale ad-decision services on **QPS per pod**, with CPU as a safety net. CPU alone reacts too late for bursty traffic, while QPS gives us proactive scaling during live or synchronized events.”

---

## One-line takeaway

> **CPU keeps pods healthy; QPS keeps latency stable.**

If you want, I can also show:

* **Prometheus metric wiring**
* **HPA stabilization windows**
* **Pre-scale strategies for live events**

---

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