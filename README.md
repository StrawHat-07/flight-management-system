# Flight Management System

A microservices-based flight booking platform demonstrating production-grade patterns for search, booking, inventory management, and payment processing.

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- MySQL 8.0 (via Docker)
- Redis 7+ (via Docker)

### Option 1: Docker (Recommended)

```bash
# Clone and navigate to project
cd flight_management_system

# Start all services
docker-compose up -d

# Verify services are running
docker-compose ps

# Check health
curl http://localhost:8081/actuator/health  # Flight Service
curl http://localhost:8083/actuator/health  # Booking Service
curl http://localhost:8084/actuator/health  # Payment Service
```

### Option 2: Local Development

```bash
# 1. Start infrastructure (MySQL + Redis)
docker-compose up -d mysql redis

# 2. Start services (in separate terminals)
cd flight-service && mvn spring-boot:run
cd booking-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
```

### Service Ports
| Service | Port | Description |
|---------|------|-------------|
| Flight Service | 8081 | Flights, Search, Inventory |
| Booking Service | 8083 | Booking orchestration |
| Payment Service | 8084 | Mock payment processing |
| MySQL | 3306 | Persistent storage |
| Redis | 6379 | Caching & distributed locks |

---

## Testing the Flows

### 1. Search Flights

```bash
# Direct flight search
curl "http://localhost:8081/v1/search?source=DEL&destination=BLR&date=2026-01-23&seats=2&maxHops=1"

# Multi-hop search (connecting flights)
curl "http://localhost:8081/v1/search?source=DEL&destination=GOI&date=2026-01-23&seats=2&maxHops=3"

# Sort by price
curl "http://localhost:8081/v1/search?source=DEL&destination=BLR&date=2026-01-23&seats=1&maxHops=3&sortBy=price&sortDirection=asc"
```

### 2. Create Booking (End-to-End)

```bash
# Configure payment to succeed (for testing)
curl -X POST http://localhost:8084/v1/mock/force-success

# Create booking
curl -X POST http://localhost:8083/v1/bookings \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-001","flightIdentifier":"FL201","seats":2}'

# Check booking status (should be CONFIRMED after payment callback)
curl http://localhost:8083/v1/bookings/{bookingId}
```

### 3. Test Payment Failure

```bash
# Configure payment to fail
curl -X POST http://localhost:8084/v1/mock/force-failure

# Create booking (will fail and release inventory)
curl -X POST http://localhost:8083/v1/bookings \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-002","flightIdentifier":"FL201","seats":2}'
```

### 4. Flight Management

```bash
# Add new flight
curl -X POST http://localhost:8081/v1/flights \
  -H "Content-Type: application/json" \
  -d '{"source":"DEL","destination":"CCU","departureTime":"2026-01-25T10:00:00","arrivalTime":"2026-01-25T12:30:00","totalSeats":150,"price":5500.00}'

# Cancel flight
curl -X DELETE http://localhost:8081/v1/flights/{flightId}

# Check inventory
curl http://localhost:8081/v1/flights/{flightId}/available-seats
```

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Client                                     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
            │   Flight     │ │   Booking    │ │   Payment    │
            │   Service    │ │   Service    │ │   Service    │
            │   :8081      │ │   :8083      │ │   :8084      │
            └──────┬───────┘ └──────┬───────┘ └──────────────┘
                   │                │                │
                   │     ┌──────────┴────────┐       │
                   │     │                   │       │
                   ▼     ▼                   ▼       │
            ┌──────────────┐          ┌──────────────┐
            │    MySQL     │          │    Redis     │
            │   (flights,  │          │  (cache,     │
            │   bookings)  │          │   locks)     │
            └──────────────┘          └──────────────┘
```

---

## Core Flows

### 1. Search Flow

```
User Request → SearchController → SearchService
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                      ▼
              Direct Flights                        Computed Routes
              (from DB)                            (from Redis cache)
                    │                                      │
                    └──────────────────┬───────────────────┘
                                       ▼
                              Merge, Filter by Seats
                                       │
                                       ▼
                              Sort (price/duration)
                                       │
                                       ▼
                              Paginated Response
```

**Key Features:**
- Multi-hop route finding using DFS algorithm
- Precomputed routes cached in Redis (refreshed every 6 hours)
- Seat availability filtering
- Sorting by price or duration
- Minimum connection time validation (60 min)

### 2. Booking Flow

```
                          ┌─────────────────────────────────────┐
                          │         Booking Service             │
                          └─────────────────────────────────────┘
                                          │
    ┌─────────────────────────────────────┼─────────────────────────────────────┐
    │                                     │                                     │
    ▼                                     ▼                                     ▼
┌─────────┐                        ┌─────────────┐                       ┌───────────┐
│ Resolve │                        │   Reserve   │                       │  Initiate │
│ Flight  │───────────────────────▶│   Seats     │──────────────────────▶│  Payment  │
│  IDs    │                        │ (Inventory) │                       │  (Async)  │
└─────────┘                        └─────────────┘                       └───────────┘
    │                                     │                                     │
    │                                     │                                     │
    ▼                                     ▼                                     ▼
Flight Service                    Flight Service                        Payment Service
(GET flight IDs)                  (POST /inventory/reserve)             (POST /payments/process)
                                          │
                                          ▼
                                  ┌───────────────┐
                                  │ DB: Decrement │
                                  │ available_seats│
                                  └───────────────┘
                                          │
                                          ▼
                                  ┌───────────────┐
                                  │ DB: Create    │
                                  │ seat_reservation│
                                  └───────────────┘
                                          │
                                          ▼
                                  ┌───────────────┐
                                  │ Redis: Sync   │
                                  │ cache         │
                                  └───────────────┘
```

**Payment Callback:**
```
Payment Service ──(callback)──▶ Booking Service
                                      │
                    ┌─────────────────┴─────────────────┐
                    ▼                                   ▼
              SUCCESS                              FAILURE
                    │                                   │
                    ▼                                   ▼
        ┌───────────────────┐              ┌───────────────────┐
        │ Confirm Inventory │              │ Release Inventory │
        │ (soft delete      │              │ (restore seats,   │
        │  reservation)     │              │  soft delete)     │
        └───────────────────┘              └───────────────────┘
                    │                                   │
                    ▼                                   ▼
        Booking: CONFIRMED                 Booking: FAILED
```

### 3. Inventory Management

**DB-First with Redis Cache:**
```
┌─────────────────────────────────────────────────────────────┐
│                    Inventory Operation                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Acquire Distributed│
                    │ Lock (Redis)       │
                    └───────────────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ DB Transaction    │
                    │ (UPDATE flights   │
                    │  SET available_   │
                    │  seats = ...)     │
                    └───────────────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Sync Redis Cache  │
                    │ (SET flight:seats)│
                    └───────────────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Release Lock      │
                    └───────────────────┘
```

**Concurrency Guarantees:**
- Distributed locks prevent race conditions
- DB is source of truth
- Redis synced after every DB write
- Soft delete for audit trail
- Background job cleans expired reservations

---

## Key Design Decisions

### 1. Inventory Strategy: Decrement on Reserve
- Seats decremented immediately on reservation (not on confirm)
- Provides accurate availability for search results
- Released if payment fails or TTL expires

### 2. Soft Delete for Reservations
- `seat_reservations` table uses `deleted_at` timestamp
- Enables audit trail and debugging
- Background job marks expired reservations as deleted

### 3. Mock Payment Service
- Configurable success/failure rates
- Runtime configuration via `/v1/mock/*` endpoints
- Supports deterministic testing

### 4. Multi-hop Route Precomputation
- DFS algorithm finds all valid routes
- Cached in Redis for fast retrieval
- Refreshed every 6 hours via scheduled job

---

## Project Structure

```
flight_management_system/
├── docker-compose.yml          # Full stack orchestration
├── flight-service/             # Flight & Inventory management
│   ├── controller/v1/
│   │   ├── FlightController    # CRUD operations
│   │   ├── SearchController    # Search API
│   │   └── InventoryController # Reserve/Confirm/Release
│   ├── service/
│   │   ├── SearchService       # Search logic
│   │   ├── InventoryService    # DB operations
│   │   ├── InventoryReservationService  # Orchestration
│   │   ├── CacheService        # Redis operations
│   │   └── DistributedLockService
│   └── resources/db/migration/ # Flyway migrations
│
├── booking-service/            # Booking orchestration
│   ├── controller/v1/
│   │   └── BookingController   # Booking API
│   ├── service/
│   │   ├── BookingService      # Main logic
│   │   ├── PaymentOrchestrator # Payment coordination
│   │   └── PricingService      # Price calculation
│   └── client/
│       ├── FlightServiceClient
│       ├── InventoryClient
│       └── PaymentServiceClient
│
└── payment-service/            # Mock payment gateway
    ├── controller/v1/
    │   ├── PaymentController   # Payment API
    │   └── MockController      # Test configuration
    └── service/
        ├── PaymentService      # Processing logic
        └── MockConfigurationService
```

---

## API Reference

### Flight Service (8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/flights/all` | List all active flights |
| GET | `/v1/flights/{id}` | Get flight details |
| POST | `/v1/flights` | Create new flight |
| DELETE | `/v1/flights/{id}` | Cancel flight |
| GET | `/v1/search` | Search flights |
| GET | `/v1/flights/{id}/available-seats` | Check availability |
| POST | `/v1/inventory/reserve` | Reserve seats |
| POST | `/v1/inventory/confirm` | Confirm reservation |
| DELETE | `/v1/inventory/release/{bookingId}` | Release seats |

### Booking Service (8083)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/bookings` | Create booking |
| GET | `/v1/bookings/{id}` | Get booking |
| GET | `/v1/bookings/user/{userId}` | User's bookings |
| POST | `/v1/bookings/payment-callback` | Payment webhook |

### Payment Service (8084)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/payments/process` | Process (async) |
| POST | `/v1/payments/process-sync` | Process (sync) |
| POST | `/v1/payments/process-with-outcome` | Force outcome |
| GET | `/v1/payments/{id}` | Get payment |
| POST | `/v1/mock/force-success` | Force all success |
| POST | `/v1/mock/force-failure` | Force all failure |
| POST | `/v1/mock/reset` | Reset to defaults |

---

## Testing

### Run Unit Tests
```bash
cd flight-service && mvn test
cd booking-service && mvn test
cd payment-service && mvn test
```

### E2E Test Script
```bash
# 1. Ensure services are running
# 2. Configure payment for success
curl -X POST http://localhost:8084/v1/mock/force-success

# 3. Search for flights
curl "http://localhost:8081/v1/search?source=DEL&destination=BLR&date=2026-01-23&seats=2"

# 4. Create booking
curl -X POST http://localhost:8083/v1/bookings \
  -H "Content-Type: application/json" \
  -d '{"userId":"test-user","flightIdentifier":"FL201","seats":2}'

# 5. Verify booking confirmed
curl http://localhost:8083/v1/bookings/{bookingId}

# 6. Check inventory decreased
curl http://localhost:8081/v1/flights/FL201/available-seats
```

---

## Configuration

### Environment Variables

| Variable | Service | Default |
|----------|---------|---------|
| `SPRING_DATASOURCE_URL` | flight, booking | jdbc:mysql://localhost:3306/... |
| `SPRING_DATA_REDIS_HOST` | flight, booking | localhost |
| `FLIGHT_SERVICE_URL` | booking | http://localhost:8081 |
| `PAYMENT_SERVICE_URL` | booking | http://localhost:8084 |
| `BOOKING_SERVICE_URL` | payment | http://localhost:8083 |

---

## Technologies

- **Java 17** + Spring Boot 3.x
- **MySQL 8.0** - Persistent storage
- **Redis 7** - Caching & distributed locks
- **Flyway** - Database migrations
- **Docker** - Containerization
- **Maven** - Build tool

---

## License

MIT License
