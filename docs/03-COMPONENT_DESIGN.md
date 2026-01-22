# Flight Management System - Component Design

**Version:** 1.0  
**Last Updated:** January 2026  
**Status:** Production Ready

---

## 1. Flight Service

### 1.1 Responsibility

Primary service for flight data management, search operations, and seat inventory control.

**Bounded Context:** Flight Operations

**Capabilities:**
- Flight CRUD operations
- Multi-hop route search with caching
- Seat inventory management with distributed locking
- Route precomputation for performance

### 1.2 Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       Flight Service                             │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ FlightController│  │ SearchController│  │InventoryController│
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘ │
│           │                    │                    │           │
│  ┌────────▼────────────────────▼────────────────────▼────────┐ │
│  │                      Service Layer                         │ │
│  │  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐ │ │
│  │  │ FlightService│ │ SearchService│ │InventoryReservation│ │ │
│  │  └──────────────┘ └──────────────┘ │     Service        │ │ │
│  │                                     └────────────────────┘ │ │
│  │  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐ │ │
│  │  │InventoryServ│ │RouteFinderSvc│ │DistributedLockServ │ │ │
│  │  └──────────────┘ └──────────────┘ └────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────┘ │
│           │                    │                    │           │
│  ┌────────▼────────────────────▼────────────────────▼────────┐ │
│  │                   Repository Layer                         │ │
│  │  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐ │ │
│  │  │FlightRepo    │ │SeatReservRepo│ │   CacheService     │ │ │
│  │  └──────────────┘ └──────────────┘ └────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────┘ │
│           │                    │                    │           │
│           ▼                    ▼                    ▼           │
│      ┌─────────┐          ┌─────────┐          ┌─────────┐     │
│      │  MySQL  │          │  MySQL  │          │  Redis  │     │
│      │ flights │          │seat_resv│          │  cache  │     │
│      └─────────┘          └─────────┘          └─────────┘     │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 Public APIs

#### Flight Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/flights/all` | List all active flights |
| GET | `/v1/flights/{flightId}` | Get flight by ID |
| POST | `/v1/flights` | Create new flight |
| PUT | `/v1/flights/{flightId}` | Update flight |
| DELETE | `/v1/flights/{flightId}` | Cancel flight (soft delete) |
| GET | `/v1/flights/graph` | Get flight connectivity graph |

#### Search

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/search` | Search flights with filters |
| GET | `/v1/search/computed/{id}` | Get computed flight details |

#### Inventory

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/flights/{flightId}/available-seats` | Get seat availability |
| POST | `/v1/inventory/reserve` | Reserve seats |
| POST | `/v1/inventory/confirm` | Confirm reservation |
| DELETE | `/v1/inventory/release/{bookingId}` | Release reservation |

### 1.4 Dependencies

| Dependency | Type | Purpose | Failure Handling |
|------------|------|---------|------------------|
| MySQL | Sync | Primary data store | Retry with backoff; fail request |
| Redis | Sync | Cache + Locks | Fallback to DB; degraded mode |

### 1.5 Failure Modes

| Failure | Detection | Response | Recovery |
|---------|-----------|----------|----------|
| DB connection failure | Connection timeout | Return 503 | Retry; circuit breaker |
| Redis unavailable | Connection timeout | Fallback to DB | Log warning; continue |
| Lock acquisition timeout | 5s timeout | Return 409 Conflict | Retry with exponential backoff |
| Expired reservation | Background job | Release seats | Automatic; every 10s |

### 1.6 Caching Strategy

| Data | Cache Key | TTL | Invalidation |
|------|-----------|-----|--------------|
| Flight seats | `flight:{flightId}:seats` | None | On every write |
| Computed routes | `routes:{source}:{dest}:{date}` | 24h | On flight change |
| Flight graph | `flight:graph` | 6h | On precomputation |

---

## 2. Booking Service

### 2.1 Responsibility

Orchestrates the booking lifecycle including payment coordination.

**Bounded Context:** Commerce

**Capabilities:**
- Booking creation and management
- Payment orchestration
- Price calculation
- Booking status tracking

### 2.2 Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       Booking Service                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    BookingController                         ││
│  └──────────────────────────┬──────────────────────────────────┘│
│                             │                                    │
│  ┌──────────────────────────▼──────────────────────────────────┐│
│  │                    Service Layer                             ││
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ ││
│  │  │BookingService│ │PaymentOrch   │ │  PricingService      │ ││
│  │  └──────────────┘ └──────────────┘ └──────────────────────┘ ││
│  └───────────────────────────────────────────────────────────────│
│                             │                                    │
│  ┌──────────────────────────▼──────────────────────────────────┐│
│  │                   Client Layer                               ││
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ ││
│  │  │FlightClient  │ │InventoryClnt│ │PaymentServiceClient  │ ││
│  │  └──────────────┘ └──────────────┘ └──────────────────────┘ ││
│  └───────────────────────────────────────────────────────────────│
│                             │                                    │
│  ┌──────────────────────────▼──────────────────────────────────┐│
│  │                   Repository Layer                           ││
│  │  ┌──────────────┐ ┌──────────────┐                          ││
│  │  │BookingRepo   │ │BookingFlight │                          ││
│  │  │              │ │   Repo       │                          ││
│  │  └──────────────┘ └──────────────┘                          ││
│  └──────────────────────────────────────────────────────────────┘│
│                             │                                    │
│                        ┌────▼────┐                               │
│                        │  MySQL  │                               │
│                        │bookings │                               │
│                        └─────────┘                               │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 Public APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/bookings` | Create booking |
| GET | `/v1/bookings/{bookingId}` | Get booking details |
| GET | `/v1/bookings/user/{userId}` | Get user's bookings |
| POST | `/v1/bookings/payment-callback` | Payment webhook |

### 2.4 Dependencies

| Dependency | Type | Purpose | Failure Handling |
|------------|------|---------|------------------|
| MySQL | Sync | Booking storage | Retry; fail request |
| Flight Service | Sync | Flight resolution, pricing | Circuit breaker |
| Payment Service | Async | Payment processing | Fire-and-forget; TTL handles timeout |

### 2.5 Failure Modes

| Failure | Detection | Response | Recovery |
|---------|-----------|----------|----------|
| Flight Service down | Connection timeout | Return 503 | Circuit breaker; retry |
| Inventory reservation fails | Error response | Return error to user | No seats: 400; retry: 503 |
| Payment Service down | Connection timeout | Booking stays PENDING | TTL expiry releases seats |
| Callback delivery fails | HTTP error | Log error | Payment service retries |

---

## 3. Payment Service

### 3.1 Responsibility

Mock payment gateway for testing and development.

**Bounded Context:** Payments (Mock)

**Capabilities:**
- Simulated payment processing
- Configurable success/failure rates
- Async callback delivery
- Deterministic testing support

### 3.2 Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       Payment Service                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────┐│
│  │        PaymentController          MockController            ││
│  └──────────────────────────┬──────────────────────────────────┘│
│                             │                                    │
│  ┌──────────────────────────▼──────────────────────────────────┐│
│  │                    Service Layer                             ││
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ ││
│  │  │PaymentService│ │MockConfigSvc │ │  CallbackSender      │ ││
│  │  └──────────────┘ └──────────────┘ └──────────────────────┘ ││
│  └───────────────────────────────────────────────────────────────│
│                             │                                    │
│  ┌──────────────────────────▼──────────────────────────────────┐│
│  │                   Repository Layer                           ││
│  │  ┌──────────────────────────────────────────────────────────┐││
│  │  │         InMemoryPaymentRepository                        │││
│  │  └──────────────────────────────────────────────────────────┘││
│  └──────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 Public APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/payments/process` | Process payment (async) |
| POST | `/v1/payments/process-sync` | Process payment (sync) |
| POST | `/v1/payments/process-with-outcome` | Force specific outcome |
| GET | `/v1/payments/{paymentId}` | Get payment by ID |
| GET | `/v1/payments/booking/{bookingId}` | Get payment by booking |
| GET | `/v1/mock/config` | Get mock configuration |
| PUT | `/v1/mock/config` | Update mock configuration |
| POST | `/v1/mock/force-success` | Force all success |
| POST | `/v1/mock/force-failure` | Force all failure |
| POST | `/v1/mock/reset` | Reset to defaults |

### 3.4 Dependencies

| Dependency | Type | Purpose | Failure Handling |
|------------|------|---------|------------------|
| Booking Service | Async (callback) | Deliver payment result | Retry with default URL |

---

## 4. Database Design

### 4.1 Flight Service Database (flight_db)

#### Table: flights

```sql
CREATE TABLE flights (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    flight_id       VARCHAR(36) NOT NULL UNIQUE,
    source          VARCHAR(10) NOT NULL,
    destination     VARCHAR(10) NOT NULL,
    departure_time  DATETIME NOT NULL,
    arrival_time    DATETIME NOT NULL,
    total_seats     INT NOT NULL,
    available_seats INT NOT NULL,
    price           DECIMAL(10,2) NOT NULL,
    status          ENUM('ACTIVE', 'CANCELLED') DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_source_dest_date (source, destination, departure_time),
    INDEX idx_status (status),
    INDEX idx_departure (departure_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Design Decisions:**
- `flight_id` as business key (UUID-based) separate from auto-increment PK
- Composite index for search queries (source + destination + date)
- `available_seats` denormalized for fast reads (updated transactionally)

#### Table: seat_reservations

```sql
CREATE TABLE seat_reservations (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id  VARCHAR(50) NOT NULL,
    flight_id   VARCHAR(36) NOT NULL,
    seats       INT NOT NULL,
    expires_at  DATETIME NOT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at  DATETIME NULL,
    
    INDEX idx_booking_id (booking_id),
    INDEX idx_flight_id (flight_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_deleted_at (deleted_at),
    
    CONSTRAINT fk_reservation_flight 
        FOREIGN KEY (flight_id) REFERENCES flights(flight_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Design Decisions:**
- `deleted_at` for soft delete (audit trail)
- `expires_at` for TTL-based cleanup
- Foreign key to `flights` for referential integrity

---

### 4.2 Booking Service Database (booking_db)

#### Table: bookings

```sql
CREATE TABLE bookings (
    booking_id       VARCHAR(50) PRIMARY KEY,
    user_id          VARCHAR(50) NOT NULL,
    flight_type      ENUM('DIRECT', 'COMPUTED') NOT NULL,
    flight_identifier VARCHAR(100) NOT NULL,
    no_of_seats      INT NOT NULL,
    total_price      DECIMAL(10,2) NOT NULL,
    status           ENUM('PENDING', 'CONFIRMED', 'FAILED', 'CANCELLED') NOT NULL,
    idempotency_key  VARCHAR(100) UNIQUE,
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### Table: booking_flights

```sql
CREATE TABLE booking_flights (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id  VARCHAR(50) NOT NULL,
    flight_id   VARCHAR(36) NOT NULL,
    leg_order   INT NOT NULL,
    
    INDEX idx_booking_id (booking_id),
    
    CONSTRAINT fk_booking 
        FOREIGN KEY (booking_id) REFERENCES bookings(booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Design Decisions:**
- `idempotency_key` unique constraint prevents duplicate bookings
- `booking_flights` junction table supports multi-leg bookings
- `leg_order` maintains flight sequence for computed routes

---

### 4.3 Indexing Strategy

| Table | Index | Columns | Purpose |
|-------|-------|---------|---------|
| flights | PRIMARY | id | Row lookup |
| flights | UNIQUE | flight_id | Business key lookup |
| flights | idx_source_dest_date | source, destination, departure_time | Search queries |
| flights | idx_status | status | Filter active flights |
| seat_reservations | idx_booking_id | booking_id | Lookup by booking |
| seat_reservations | idx_expires_at | expires_at | Cleanup job |
| bookings | PRIMARY | booking_id | Row lookup |
| bookings | UNIQUE | idempotency_key | Idempotent creates |
| bookings | idx_user_id | user_id | User's bookings |

---

### 4.4 Transaction Boundaries

| Operation | Transaction Scope | Isolation Level |
|-----------|-------------------|-----------------|
| Reserve seats | Single flight UPDATE + INSERT | READ_COMMITTED |
| Multi-flight reserve | All flights in single TX | READ_COMMITTED |
| Confirm reservation | UPDATE seat_reservation | READ_COMMITTED |
| Create booking | INSERT bookings + booking_flights | READ_COMMITTED |
| Cancel flight | UPDATE flight status | READ_COMMITTED |

**Critical Invariant:**
```
flights.available_seats = flights.total_seats 
                        - SUM(confirmed_bookings.seats) 
                        - SUM(active_reservations.seats)
```

---

## 5. API Design Standards

### 5.1 Request/Response Format

**Success Response:**
```json
{
    "data": { ... },
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 100
    }
}
```

**Error Response:**
```json
{
    "error": "ERROR_CODE",
    "message": "Human-readable message",
    "details": { "field": "validation error" },
    "retryable": false,
    "timestamp": "2026-01-22T12:00:00Z"
}
```

### 5.2 Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Invalid input |
| `INVALID_FLIGHT` | 400 | Flight not found or inactive |
| `NO_SEATS_AVAILABLE` | 400 | Insufficient seats |
| `BOOKING_NOT_FOUND` | 404 | Booking doesn't exist |
| `RESERVATION_EXPIRED` | 400 | Reservation TTL expired |
| `LOCK_ACQUISITION_FAILED` | 409 | Concurrent modification |
| `INTERNAL_ERROR` | 500 | Unexpected error |
| `SERVICE_UNAVAILABLE` | 503 | Dependency down |

### 5.3 Idempotency

**Idempotency Key Header:** `X-Idempotency-Key`

**Behavior:**
- If key exists with completed request → return cached response
- If key exists with in-progress request → return 409 Conflict
- If key doesn't exist → process request, store result

**Applicable Endpoints:**
- `POST /v1/bookings`
- `POST /v1/inventory/reserve`

### 5.4 Versioning Strategy

- **URL Path Versioning:** `/v1/`, `/v2/`
- **Breaking changes require new version**
- **Deprecation period:** 6 months

---

## 6. State Management

### 6.1 Booking State Machine

```
                    ┌───────────────┐
                    │   PENDING     │
                    └───────┬───────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
            ▼               ▼               ▼
    ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
    │   CONFIRMED   │ │    FAILED     │ │   CANCELLED   │
    └───────────────┘ └───────────────┘ └───────────────┘
```

**Transitions:**
| From | To | Trigger |
|------|-----|---------|
| - | PENDING | Booking created, awaiting payment |
| PENDING | CONFIRMED | Payment SUCCESS callback |
| PENDING | FAILED | Payment FAILURE/TIMEOUT callback |
| PENDING | CANCELLED | User cancellation (future) |
| CONFIRMED | CANCELLED | User cancellation (future) |

### 6.2 Reservation Lifecycle

```
    ┌─────────┐     TTL Expires      ┌─────────┐
    │ ACTIVE  │ ─────────────────────▶│ EXPIRED │
    └────┬────┘                       └─────────┘
         │                                  │
         │ Payment Success                  │ Cleanup Job
         ▼                                  ▼
    ┌─────────┐                       ┌─────────┐
    │CONFIRMED│                       │ DELETED │
    │(soft del)                       │(soft del)
    └─────────┘                       └─────────┘
```

---

## 7. Caching Architecture

### 7.1 Cache Topology

```
┌─────────────────────────────────────────────────────────────────┐
│                         Redis                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Seat Cache                            │   │
│  │  Key: flight:{flightId}:seats                           │   │
│  │  Value: integer                                          │   │
│  │  TTL: none (invalidated on write)                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   Route Cache                            │   │
│  │  Key: routes:{source}:{dest}:{date}                     │   │
│  │  Value: JSON array of computed routes                   │   │
│  │  TTL: 24 hours                                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  Distributed Locks                       │   │
│  │  Key: inventory:lock:{flightId}                         │   │
│  │  Value: lock owner UUID                                 │   │
│  │  TTL: 30 seconds (auto-release)                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 Cache-Aside Pattern

```
READ:
1. Check Redis for key
2. If hit → return cached value
3. If miss → query DB → write to Redis → return value

WRITE:
1. Acquire distributed lock
2. Update DB (transaction)
3. Update Redis
4. Release lock
```

---

## 8. Retry and Circuit Breaker

### 8.1 Retry Configuration

| Operation | Max Retries | Backoff | Timeout |
|-----------|-------------|---------|---------|
| DB query | 3 | Exponential (100ms base) | 5s |
| Redis operation | 2 | Fixed 50ms | 1s |
| HTTP call (internal) | 2 | Exponential (200ms base) | 10s |
| Lock acquisition | 3 | Fixed 100ms | 5s |

### 8.2 Circuit Breaker (Future)

```
State Transitions:
CLOSED → (failure threshold) → OPEN → (timeout) → HALF_OPEN → (probe success) → CLOSED
                                                            → (probe failure) → OPEN

Configuration:
- Failure threshold: 5 failures in 30 seconds
- Open duration: 30 seconds
- Half-open probes: 3
```
