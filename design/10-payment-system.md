# Design a Payment System / Digital Wallet
> The hardest correctness question. Tests: idempotency, distributed transactions, double-spend prevention, eventual consistency, regulatory awareness.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| Peer-to-peer wallet or payment gateway integration? | Internal ledger vs external PSP coordination |
| What currencies? Multi-currency? | FX conversion, precision (always use paise/cents, never float) |
| Synchronous or async settlement? | UPI = near-sync, NEFT = batch |
| Idempotency — what if network fails mid-payment? | This is the core problem |
| Regulatory: KYC, AML, PCI-DSS? | Shapes data storage and access patterns |
| Scale: transactions/sec? | 10K TPS is very different from 1M TPS |
| Refunds, partial payments, splits? | Scope of ledger operations |
| Read-heavy or write-heavy? | Balance reads vs transaction writes |

**Typical answer:** Digital wallet (P2P + merchant payments), INR + multi-currency, 50K TPS peak, strict idempotency, PCI-DSS compliant, refunds needed.

---

## Scale Estimation

```
50K TPS → 50,000 transactions/second at peak
  → Write throughput: 50K * 2 ledger entries (debit + credit) = 100K writes/sec

Daily: 50K TPS * 3600 * 16 active hours = ~2.88B transactions/day
Storage: 2.88B * 200 bytes = ~576 GB/day ledger data

Read throughput: balance checks are 10x writes (check before pay) = 500K reads/sec
  → This is why balance must be cached
```

---

## HLD Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                   Client (App / Merchant)                    │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTPS (TLS 1.3)
              ┌────────────▼────────────┐
              │    API Gateway [NGINX]  │  PCI-DSS zone boundary
              │    (TLS termination,    │
              │     rate limiting)      │
              └────────────┬────────────┘
                           │
              ┌────────────▼────────────┐
              │    Payment API Service  │  [Java/SBoot]
              │  - Idempotency check    │
              │  - Input validation     │
              │  - Auth & authz         │
              └────────────┬────────────┘
                           │
         ┌─────────────────┼──────────────────┐
         │                 │                  │
┌────────▼──────┐ ┌────────▼──────┐ ┌────────▼──────┐
│  Balance Svc  │ │ Transfer Svc  │ │  Ledger Svc   │
│  [Java]       │ │  [Java]       │ │  [Java]       │
└────────┬──────┘ └────────┬──────┘ └────────┬──────┘
         │                 │                  │
{Redis Cache}    ┌─────────▼──────┐  ┌────────▼──────────┐
balance:{userId} │  <Kafka>       │  │  ((MySQL/Aurora)) │
                 │  payment.init  │  │  ledger_entries   │
                 │  payment.done  │  │  wallets          │
                 │  payment.fail  │  │  transactions     │
                 └─────────┬──────┘  └───────────────────┘
                           │
              ┌────────────▼────────────┐
              │   Payment Processor     │  [Java]
              │   (Saga Orchestrator)   │
              └────────────┬────────────┘
                           │
         ┌─────────────────┼──────────────────┐
         │                 │                  │
┌────────▼──────┐ ┌────────▼──────┐ ┌────────▼──────┐
│  Fraud Svc    │ │  PSP Adapter  │ │  Notification │
│  [Python/ML]  │ │  (Razorpay /  │ │  Svc          │
│               │ │   Stripe)     │ │               │
└───────────────┘ └───────────────┘ └───────────────┘

┌──────────────────────────────────────────────────┐
│              Audit & Compliance                  │
│    All events → immutable audit log (S3 + Kafka) │
│    Regulatory reporting → Druid                  │
└──────────────────────────────────────────────────┘
```

---

## Key Design Decisions

### 1. Idempotency — The Core Problem

```
Problem: 
  User pays ₹1000. Network timeout at T+2s.
  Client retries. Do they pay ₹2000?

Solution: Idempotency Key

Every payment request carries a client-generated idempotency_key (UUID).

Flow:
  1. Client generates idempotency_key = UUID v4
  2. Sends: POST /payment { amount, to, idempotency_key }
  3. Server: SELECT * FROM idempotency_keys WHERE key = ?
     - Found, status = COMPLETED → return same response (don't re-process)
     - Found, status = IN_PROGRESS → return 202 (processing, check later)
     - Not found → proceed with payment → INSERT idempotency_keys(key, status=IN_PROGRESS)

  4. After payment completes → UPDATE idempotency_keys SET status=COMPLETED, response=<result>

Why not just dedup in Redis?
  → Redis can fail → lose the dedup record → double charge
  → DB-level unique constraint is durable

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    status          ENUM('IN_PROGRESS', 'COMPLETED', 'FAILED'),
    response_json   TEXT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at)
);
```

### 2. Double-Spend Prevention

```
Problem: User has ₹1000 balance. Makes 2 concurrent payments of ₹800 each.
Both read balance=1000. Both think they can proceed. Both deduct. Balance = -600.

Solutions:

A. Pessimistic Locking (SELECT FOR UPDATE):
   BEGIN TRANSACTION;
   SELECT balance FROM wallets WHERE user_id = ? FOR UPDATE;  -- row lock
   IF balance >= amount:
     UPDATE wallets SET balance = balance - amount WHERE user_id = ?;
     INSERT INTO ledger_entries (...);
   COMMIT;
   
   ✓ Correct — serialized access
   ✗ Low throughput — lock contention at 50K TPS

B. Optimistic Locking (Compare-and-Swap):
   SELECT balance, version FROM wallets WHERE user_id = ?;
   -- application checks balance >= amount
   UPDATE wallets 
     SET balance = balance - amount, version = version + 1
     WHERE user_id = ? AND version = <read_version>;
   -- if 0 rows updated → conflict → retry
   
   ✓ Higher throughput — no locks held
   ✗ Retry under contention — need retry limit

C. Event Sourcing (Ledger-based):
   Never store "current balance" — derive it from ledger.
   Each debit/credit is an immutable ledger entry.
   Balance = SUM of all entries (cached in Redis).
   
   Idempotency + ledger:
     INSERT ledger_entries (entry_id, user_id, type, amount, ref_id) 
     -- UNIQUE(ref_id) prevents duplicate entry
   
   ✓ Audit trail built-in
   ✓ No double-spend (unique constraint)
   ✗ Balance computation expensive (mitigated by snapshot + Redis cache)

Production choice: Optimistic locking for high-throughput + ledger for audit trail.
```

### 3. The Double-Spend in Distributed Systems

```
User has ₹1000 in wallet. Makes payment on 2 devices simultaneously.
Both requests hit different API servers. Both read balance from Redis = ₹1000.
Both proceed to DB write. One wins (optimistic lock), one retries → correct.

What if we have DB sharding?
→ User's wallet must be on ONE shard (shard by user_id)
→ Both concurrent payments → same DB shard → serialized by DB lock
→ Cross-user transfers are harder (sender on shard A, receiver on shard B)

Cross-shard transfers (Saga Pattern):
  1. Debit sender (shard A) → PENDING
  2. Credit receiver (shard B) → SUCCESS
  3. Mark sender debit as COMPLETED
  
  Failure at step 2:
  → Compensating transaction: refund sender (reverse the debit)
  → This is the Saga choreography pattern
```

### 4. Saga Pattern for Distributed Transactions

```
No distributed 2-phase commit (2PC) — it's slow and locks resources.
Use Saga: sequence of local transactions with compensating actions.

Choreography-based Saga for P2P Transfer:

  Event: payment.initiated { txnId, fromUser, toUser, amount }
    ↓
  Balance Service: debit fromUser → publish payment.debited or payment.debit_failed
    ↓ (if debited)
  Ledger Service: record debit entry → publish ledger.debit_recorded
    ↓
  Balance Service: credit toUser → publish payment.credited or payment.credit_failed
    ↓ (if credited)
  Ledger Service: record credit entry → publish ledger.credit_recorded
    ↓
  Notification Service: notify both users → publish notifications
    ↓
  payment.completed

  If credit fails (toUser account frozen, closed):
    → Compensate: REVERSE the debit → credit fromUser back
    → payment.refunded event
    → Notify both users of failure + refund

Orchestration-based (simpler to reason about):
  → Single Payment Orchestrator service calls each step sequentially
  → On failure → orchestrator calls compensating APIs
  → Easier to monitor (single state machine), less eventual consistency chaos
```

### 5. Ledger Design — Double-Entry Bookkeeping

```
Every payment = 2 ledger entries (debit + credit).
Sum of ALL entries across ALL accounts must = 0. (Conservation of money)

CREATE TABLE ledger_entries (
    entry_id       BIGINT PRIMARY KEY,      -- Snowflake ID
    transaction_id BIGINT NOT NULL,         -- groups debit+credit pair
    account_id     BIGINT NOT NULL,         -- user wallet or escrow account
    entry_type     ENUM('DEBIT', 'CREDIT'),
    amount         BIGINT NOT NULL,         -- in paise (₹1 = 100 paise). NEVER FLOAT.
    currency       CHAR(3) NOT NULL,        -- INR, USD, etc.
    balance_after  BIGINT NOT NULL,         -- snapshot of balance after this entry
    ref_id         VARCHAR(64) UNIQUE,      -- idempotency: txnId + entryType
    description    VARCHAR(200),
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_time (account_id, created_at)
);

-- Payment of ₹500 from User A to User B:
INSERT INTO ledger_entries VALUES
  (snowflake(), txnId, userA_account, 'DEBIT',  50000, 'INR', 150000, 'txn123:DEBIT',  'Payment to UserB'),
  (snowflake(), txnId, userB_account, 'CREDIT', 50000, 'INR', 200000, 'txn123:CREDIT', 'Payment from UserA');

-- NEVER store float: 0.1 + 0.2 ≠ 0.3 in floating point. Always paise/cents as BIGINT.
```

### 6. Balance Caching

```
Reading balance from ledger SUM is expensive at scale.

Cache balance in Redis:
  Key: balance:{userId}:{currency}  Value: balance_in_paise

Write-through pattern:
  → Every ledger write → also update Redis balance atomically (Lua script)
  → Lua: INCRBY balance:userId:INR <delta> (DEBIT: negative, CREDIT: positive)

Lua script (atomic balance update + ledger write):
  -- pseudo-Lua for illustration
  local key = "balance:" .. user_id .. ":INR"
  local current = redis.call("GET", key)
  if tonumber(current) < amount then
      return "INSUFFICIENT_FUNDS"
  end
  redis.call("DECRBY", key, amount)
  return "OK"

Recovery (Redis failure):
  → If Redis cache lost → rebuild from ledger (SUM of entries)
  → Scheduled job: periodically verify Redis balance == ledger SUM → alert on drift

Balance snapshot:
  → Periodically (daily) write a balance snapshot to MySQL
  → Speeds up balance rebuild: SUM(entries after snapshot) + snapshot_balance
```

### 7. Fraud Detection

```
Real-time signals → Kafka → Fraud Service (Python/ML):

Rules-based (fast, first line):
  → Amount > ₹50,000 → require 2FA re-auth
  → 10+ transactions in 5 minutes → flag
  → New device + international merchant → flag
  → Same recipient received 5 payments from different users in 1 min → ring alert

ML model (async, 100ms budget):
  → Features: user history, device fingerprint, network, amount, time
  → XGBoost / LightGBM trained on labeled fraud data
  → Score 0-100 → threshold configurable per product
  → > 80: block, 50-80: step-up auth (OTP), < 50: allow

Velocity checks in Redis:
  INCR txn_count:{userId}:{minute}  EX 60
  If count > 10 → suspicious

Integration:
  Payment API → sync call to Fraud Service (100ms timeout)
  If fraud_score > threshold → return 403 + "transaction blocked for security"
  If Fraud Service times out → fail open (allow) for UX, flag for review
```

---

## State Machine — Transaction Lifecycle

```
INITIATED → FRAUD_CHECK → AUTHORIZED → DEBITED → CREDITED → COMPLETED
              ↓               ↓            ↓          ↓
           BLOCKED         DECLINED     REFUNDED    FAILED
                                         ↑
                                   (compensation)

Stored in: MySQL transactions table
  - status column with ENUM
  - status_updated_at
  - failure_reason (if failed)
  - All state transitions logged to Kafka (audit trail)
```

---

## Code Skeleton — Transfer Service

```java
@Service
@Transactional
public class TransferService {

    public TransactionResult transfer(TransferRequest req) {
        
        // 1. Idempotency check
        Optional<Transaction> existing = txnRepo.findByIdempotencyKey(req.getIdempotencyKey());
        if (existing.isPresent()) return existing.get().toResult(); // return cached response
        
        // 2. Create transaction record
        Transaction txn = Transaction.create(req);
        txnRepo.save(txn);  // status = INITIATED
        
        // 3. Fraud check (sync, 100ms budget)
        FraudScore score = fraudService.check(req);
        if (score.isBlocked()) {
            txn.fail("FRAUD_BLOCKED");
            return TransactionResult.blocked();
        }
        
        // 4. Debit sender (optimistic lock)
        int updated = walletRepo.debitIfSufficient(
            req.getFromUserId(), req.getAmount(), req.getCurrency()
        ); // UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ? AND version = ?
        
        if (updated == 0) return TransactionResult.insufficientFunds();
        
        // 5. Write debit ledger entry
        ledgerRepo.save(LedgerEntry.debit(txn.getId(), req.getFromUserId(), req.getAmount()));
        
        // 6. Credit receiver
        walletRepo.credit(req.getToUserId(), req.getAmount(), req.getCurrency());
        
        // 7. Write credit ledger entry
        ledgerRepo.save(LedgerEntry.credit(txn.getId(), req.getToUserId(), req.getAmount()));
        
        // 8. Complete
        txn.complete();
        
        // 9. Publish events (notification, analytics) — async via Kafka
        kafkaTemplate.send("payment.completed", txn.toEvent());
        
        return TransactionResult.success(txn);
    }
}
```

---

## Interview Tips

- **NEVER use float for money** — say this early. Always `BIGINT` in paise/cents, convert to decimal for display only.
- **Idempotency key** is the first thing to design — "what happens if the network fails mid-transfer?"
- **Saga pattern over 2PC** — "2PC is slow and creates lock contention; we use Saga with compensating transactions."
- **Double-entry bookkeeping** — name it. Shows financial domain knowledge.
- **Fraud detection timeout → fail open** — shows product vs security balance awareness.
- Mention **PCI-DSS**: no storing raw card data (use tokenization via Stripe/Razorpay), audit logs, access controls.
- **Druid for real-time analytics**: "how much did we settle today" is a real-time OLAP query — exactly Druid's use case.
- Bring up **idempotency at the DB level** (UNIQUE constraint on ref_id in ledger) as the final safety net.
