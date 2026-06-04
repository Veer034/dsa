# LLD: Parking Lot System

> **Experience Level:** 10+ Years Java | Spring Boot · Kafka · Redis · MySQL · Elasticsearch · ScyllaDB · Druid

---

## 🔁 Clarifying Questions (You → Interviewer)

Ask these **before** designing anything. They signal seniority and scope awareness.

| # | Question | Why It Matters |
|---|----------|----------------|
| 1 | How many levels, entry/exit points, and total slots are we designing for? | Shapes data model and concurrency strategy |
| 2 | Do we need real-time slot availability or eventual consistency is fine? | Determines Redis vs DB-first strategy |
| 3 | Is this a single building or a distributed multi-location system? | Impacts whether we need distributed coordination |
| 4 | What vehicle types must be supported (motorcycle, car, bus/truck)? | Slot sizing and type hierarchy |
| 5 | Do we need billing/payment integration? | Out of scope for pure LLD but good to confirm |
| 6 | Should slot assignment be deterministic (closest to entry) or random? | Assignment algorithm complexity |
| 7 | Is high availability required (failover, no single point of failure)? | Data store and locking strategy |
| 8 | Multi-tenant (mall vs airport vs hospital)? | Configuration abstraction |

---

## 🔁 Expected Follow-up Questions (Interviewer → You)

Be ready to answer:

- "How do you handle concurrent slot booking at peak hours?"
- "What happens if a process crashes between assigning and persisting a slot?"
- "How would your design scale to 10,000 slots?"
- "Walk me through the `parkVehicle` method end-to-end."
- "Why did you choose a `LinkedHashMap` over a `TreeMap` for slot tracking?"
- "How would you extend this for EV charging spots?"

---

## Class Design

### Enums

```java
public enum VehicleType {
    MOTORCYCLE, CAR, TRUCK
}

public enum SlotStatus {
    AVAILABLE, OCCUPIED, RESERVED, MAINTENANCE
}
```

---

### Core Entities

```java
public abstract class Vehicle {
    protected String licensePlate;
    protected VehicleType type;

    public Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public String getLicensePlate() { return licensePlate; }
    public VehicleType getType() { return type; }
}

public class Car extends Vehicle {
    public Car(String plate) { super(plate, VehicleType.CAR); }
}

public class Motorcycle extends Vehicle {
    public Motorcycle(String plate) { super(plate, VehicleType.MOTORCYCLE); }
}

public class Truck extends Vehicle {
    public Truck(String plate) { super(plate, VehicleType.TRUCK); }
}
```

---

```java
public class ParkingSlot {
    private final String slotId;        // e.g., "L2-A-05"
    private final int level;
    private final String row;
    private final int number;
    private final VehicleType allowedType;
    private volatile SlotStatus status;
    private Vehicle currentVehicle;
    private LocalDateTime occupiedAt;

    public ParkingSlot(String slotId, int level, String row, int number, VehicleType allowedType) {
        this.slotId = slotId;
        this.level = level;
        this.row = row;
        this.number = number;
        this.allowedType = allowedType;
        this.status = SlotStatus.AVAILABLE;
    }

    public synchronized boolean occupy(Vehicle vehicle) {
        if (status != SlotStatus.AVAILABLE) return false;
        this.currentVehicle = vehicle;
        this.status = SlotStatus.OCCUPIED;
        this.occupiedAt = LocalDateTime.now();
        return true;
    }

    public synchronized Vehicle vacate() {
        Vehicle v = this.currentVehicle;
        this.currentVehicle = null;
        this.status = SlotStatus.AVAILABLE;
        this.occupiedAt = null;
        return v;
    }

    // Getters omitted for brevity
}
```

---

### Ticket

```java
public class ParkingTicket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSlot slot;
    private final String entryPointId;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;

    public ParkingTicket(Vehicle vehicle, ParkingSlot slot, String entryPointId) {
        this.ticketId = UUID.randomUUID().toString();
        this.vehicle = vehicle;
        this.slot = slot;
        this.entryPointId = entryPointId;
        this.entryTime = LocalDateTime.now();
    }

    public Duration getParkingDuration() {
        LocalDateTime end = exitTime != null ? exitTime : LocalDateTime.now();
        return Duration.between(entryTime, end);
    }
    // Getters + setters omitted
}
```

---

### Level

```java
public class ParkingLevel {
    private final int levelNumber;
    private final Map<String, ParkingSlot> slots;           // slotId → slot
    private final Map<VehicleType, List<ParkingSlot>> availableByType; // for fast lookup

    public ParkingLevel(int levelNumber) {
        this.levelNumber = levelNumber;
        this.slots = new ConcurrentHashMap<>();
        this.availableByType = new ConcurrentHashMap<>();
        for (VehicleType t : VehicleType.values()) {
            availableByType.put(t, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    public void addSlot(ParkingSlot slot) {
        slots.put(slot.getSlotId(), slot);
        availableByType.get(slot.getAllowedType()).add(slot);
    }

    public Optional<ParkingSlot> findAvailableSlot(VehicleType type) {
        return availableByType.getOrDefault(type, Collections.emptyList())
                .stream()
                .filter(s -> s.getStatus() == SlotStatus.AVAILABLE)
                .findFirst();
    }
}
```

---

### Entry/Exit Points

```java
public class EntryPoint {
    private final String id;
    private final int nearestLevel;
    public EntryPoint(String id, int nearestLevel) {
        this.id = id;
        this.nearestLevel = nearestLevel;
    }
    public String getId() { return id; }
    public int getNearestLevel() { return nearestLevel; }
}

public class ExitPoint {
    private final String id;
    public ExitPoint(String id) { this.id = id; }
    public String getId() { return id; }
}
```

---

### Slot Assignment Strategy (Strategy Pattern)

```java
public interface SlotAssignmentStrategy {
    Optional<ParkingSlot> assign(Vehicle vehicle, List<ParkingLevel> levels, EntryPoint entryPoint);
}

// Nearest-to-entry strategy
public class NearestFirstStrategy implements SlotAssignmentStrategy {
    @Override
    public Optional<ParkingSlot> assign(Vehicle vehicle, List<ParkingLevel> levels, EntryPoint entry) {
        // Start scanning from nearest level to entry point
        return levels.stream()
                .sorted(Comparator.comparingInt(l ->
                    Math.abs(l.getLevelNumber() - entry.getNearestLevel())))
                .map(level -> level.findAvailableSlot(vehicle.getType()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
```

---

### Pricing Strategy (Strategy Pattern)

```java
public interface PricingStrategy {
    double calculateFee(ParkingTicket ticket);
}

public class HourlyPricingStrategy implements PricingStrategy {
    private final Map<VehicleType, Double> ratePerHour;

    public HourlyPricingStrategy() {
        ratePerHour = new EnumMap<>(VehicleType.class);
        ratePerHour.put(VehicleType.MOTORCYCLE, 20.0);
        ratePerHour.put(VehicleType.CAR, 50.0);
        ratePerHour.put(VehicleType.TRUCK, 100.0);
    }

    @Override
    public double calculateFee(ParkingTicket ticket) {
        long hours = Math.max(1, ticket.getParkingDuration().toHours());
        return hours * ratePerHour.get(ticket.getVehicle().getType());
    }
}
```

---

### ParkingLot (Orchestrator)

```java
public class ParkingLot {
    private final String lotId;
    private final List<ParkingLevel> levels;
    private final List<EntryPoint> entryPoints;
    private final List<ExitPoint> exitPoints;
    private final SlotAssignmentStrategy assignmentStrategy;
    private final PricingStrategy pricingStrategy;

    // ticketId → ticket
    private final Map<String, ParkingTicket> activeTickets = new ConcurrentHashMap<>();

    public ParkingLot(String lotId,
                      List<ParkingLevel> levels,
                      List<EntryPoint> entryPoints,
                      List<ExitPoint> exitPoints,
                      SlotAssignmentStrategy strategy,
                      PricingStrategy pricing) {
        this.lotId = lotId;
        this.levels = levels;
        this.entryPoints = entryPoints;
        this.exitPoints = exitPoints;
        this.assignmentStrategy = strategy;
        this.pricingStrategy = pricing;
    }

    public synchronized ParkingTicket parkVehicle(Vehicle vehicle, String entryPointId) {
        EntryPoint entry = entryPoints.stream()
                .filter(e -> e.getId().equals(entryPointId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown entry: " + entryPointId));

        ParkingSlot slot = assignmentStrategy.assign(vehicle, levels, entry)
                .orElseThrow(() -> new RuntimeException("Parking full for: " + vehicle.getType()));

        if (!slot.occupy(vehicle)) {
            throw new RuntimeException("Slot race condition on: " + slot.getSlotId());
        }

        ParkingTicket ticket = new ParkingTicket(vehicle, slot, entryPointId);
        activeTickets.put(ticket.getTicketId(), ticket);
        return ticket;
    }

    public double exitVehicle(String ticketId) {
        ParkingTicket ticket = activeTickets.remove(ticketId);
        if (ticket == null) throw new IllegalArgumentException("Invalid ticket: " + ticketId);
        ticket.setExitTime(LocalDateTime.now());
        ticket.getSlot().vacate();
        return pricingStrategy.calculateFee(ticket);
    }

    public int getAvailableSlots(VehicleType type) {
        return levels.stream()
                .mapToInt(l -> (int) l.getSlots().values().stream()
                        .filter(s -> s.getAllowedType() == type && s.getStatus() == SlotStatus.AVAILABLE)
                        .count())
                .sum();
    }
}
```

---

## Concurrency & Thread Safety

| Concern | Solution |
|---------|----------|
| Slot double-booking | `synchronized` on `ParkingSlot.occupy()` |
| `parkVehicle` atomicity | `synchronized` on `ParkingLot.parkVehicle()` |
| Ticket map | `ConcurrentHashMap` for non-blocking reads |
| High throughput | Use Redis distributed lock (`SETNX`) across nodes |

---

## Redis Integration (Distributed Slot Counter)

```java
// Using Spring Data Redis Lettuce
@Service
public class SlotCounterService {
    @Autowired
    private StringRedisTemplate redis;

    private static final String KEY = "parking:available:%s:%s"; // lotId:vehicleType

    public boolean decrementAndCheck(String lotId, VehicleType type) {
        String key = String.format(KEY, lotId, type.name());
        Long remaining = redis.opsForValue().decrement(key);
        if (remaining != null && remaining < 0) {
            redis.opsForValue().increment(key); // rollback
            return false;
        }
        return true;
    }
}
```

---

## MySQL Schema

```sql
CREATE TABLE parking_slot (
    slot_id     VARCHAR(20) PRIMARY KEY,
    lot_id      VARCHAR(36) NOT NULL,
    level       INT NOT NULL,
    row_label   VARCHAR(5),
    slot_number INT,
    vehicle_type ENUM('MOTORCYCLE','CAR','TRUCK'),
    status      ENUM('AVAILABLE','OCCUPIED','RESERVED','MAINTENANCE') DEFAULT 'AVAILABLE',
    INDEX idx_lot_type_status (lot_id, vehicle_type, status)
);

CREATE TABLE parking_ticket (
    ticket_id   VARCHAR(36) PRIMARY KEY,
    lot_id      VARCHAR(36) NOT NULL,
    slot_id     VARCHAR(20) NOT NULL,
    license_plate VARCHAR(20),
    vehicle_type ENUM('MOTORCYCLE','CAR','TRUCK'),
    entry_point VARCHAR(20),
    entry_time  DATETIME NOT NULL,
    exit_time   DATETIME,
    fee_charged DECIMAL(10,2),
    INDEX idx_license_plate (license_plate),
    INDEX idx_entry_time (entry_time)
);
```

---

## Design Patterns Used

| Pattern | Where Applied |
|---------|--------------|
| Strategy | `SlotAssignmentStrategy`, `PricingStrategy` |
| Factory | `VehicleFactory` creates typed vehicles |
| Singleton | `ParkingLot` instance per lot |
| Observer | Emit events on slot status change (Kafka) |

---

## Extension Points

- **Multi-location**: Add `ParkingLotManager` that routes across multiple `ParkingLot` instances
- **Reservations**: Add `ReservationService` with TTL-based Redis slots
- **EV Charging**: Extend `ParkingSlot` with `hasCharger: boolean`
- **Analytics**: Publish slot change events to Kafka → consumed by Druid for occupancy dashboards