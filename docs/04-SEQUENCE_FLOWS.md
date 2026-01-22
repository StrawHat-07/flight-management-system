# Flight Management System - Sequence Flows

**Version:** 1.0  
**Last Updated:** January 2026  
**Status:** Production Ready

---

## 1. Search Flow

### 1.1 Direct Flight Search (Happy Path)

**Actors:** User, Flight Service, Redis, MySQL

```
User                Flight Service           Redis                MySQL
 │                        │                    │                    │
 │  GET /v1/search        │                    │                    │
 │  ?source=DEL           │                    │                    │
 │  &destination=BLR      │                    │                    │
 │  &date=2026-01-23      │                    │                    │
 │  &seats=2              │                    │                    │
 │───────────────────────▶│                    │                    │
 │                        │                    │                    │
 │                        │ GET routes:DEL:BLR:2026-01-23          │
 │                        │───────────────────▶│                    │
 │                        │                    │                    │
 │                        │   [CACHE MISS]     │                    │
 │                        │◀───────────────────│                    │
 │                        │                    │                    │
 │                        │ SELECT * FROM flights                   │
 │                        │ WHERE source='DEL'                      │
 │                        │   AND destination='BLR'                 │
 │                        │   AND DATE(departure_time)='2026-01-23' │
 │                        │   AND status='ACTIVE'                   │
 │                        │───────────────────────────────────────▶│
 │                        │                    │                    │
 │                        │   [Flight Records]                      │
 │                        │◀───────────────────────────────────────│
 │                        │                    │                    │
 │                        │ For each flight:                       │
 │                        │ GET flight:{id}:seats                  │
 │                        │───────────────────▶│                    │
 │                        │                    │                    │
 │                        │   [Seat counts]    │                    │
 │                        │◀───────────────────│                    │
 │                        │                    │                    │
 │                        │ [Filter: seats >= 2]                   │
 │                        │ [Sort by price ASC]                    │
 │                        │ [Paginate]                             │
 │                        │                    │                    │
 │  200 OK                │                    │                    │
 │  { content: [...],     │                    │                    │
 │    totalElements: N }  │                    │                    │
 │◀───────────────────────│                    │                    │
```

**State Ownership:**
- Flight Service: Query orchestration
- Redis: Cache (read-only in this flow)
- MySQL: Source of truth for flight data

---

### 1.2 Multi-Hop Search with Cached Routes

**Actors:** User, Flight Service, Redis, MySQL

```
User                Flight Service           Redis                MySQL
 │                        │                    │                    │
 │  GET /v1/search        │                    │                    │
 │  ?source=DEL           │                    │                    │
 │  &destination=GOI      │                    │                    │
 │  &maxHops=3            │                    │                    │
 │───────────────────────▶│                    │                    │
 │                        │                    │                    │
 │                        │ GET routes:DEL:GOI:2026-01-23          │
 │                        │───────────────────▶│                    │
 │                        │                    │                    │
 │                        │   [CACHE HIT]      │                    │
 │                        │   [Precomputed     │                    │
 │                        │    routes JSON]    │                    │
 │                        │◀───────────────────│                    │
 │                        │                    │                    │
 │                        │ [Parse cached routes]                  │
 │                        │ [For each route, get fresh seat counts]│
 │                        │                    │                    │
 │                        │ MGET flight:FL1:seats                  │
 │                        │      flight:FL2:seats ...              │
 │                        │───────────────────▶│                    │
 │                        │                    │                    │
 │                        │   [Seat counts]    │                    │
 │                        │◀───────────────────│                    │
 │                        │                    │                    │
 │                        │ [Filter: min(seats) >= requested]      │
 │                        │ [Sort, Paginate]                       │
 │                        │                    │                    │
 │  200 OK                │                    │                    │
 │  { content: [          │                    │                    │
 │      {type: DIRECT},   │                    │                    │
 │      {type: COMPUTED}  │                    │                    │
 │    ] }                 │                    │                    │
 │◀───────────────────────│                    │                    │
```

---

## 2. Booking Flow

### 2.1 Complete Booking (Happy Path)

**Actors:** User, Booking Service, Flight Service, Payment Service, MySQL, Redis

```
User          Booking Svc      Flight Svc       Payment Svc      MySQL         Redis
 │                │                │                │              │              │
 │ POST /v1/bookings              │                │              │              │
 │ {userId,       │                │                │              │              │
 │  flightId:FL201,               │                │              │              │
 │  seats: 2}     │                │                │              │              │
 │───────────────▶│                │                │              │              │
 │                │                │                │              │              │
 │                │ [Check idempotency key]         │              │              │
 │                │───────────────────────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │ [No existing booking]           │              │              │
 │                │◀───────────────────────────────────────────────│              │
 │                │                │                │              │              │
 │                │ GET /v1/flights/FL201           │              │              │
 │                │ (resolve flight, get price)     │              │              │
 │                │───────────────▶│                │              │              │
 │                │                │                │              │              │
 │                │ {flightId, price: 5400}         │              │              │
 │                │◀───────────────│                │              │              │
 │                │                │                │              │              │
 │                │ POST /v1/inventory/reserve      │              │              │
 │                │ {bookingId, flightIds,          │              │              │
 │                │  seats: 2, ttl: 5}              │              │              │
 │                │───────────────▶│                │              │              │
 │                │                │                │              │              │
 │                │                │ SETNX inventory:lock:FL201    │              │
 │                │                │────────────────────────────────────────────▶│
 │                │                │                │              │              │
 │                │                │ [Lock acquired]│              │              │
 │                │                │◀────────────────────────────────────────────│
 │                │                │                │              │              │
 │                │                │ BEGIN TRANSACTION             │              │
 │                │                │───────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │                │ UPDATE flights SET            │              │
 │                │                │ available_seats = available_seats - 2       │
 │                │                │ WHERE flight_id = 'FL201'     │              │
 │                │                │   AND available_seats >= 2    │              │
 │                │                │───────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │                │ [1 row affected]│              │              │
 │                │                │◀───────────────────────────────│              │
 │                │                │                │              │              │
 │                │                │ INSERT INTO seat_reservations │              │
 │                │                │ (booking_id, flight_id, seats,│              │
 │                │                │  expires_at)                  │              │
 │                │                │───────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │                │ COMMIT         │              │              │
 │                │                │───────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │                │ SET flight:FL201:seats {new_count}           │
 │                │                │────────────────────────────────────────────▶│
 │                │                │                │              │              │
 │                │                │ DEL inventory:lock:FL201      │              │
 │                │                │────────────────────────────────────────────▶│
 │                │                │                │              │              │
 │                │ {success: true, expiresAt: ...} │              │              │
 │                │◀───────────────│                │              │              │
 │                │                │                │              │              │
 │                │ INSERT INTO bookings            │              │              │
 │                │ (booking_id, user_id, ...)      │              │              │
 │                │───────────────────────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │ INSERT INTO booking_flights     │              │              │
 │                │───────────────────────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │ POST /v1/payments/process       │              │              │
 │                │ {bookingId, amount: 10800,      │              │              │
 │                │  callbackUrl}  │                │              │              │
 │                │──────────────────────────────▶│              │              │
 │                │                │                │              │              │
 │                │ 202 Accepted   │                │              │              │
 │                │◀──────────────────────────────│              │              │
 │                │                │                │              │              │
 │ 201 Created    │                │                │              │              │
 │ {bookingId,    │                │                │              │              │
 │  status: PENDING}               │                │              │              │
 │◀───────────────│                │                │              │              │
 │                │                │                │              │              │
 │     [ASYNC - Payment Processing]                │              │              │
 │                │                │                │              │              │
 │                │                │      [Process payment]        │              │
 │                │                │                │              │              │
 │                │ POST /v1/bookings/payment-callback             │              │
 │                │ {bookingId, status: SUCCESS}   │              │              │
 │                │◀──────────────────────────────│              │              │
 │                │                │                │              │              │
 │                │ POST /v1/inventory/confirm      │              │              │
 │                │ {bookingId, flightIds}          │              │              │
 │                │───────────────▶│                │              │              │
 │                │                │                │              │              │
 │                │                │ UPDATE seat_reservations      │              │
 │                │                │ SET deleted_at = NOW()        │              │
 │                │                │ WHERE booking_id = ?          │              │
 │                │                │───────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │ {status: confirmed}             │              │              │
 │                │◀───────────────│                │              │              │
 │                │                │                │              │              │
 │                │ UPDATE bookings SET             │              │              │
 │                │ status = 'CONFIRMED'            │              │              │
 │                │───────────────────────────────────────────────▶│              │
 │                │                │                │              │              │
```

**State Ownership at Each Step:**

| Step | State Owner | Data Modified |
|------|-------------|---------------|
| 1 | Booking Service | Idempotency check |
| 2 | Flight Service | Lock acquired |
| 3 | Flight Service (MySQL) | `flights.available_seats` decremented |
| 4 | Flight Service (MySQL) | `seat_reservations` created |
| 5 | Flight Service (Redis) | Cache updated |
| 6 | Booking Service (MySQL) | `bookings` created |
| 7 | Payment Service | Payment initiated |
| 8 | Payment Service | Payment processed |
| 9 | Booking Service | Callback received |
| 10 | Flight Service | Reservation soft-deleted |
| 11 | Booking Service | Booking CONFIRMED |

---

### 2.2 Booking with Payment Failure

```
User          Booking Svc      Flight Svc       Payment Svc      MySQL         Redis
 │                │                │                │              │              │
 │ [Steps 1-7 same as happy path]  │                │              │              │
 │                │                │                │              │              │
 │                │                │      [Process payment]        │              │
 │                │                │      [PAYMENT DECLINED]       │              │
 │                │                │                │              │              │
 │                │ POST /v1/bookings/payment-callback             │              │
 │                │ {bookingId, status: FAILURE,   │              │              │
 │                │  message: "Card declined"}     │              │              │
 │                │◀──────────────────────────────│              │              │
 │                │                │                │              │              │
 │                │ DELETE /v1/inventory/release/{bookingId}       │              │
 │                │───────────────▶│                │              │              │
 │                │                │                │              │              │
 │                │                │ SELECT * FROM seat_reservations              │
 │                │                │ WHERE booking_id = ?          │              │
 │                │                │   AND deleted_at IS NULL      │              │
 │                │                │───────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │                │ [reservation: 2 seats on FL201]              │
 │                │                │◀───────────────────────────────│              │
 │                │                │                │              │              │
 │                │                │ SETNX inventory:lock:FL201    │              │
 │                │                │────────────────────────────────────────────▶│
 │                │                │                │              │              │
 │                │                │ BEGIN TRANSACTION             │              │
 │                │                │                │              │              │
 │                │                │ UPDATE flights SET            │              │
 │                │                │ available_seats = available_seats + 2       │
 │                │                │ WHERE flight_id = 'FL201'     │              │
 │                │                │───────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │                │ UPDATE seat_reservations      │              │
 │                │                │ SET deleted_at = NOW()        │              │
 │                │                │ WHERE booking_id = ?          │              │
 │                │                │───────────────────────────────▶│              │
 │                │                │                │              │              │
 │                │                │ COMMIT         │              │              │
 │                │                │                │              │              │
 │                │                │ SET flight:FL201:seats {restored}            │
 │                │                │────────────────────────────────────────────▶│
 │                │                │                │              │              │
 │                │                │ DEL inventory:lock:FL201      │              │
 │                │                │────────────────────────────────────────────▶│
 │                │                │                │              │              │
 │                │ {status: released}              │              │              │
 │                │◀───────────────│                │              │              │
 │                │                │                │              │              │
 │                │ UPDATE bookings SET             │              │              │
 │                │ status = 'FAILED'               │              │              │
 │                │───────────────────────────────────────────────▶│              │
```

**Key Difference:** Seats are restored to `flights.available_seats` and cache is synced.

---

## 3. Concurrent Booking Scenario

### 3.1 Two Users Booking Last 2 Seats

**Initial State:** FL201 has 2 available seats

```
User A              Flight Svc                Redis              User B
 │                      │                       │                   │
 │ Reserve 2 seats      │                       │  Reserve 2 seats  │
 │─────────────────────▶│                       │◀──────────────────│
 │                      │                       │                   │
 │                      │ SETNX lock:FL201      │                   │
 │                      │ [User A's request]    │                   │
 │                      │──────────────────────▶│                   │
 │                      │                       │                   │
 │                      │ OK (lock acquired)    │                   │
 │                      │◀──────────────────────│                   │
 │                      │                       │                   │
 │                      │ SETNX lock:FL201      │                   │
 │                      │ [User B's request]    │                   │
 │                      │──────────────────────▶│                   │
 │                      │                       │                   │
 │                      │ FAIL (already locked) │                   │
 │                      │◀──────────────────────│                   │
 │                      │                       │                   │
 │                      │ [User B waits/retries]│                   │
 │                      │                       │                   │
 │                      │ [User A: UPDATE seats]│                   │
 │                      │ available = 2 - 2 = 0 │                   │
 │                      │                       │                   │
 │                      │ [User A: INSERT reservation]              │
 │                      │                       │                   │
 │                      │ DEL lock:FL201        │                   │
 │                      │──────────────────────▶│                   │
 │                      │                       │                   │
 │ Success (2 seats)    │                       │                   │
 │◀─────────────────────│                       │                   │
 │                      │                       │                   │
 │                      │ SETNX lock:FL201      │                   │
 │                      │ [User B retry]        │                   │
 │                      │──────────────────────▶│                   │
 │                      │                       │                   │
 │                      │ OK (lock acquired)    │                   │
 │                      │◀──────────────────────│                   │
 │                      │                       │                   │
 │                      │ [User B: SELECT seats]│                   │
 │                      │ available = 0         │                   │
 │                      │                       │                   │
 │                      │ DEL lock:FL201        │                   │
 │                      │──────────────────────▶│                   │
 │                      │                       │  FAIL             │
 │                      │                       │  (NO_SEATS)       │
 │                      │                       │─────────────────▶│
```

**Guarantees:**
- Only one user can modify inventory at a time (per flight)
- Second user sees accurate availability after first completes
- No overbooking possible

---

## 4. Reservation Expiry Flow

### 4.1 Background Cleanup Job

**Trigger:** Scheduled job runs every 10 seconds

```
Cleanup Job              Flight Svc              MySQL               Redis
    │                        │                     │                   │
    │ [Scheduled trigger]    │                     │                   │
    │───────────────────────▶│                     │                   │
    │                        │                     │                   │
    │                        │ SELECT * FROM seat_reservations        │
    │                        │ WHERE expires_at < NOW()               │
    │                        │   AND deleted_at IS NULL               │
    │                        │────────────────────▶│                   │
    │                        │                     │                   │
    │                        │ [Expired reservations:                 │
    │                        │  BK001: FL201, 3 seats                 │
    │                        │  BK002: FL202, 2 seats]                │
    │                        │◀────────────────────│                   │
    │                        │                     │                   │
    │                        │ [For each expired reservation:]        │
    │                        │                     │                   │
    │                        │ SETNX lock:FL201    │                   │
    │                        │─────────────────────────────────────▶│
    │                        │                     │                   │
    │                        │ BEGIN TRANSACTION   │                   │
    │                        │                     │                   │
    │                        │ UPDATE flights      │                   │
    │                        │ SET available_seats │                   │
    │                        │   = available_seats + 3                │
    │                        │ WHERE flight_id = 'FL201'              │
    │                        │────────────────────▶│                   │
    │                        │                     │                   │
    │                        │ UPDATE seat_reservations               │
    │                        │ SET deleted_at = NOW()                 │
    │                        │ WHERE id = ?        │                   │
    │                        │────────────────────▶│                   │
    │                        │                     │                   │
    │                        │ COMMIT              │                   │
    │                        │                     │                   │
    │                        │ SET flight:FL201:seats                 │
    │                        │─────────────────────────────────────▶│
    │                        │                     │                   │
    │                        │ DEL lock:FL201      │                   │
    │                        │─────────────────────────────────────▶│
    │                        │                     │                   │
    │                        │ [Repeat for FL202...]                  │
    │                        │                     │                   │
    │ [Completed]            │                     │                   │
    │◀───────────────────────│                     │                   │
```

**Invariant Maintained:**
```
available_seats = total_seats - confirmed_bookings - active_reservations
```

---

## 5. Redis Failure Scenario

### 5.1 Search with Redis Down

```
User              Flight Svc              Redis (DOWN)          MySQL
 │                    │                       │                   │
 │ GET /v1/search     │                       │                   │
 │───────────────────▶│                       │                   │
 │                    │                       │                   │
 │                    │ GET routes:DEL:BLR    │                   │
 │                    │──────────────────────▶│                   │
 │                    │                       │                   │
 │                    │ [CONNECTION TIMEOUT]  │                   │
 │                    │◀──────────────────────│                   │
 │                    │                       │                   │
 │                    │ [Log warning: Redis unavailable]          │
 │                    │ [Fallback to DB]      │                   │
 │                    │                       │                   │
 │                    │ SELECT * FROM flights │                   │
 │                    │ WHERE source='DEL'... │                   │
 │                    │───────────────────────────────────────────▶│
 │                    │                       │                   │
 │                    │ [Direct flights]      │                   │
 │                    │◀───────────────────────────────────────────│
 │                    │                       │                   │
 │                    │ [Compute routes in-memory]                │
 │                    │ [Skip cache write]    │                   │
 │                    │                       │                   │
 │ 200 OK             │                       │                   │
 │ [Results - slower] │                       │                   │
 │◀───────────────────│                       │                   │
```

**Degradation:**
- Search still works (no data loss)
- P99 latency increases from ~100ms to ~500ms
- Routes computed on-the-fly instead of cached

---

## 6. Multi-Flight Booking (Computed Route)

### 6.1 Booking DEL→BLR via BOM (2 flights)

```
User          Booking Svc      Flight Svc                MySQL
 │                │                │                       │
 │ POST /bookings │                │                       │
 │ {flightId:     │                │                       │
 │  CF_DEL_BLR_FL1_FL2}           │                       │
 │───────────────▶│                │                       │
 │                │                │                       │
 │                │ GET /computed/CF_DEL_BLR_FL1_FL2       │
 │                │───────────────▶│                       │
 │                │                │                       │
 │                │ {flightIds: [FL1, FL2],               │
 │                │  totalPrice: 9000}                    │
 │                │◀───────────────│                       │
 │                │                │                       │
 │                │ POST /inventory/reserve               │
 │                │ {bookingId, flightIds: [FL1, FL2],    │
 │                │  seats: 2}     │                       │
 │                │───────────────▶│                       │
 │                │                │                       │
 │                │                │ [Acquire locks for BOTH flights]
 │                │                │ SETNX lock:FL1        │
 │                │                │ SETNX lock:FL2        │
 │                │                │                       │
 │                │                │ [Single transaction for both]
 │                │                │ BEGIN TRANSACTION     │
 │                │                │                       │
 │                │                │ UPDATE flights        │
 │                │                │ SET available_seats -= 2
 │                │                │ WHERE flight_id IN ('FL1', 'FL2')
 │                │                │ AND available_seats >= 2
 │                │                │───────────────────────▶│
 │                │                │                       │
 │                │                │ [2 rows affected]     │
 │                │                │◀───────────────────────│
 │                │                │                       │
 │                │                │ INSERT INTO seat_reservations
 │                │                │ (booking_id='BK1', flight_id='FL1')
 │                │                │ (booking_id='BK1', flight_id='FL2')
 │                │                │───────────────────────▶│
 │                │                │                       │
 │                │                │ COMMIT                │
 │                │                │                       │
 │                │                │ [Sync both to Redis]  │
 │                │                │ [Release both locks]  │
 │                │                │                       │
 │                │ {success: true}│                       │
 │                │◀───────────────│                       │
 │                │                │                       │
 │                │ [Continue with payment...]            │
```

**Atomicity Guarantee:**
- Both flights reserved in single DB transaction
- If either fails (insufficient seats), entire transaction rolls back
- Both locks released only after commit

---

## 7. Idempotent Booking Request

### 7.1 Duplicate Request Handling

```
User              Booking Svc              MySQL
 │                    │                      │
 │ POST /bookings     │                      │
 │ X-Idempotency-Key: │                      │
 │   "abc123"         │                      │
 │───────────────────▶│                      │
 │                    │                      │
 │                    │ SELECT * FROM bookings
 │                    │ WHERE idempotency_key = 'abc123'
 │                    │─────────────────────▶│
 │                    │                      │
 │                    │ [No match]           │
 │                    │◀─────────────────────│
 │                    │                      │
 │                    │ [Process booking...]  │
 │                    │                      │
 │ 201 Created        │                      │
 │ {bookingId: BK001} │                      │
 │◀───────────────────│                      │
 │                    │                      │
 │                    │                      │
 │ [DUPLICATE REQUEST - Network retry]       │
 │                    │                      │
 │ POST /bookings     │                      │
 │ X-Idempotency-Key: │                      │
 │   "abc123"         │                      │
 │───────────────────▶│                      │
 │                    │                      │
 │                    │ SELECT * FROM bookings
 │                    │ WHERE idempotency_key = 'abc123'
 │                    │─────────────────────▶│
 │                    │                      │
 │                    │ [Found: BK001]       │
 │                    │◀─────────────────────│
 │                    │                      │
 │ 200 OK             │                      │
 │ {bookingId: BK001} │  [Same booking returned]
 │◀───────────────────│                      │
```

**Guarantee:**
- Same idempotency key always returns same booking
- No duplicate reservations or charges
- Client can safely retry on network failures
