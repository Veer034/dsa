# Design Uber / Lyft — Ride Hailing
> Tests: geospatial indexing, real-time location updates, matching algorithms, surge pricing, ETA.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| Scope: just matching or full trip lifecycle? | Payment, ratings add scope significantly |
| How many cities / global? | Geo-sharding, timezone handling |
| Driver location update frequency? | Every 4 seconds = massive write throughput |
| Real-time ETA or approximate? | Map API integration vs internal routing |
| Surge pricing needed? | Dynamic pricing algorithm scope |
| Scheduled rides? | Future booking adds state complexity |
| Multiple vehicle types? | UberPool / UberX / UberBlack matching logic |

**Typical answer:** Global, 5M drivers, 20M riders, driver sends location every 4s, matching + ETA + surge needed, multiple vehicle types.

---

## Scale Estimation

```
Driver location updates:
  5M active drivers * 1 update/4s = 1.25M writes/sec  ← This is the hardest part

Ride requests:
  Peak: 200K requests/min → ~3,300 matching requests/sec

Geospatial queries:
  For each ride request: "find drivers within 2km" → geospatial index lookup
  Must be < 500ms to feel real-time
```

---

## HLD Diagram

```
         ┌──────────────┐         ┌──────────────┐
         │  Rider App   │         │  Driver App  │
         └──────┬───────┘         └──────┬───────┘
                │                        │ GPS update every 4s
                │ HTTPS                  │ HTTPS / WebSocket
         ┌──────▼────────────────────────▼───────┐
         │         API Gateway [NGINX]            │
         └──────┬────────────────────────┬────────┘
                │                        │
  ┌─────────────▼──────┐    ┌────────────▼────────────┐
  │   Ride Request Svc │    │   Location Update Svc   │
  │   [Java/SBoot]     │    │   [Java/SBoot]          │
  └─────────────┬──────┘    └────────────┬────────────┘
                │                        │
  ┌─────────────▼──────┐    ┌────────────▼────────────┐
  │  Matching Service  │    │   {Redis GeoSet}         │
  │  [Java/SBoot]      │◄───│   driver_locations:city  │
  └─────────────┬──────┘    │   (lat, lng, driverId)   │
                │           └─────────────────────────┘
  ┌─────────────▼──────┐
  │  <Kafka Topics>    │  ride.requested
  │                    │  ride.matched
  │                    │  driver.location
  └─────────────┬──────┘
                │
  ┌─────────────▼────────────────────────────────┐
  │         Trip Management Service              │
  │  [Java/SBoot]                               │
  │  State machine: REQUESTED→MATCHED→PICKUP    │
  │                →ENROUTE→COMPLETED→PAID      │
  └─────────────┬───────────────┬───────────────┘
                │               │
  ┌─────────────▼──┐   ┌────────▼───────────┐
  │  ((MySQL))     │   │  Surge Pricing Svc │
  │  trips, users  │   │  [Python]          │
  │  payments      │   └────────┬───────────┘
  └────────────────┘            │
                       ┌────────▼───────────┐
                       │    Druid / Redis   │
                       │  demand heatmap    │
                       └────────────────────┘
```

---

## Key Design Decisions

### 1. Driver Location Storage — Redis Geo

```
This is the centerpiece of the system.

Redis GEOADD — stores driver locations as sorted set with geohash score:

  GEOADD driver_locations:bangalore <lng> <lat> <driverId>
  GEORADIUS driver_locations:bangalore <lng> <lat> 2 km ASC COUNT 10

Why Redis Geo?
  → O(N+log M) for GEORADIUS — fast enough for real-time matching
  → In-memory — sub-millisecond reads
  → Supports expiry via key TTL (remove stale drivers)

Sharding by city:
  → Key per city: driver_locations:{cityId}
  → Keeps each geo-set manageable (<100K drivers per city)
  → Route by city on API gateway level

Update frequency:
  → Driver sends GPS every 4 seconds
  → We update Redis on every write
  → 5M drivers * 1 update/4s = 1.25M writes/sec to Redis
  → Use Redis Cluster: 10+ shards, each handling ~125K writes/sec (Redis handles 1M+ ops/sec per node)

Driver offline detection:
  → Each location update sets: SET driver_active:{driverId} EX 30
  → If no update in 30s → key expires → driver considered offline
  → Matching service checks this before dispatching
```

### 2. Matching Algorithm

```
Simple version (what you describe first):
  1. Receive ride request { riderId, pickupLat, pickupLng, vehicleType }
  2. GEORADIUS to find 10 nearest available drivers (same vehicleType, not on a trip)
  3. Calculate real-time ETA for each candidate (Google Maps API or internal routing)
  4. Pick driver with lowest ETA
  5. Send offer to driver (WebSocket / FCM push)
  6. Driver accepts within 10s → matched. Else → try next candidate.

Advanced version (mention if asked):
  → Score = α * distance + β * driver_rating + γ * acceptance_rate
  → Real-time demand supply prediction to pre-position drivers
  → Batched matching for shared rides (UberPool): Hungarian algorithm / assignment problem

Driver availability in matching:
  → Redis Set: available_drivers:{cityId}:{vehicleType}
  → On trip start → SREM (remove from available set)
  → On trip end → SADD (add back)
  → GEORADIUS + check available set → intersection
```

### 3. Real-time Driver Tracking (Rider sees driver moving)

```
Rider needs live updates of driver position after matching.

Options:
  A. Polling: client polls every 3s → simple but chatty
  B. WebSocket: persistent connection → true real-time
  C. Server-Sent Events (SSE): one-directional push, simpler than WS

Implementation:
  → Driver sends GPS to Location Service → writes to Redis
  → Kafka: driver.location topic → partitioned by driverId
  → Trip Tracking Service subscribes (Kafka consumer per active trip)
  → Pushes to rider via WebSocket / SSE

  OR simpler:
  → Rider polls GET /trip/{tripId}/driver-location every 3s
  → Server reads from Redis Geo → returns current position
  → Simple but adds 3s latency max — acceptable for most use cases
```

### 4. Trip State Machine

```
REQUESTED → (matching) → MATCHED → (driver en route to pickup) →
DRIVER_ARRIVED → (rider boards) → IN_PROGRESS → (drop-off) →
COMPLETED → (payment) → PAID

State stored in:
  → Redis: active_trip:{tripId} → current state (fast reads during trip)
  → MySQL: trips table (durable record, updated on state transitions)

State transitions via Kafka events:
  ride.matched → trip enters MATCHED state
  driver.arrived → state = DRIVER_ARRIVED, notify rider
  trip.started → state = IN_PROGRESS, start tracking
  trip.completed → state = COMPLETED, trigger payment
```

### 5. Surge Pricing

```
Demand/Supply imbalance detection:
  → Count ride requests per hexagonal grid cell (H3 geohex) in last 5 minutes
  → Count available drivers per grid cell
  → surge_ratio = demand / supply

  → surge_ratio > 2.0 → 1.5x price
  → surge_ratio > 3.0 → 2.0x price
  → surge_ratio > 5.0 → 2.5x price (capped)

Real-time computation:
  → Kafka: ride.requested events → Flink/Druid streaming aggregation
  → Aggregate per H3 cell, 1-minute tumbling window
  → Write surge multiplier to Redis: surge:{h3cellId} → 1.5 (TTL 5 min)
  → Pricing Service reads this on each ride request

H3 (Uber's hexagonal grid library):
  → Each H3 hex at resolution 8 ≈ 0.74 km² area
  → Rider's pickup lat/lng → H3 cell ID → lookup surge
  → Hexagons tile without gaps, better than square grids
```

### 6. ETA Calculation

```
Options:
  A. Google Maps Distance Matrix API → accurate, expensive, rate limited
  B. Internal routing engine (OSRM / Valhalla) → self-hosted, cheaper, fast
  C. Historical ETA model → ML model trained on past trip data

What you say:
  "We'd use a precomputed road graph (OSRM or Valhalla) for initial ETA,
   enhanced by a ML model trained on historical trip times factoring in
   time of day, weather, and known congestion patterns."

Internal routing:
  → Road network loaded into memory (city-level graph)
  → Dijkstra / A* for shortest path
  → Updated with real-time speed data from ongoing trips (Kafka → graph update)
```

---

## Database Schema

```sql
CREATE TABLE trips (
    trip_id       BIGINT PRIMARY KEY,        -- Snowflake ID
    rider_id      BIGINT NOT NULL,
    driver_id     BIGINT,
    pickup_lat    DECIMAL(9,6),
    pickup_lng    DECIMAL(9,6),
    dropoff_lat   DECIMAL(9,6),
    dropoff_lng   DECIMAL(9,6),
    status        ENUM('REQUESTED','MATCHED','IN_PROGRESS','COMPLETED','CANCELLED'),
    vehicle_type  VARCHAR(20),
    surge_mult    DECIMAL(3,1) DEFAULT 1.0,
    estimated_fare DECIMAL(10,2),
    final_fare    DECIMAL(10,2),
    requested_at  DATETIME,
    matched_at    DATETIME,
    completed_at  DATETIME,
    INDEX idx_rider (rider_id, requested_at),
    INDEX idx_driver (driver_id, requested_at),
    INDEX idx_status (status)
);

CREATE TABLE drivers (
    driver_id     BIGINT PRIMARY KEY,
    name          VARCHAR(100),
    vehicle_type  VARCHAR(20),
    rating        DECIMAL(3,2),
    is_active     BOOLEAN DEFAULT TRUE,
    city_id       INT,
    INDEX idx_city_active (city_id, is_active)
);
```

---

## Code Skeleton — Matching Service

```java
@Service
public class RideMatchingService {

    public MatchResult matchRide(RideRequest request) {
        
        // 1. Find nearby available drivers
        List<DriverCandidate> nearby = redisGeoService.findNearbyDrivers(
            request.getPickupLat(), request.getPickupLng(),
            2.0,  // 2 km radius
            request.getVehicleType(),
            10    // top 10 candidates
        );

        if (nearby.isEmpty()) return MatchResult.noDriversAvailable();

        // 2. Rank by ETA (call routing service)
        nearby.sort(Comparator.comparingInt(d -> routingService.getETA(
            d.getCurrentLat(), d.getCurrentLng(),
            request.getPickupLat(), request.getPickupLng()
        )));

        // 3. Offer to top driver, wait for accept
        for (DriverCandidate driver : nearby) {
            DriverResponse resp = offerService.sendOffer(driver, request, 10_000); // 10s timeout
            if (resp == DriverResponse.ACCEPTED) {
                markDriverUnavailable(driver.getDriverId());
                return MatchResult.matched(driver);
            }
        }
        return MatchResult.noMatch();
    }
}
```

---

## Interview Tips

- **Redis GEOADD/GEORADIUS** is the answer to "how do you find nearby drivers" — don't say MySQL with lat/lng columns.
- **H3 hexagonal grid** for surge pricing — dropping "Uber's H3 library" scores serious points.
- The **matching offer flow** (offer → 10s timeout → next driver) is what makes it real — mention it.
- Mention **city-level sharding** of the geo index — shows you understand hot-spot problems.
- For location updates at 1.25M writes/sec: mention Redis Cluster + async Kafka write for trip replay/audit.
- Bring up the **CAP theorem tradeoff**: for location data, we prefer availability over consistency — a slightly stale driver position is fine.
