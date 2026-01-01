Below is the **modified and corrected version**, explicitly adding **ALL limit checks (campaign + user-level)** and **event-based completion**, while keeping your original structure intact.

---

## 🔷 End-to-End Architecture (Visual)

![Image](https://cdn.prod.website-files.com/67d134551b65a3fef7007145/68cbf52e238fce1f542c4f8f_65b1f9841cc487b85430c44d_RD%25202_result.webp)

![Image](https://www.watchingthat.com/wp-content/uploads/2022/03/video%20session%20flowchart%20%281%29.png)

---

## 1️⃣ Runtime call from Video Engineering

Video player makes **one single call**:

```json
POST /getAd
{
  "tagId": "VIDEO_PRE_ROLL_1",
  "user": { "age": 28, "gender": "M", "region": "IN", "userId": "u123" },
  "content": { "language": "Hindi", "category": "News" }
}
```

👉 Video team does **no ad logic**
👉 Only passes **facts**

---

## 2️⃣ Rule Engine (Policy Validation Layer)

**Purpose:** Decide *what kind of ads are allowed*

Checks:

* Age / child safety
* Gender rules
* Language match
* Ad-unit restrictions
* Legal & brand safety (kids, politics, alcohol)

**Output (constraints only):**

```json
{
  "allowedCategories": ["EDUCATIONAL", "INTERNAL_PROMO"],
  "maxDuration": 15,
  "monetizationAllowed": true
}
```

❌ No campaign logic
❌ No budget logic

---

## 3️⃣ Ad Decision Service (Selection + Enforcement Layer)

### Step A: Fetch candidates from Redis (DSP indexed)

```text
Key: adunit:VIDEO_PRE_ROLL_1
Value: [campaign1, campaign2, campaign3]
```

Redis contains:

* Campaign targeting
* Budgets
* Frequency limits
* Priority
* Status

---

### Step B: Filter (HARD checks)

For each candidate campaign:

```
✔ Allowed category (from Rule Engine)
✔ User targeting match
✔ Campaign status = ACTIVE
✔ Budget remaining (with buffer)
✔ User frequency caps (hour / day / month)
```

#### User-level frequency check (runtime)

Stored in Redis:

```
freq:user:{uid}:campaign:{cid}:hour
freq:user:{uid}:campaign:{cid}:day
freq:user:{uid}:campaign:{cid}:month
```

If **any cap exceeded** → campaign skipped.

---

### Step C: Near-limit protection (important)

Before selection:

```
if (delivered + safety_buffer >= limit)
→ skip campaign
→ publish CAMPAIGN_LIMIT_REACHED event
```

This prevents over-delivery.

---

### Step D: Rank & Select

Remaining candidates are ranked by:

1. Priority (editorial / promo / paid)
2. Frequency safety
3. Pacing (under-delivered preferred)
4. Weighted rotation

✅ **Exactly one ad is selected**

---

### Step E: Atomic updates (Ad Server responsibility)

On selection:

```
- Increment campaign counters
- Increment user frequency counters
- Write impression intent
```

All done atomically (Redis / Lua).

---

## 4️⃣ Ad Response sent to Video Engineering

```json
{
  "stream_url": "https://cdn.zee.com/ads/prime_ad/master.m3u8",

  "click_url":
    "https://ads.zee.com/click?cid=123&uid=u123",

  "tracking": {
    "impression":
      "https://ads.zee.com/track/impression",

    "quartiles": {
      "q25": "https://ads.zee.com/track/25",
      "q50": "https://ads.zee.com/track/50",
      "q75": "https://ads.zee.com/track/75",
      "q100":"https://ads.zee.com/track/100"
    },

    "error":
      "https://ads.zee.com/track/error"
  }
}
```

---

## 5️⃣ What Video Engineering does

### During playback

* Fire **impression** when playback starts
* Fire **25 / 50 / 75 / 100%** tracking beacons
* Fire **error beacon** on failure

### On click

* Open `click_url`
* Ad system:

  * Logs click
  * Redirects to advertiser landing page

---

## 6️⃣ DSP responsibility (offline only)

DSP is **not in runtime path**.

DSP configures:

* Campaign targeting rules
* User frequency caps
* Budgets & limits
* Creative metadata

DSP **publishes campaign state → Redis**

---

## 7️⃣ Campaign completion handling (important)

When limits are reached:

* Ad Server emits event:

```
CAMPAIGN_COMPLETED / CAMPAIGN_LIMIT_REACHED
```

* DSP consumes event
* Campaign marked **COMPLETED / PAUSED**
* Removed from Redis index

---

## 8️⃣ Why this design is correct

| Aspect                  | Reason                      |
| ----------------------- | --------------------------- |
| Rule Engine             | Central compliance & safety |
| Redis                   | Ultra-low latency state     |
| Ad Server enforcement   | Prevents over-delivery      |
| User-level caps         | Avoids ad fatigue           |
| Event-driven completion | Decoupled & safe            |
| Streaming manifests     | Mobile + ABR friendly       |

---

## 9️⃣ Interview-ready one-liner

> “DSP defines limits and targeting, but the ad server enforces campaign and per-user frequency caps at runtime using Redis counters. When limits are near exhaustion, the ad server skips the campaign and publishes completion events, while the video player handles streaming, clicks, and quartile tracking.”

If you want next:

* **Exact Redis Lua script**
* **Handling anonymous users**
* **Cross-device frequency capping**
* **Race conditions at high QPS**

Just say.
