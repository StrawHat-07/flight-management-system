# Flight Management System

A production-grade microservices-based flight management system built with Spring Boot, MySQL, and Redis.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Flight Management System                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐  │
│  │  Flight Service │    │ Booking Service │    │ Payment Service │  │
│  │     :8081       │    │     :8083       │    │     :8084       │  │
│  │                 │    │                 │    │                 │  │
│  │ • CRUD Flights  │◄───│ • Create Booking│───►│ • Process Pay   │  │
│  │ • Graph Mgmt    │    │ • Block Seats   │    │ • Async Callback│  │
│  │ • Search/Routes │    │ • Idempotency   │    │ • Mock Outcomes │  │
│  └────────┬────────┘    └────────┬────────┘    └─────────────────┘  │
│           │                      │                                    │
│  ┌────────▼────────┐    ┌────────▼────────┐                         │
│  │   MySQL (DB)    │    │   MySQL (DB)    │                         │
│  │  flight_db      │    │  booking_db     │                         │
│  └─────────────────┘    └─────────────────┘                         │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                         Redis                                   │  │
│  │  • Seat Counters: flight:{id}:availableSeats                   │  │
│  │  • Blocked Seats: flight:{id}:blocked:{bookingId} (TTL: 5min)  │  │
│  │  • Route Cache:   computed:{date}:{hops}:{src}_{dest}          │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Design Decisions

### 1. TTL-Based Seat Rollback (No SAGA)
Instead of implementing distributed transactions (SAGA pattern), we use Redis TTL for automatic seat rollback:
- When booking is created, seats are atomically blocked in Redis with a 5-minute TTL
- If payment succeeds: blocked keys are removed, MySQL is updated
- If payment fails/times out: TTL expires, seats are automatically restored

### 2. Atomic Seat Blocking
Uses Redis Lua scripts to ensure atomic check-and-block across multiple flights (for multi-hop routes):

```lua
-- Check all flights have enough seats, then block atomically
for each flight:
    if available < requested: return 0
for each flight:
    DECRBY availableKey seats
    SET blockedKey seats EX ttl
return 1
```

### 3. Route Precomputation
- In-memory graph (Map<Location, List<Flight>>) for efficient route computation
- DFS algorithm finds all valid paths up to N hops
- Routes are cached in Redis for fast search responses
- Background recomputation on flight changes

### 4. Thread-Safe Graph Operations
- ReadWriteLock protects the flight graph
- CopyOnWriteArrayList for concurrent modification safety
- Defensive copies returned to callers

## Services

### Flight Service (Port 8081)
**Responsibilities:**
- Flight CRUD operations
- In-memory flight graph management
- Route search and computation
- Real-time seat availability

**API Endpoints:**
```
# Flight Management
POST   /v1/flights                     - Create flight
GET    /v1/flights                     - List all flights
GET    /v1/flights/{id}                - Get flight by ID
PUT    /v1/flights/{id}                - Update flight
DELETE /v1/flights/{id}                - Delete (soft) flight
GET    /v1/flights/search?src=X&dest=Y - Search by route
GET    /v1/flights/date/{date}         - Search by date
GET    /v1/flights/{id}/available-seats - Get real-time seats

# Search
GET    /v1/search?src=DEL&dest=BLR&date=2026-01-25&seats=2&maxHops=2
POST   /v1/search                      - Search via request body
GET    /v1/search/computed/{id}        - Get computed flight details

# Management
GET    /actuator/health                - Health check
POST   /v1/flights/trigger-recomputation - Trigger route recomputation
```

### Booking Service (Port 8083)
**Responsibilities:**
- Create bookings with atomic seat blocking
- Idempotent booking creation
- Handle payment callbacks
- Manage booking lifecycle

**API Endpoints:**
```
POST   /v1/bookings                    - Create booking
POST   /v1/bookings/book/{flightId}    - Quick book endpoint
GET    /v1/bookings/{id}               - Get booking by ID
GET    /v1/bookings/user/{userId}      - Get user's bookings
POST   /v1/bookings/payment-callback   - Payment service callback

# Management
GET    /actuator/health                - Health check
```

**Request Headers:**
- `Idempotency-Key`: Optional header for idempotent requests

### Payment Service (Port 8084)
**Responsibilities:**
- Mock payment processing
- Async callback to booking service
- Configurable success/failure rates

**API Endpoints:**
```
POST   /v1/payments/process            - Process payment (async)
POST   /v1/payments/process-sync       - Process payment (sync, for testing)
POST   /v1/payments/process-with-outcome?outcome=SUCCESS|FAILURE|TIMEOUT
GET    /v1/payments/{id}               - Get payment by ID
GET    /v1/payments/booking/{bookingId} - Get payment by booking

# Management
GET    /actuator/health                - Health check
```

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local development)
- Maven 3.8+ (for local development)

### Running with Docker Compose

```bash
# Start all services
docker-compose up -d

# Check health
curl http://localhost:8081/actuator/health  # Flight Service
curl http://localhost:8083/actuator/health  # Booking Service
curl http://localhost:8084/actuator/health  # Payment Service

# View logs
docker-compose logs -f
```

### Running Locally (Development)

```bash
# Start infrastructure
docker-compose up -d mysql redis

# Start services (in separate terminals)
cd flight-service && mvn spring-boot:run
cd booking-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
```

## Testing

### Run Unit Tests
```bash
# All services
cd flight-service && mvn test
cd booking-service && mvn test
cd payment-service && mvn test
```

### Run End-to-End Tests
```bash
# Ensure services are running first
./test-flows.sh
```

### Manual Testing

#### 1. Search for Flights
```bash
# Get tomorrow's date
TOMORROW=$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d '+1 day' +%Y-%m-%d)

# Search direct flights
curl "http://localhost:8081/v1/search?src=DEL&dest=BLR&date=$TOMORROW&seats=2&maxHops=1"

# Search with connections
curl "http://localhost:8081/v1/search?src=DEL&dest=MAA&date=$TOMORROW&seats=2&maxHops=3"
```

#### 2. Book a Flight
```bash
# Get available flight
FLIGHT_ID=$(curl -s http://localhost:8081/v1/flights | jq -r '.[0].flightId')

# Create booking with idempotency key
curl -X POST http://localhost:8083/v1/bookings \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: booking-$(date +%s)" \
  -d '{
    "userId": "user123",
    "flightIdentifier": "'$FLIGHT_ID'",
    "seats": 2
  }'
```

#### 3. Check Booking Status
```bash
# Get booking (replace with actual booking ID)
curl http://localhost:8083/v1/bookings/BK12345678

# Get all bookings for user
curl http://localhost:8083/v1/bookings/user/user123
```

#### 4. Test Payment Outcomes
```bash
# Force successful payment
curl -X POST "http://localhost:8084/v1/payments/process-with-outcome?outcome=SUCCESS" \
  -H "Content-Type: application/json" \
  -d '{"bookingId": "test-001", "userId": "user1", "amount": 299.99}'

# Force failed payment
curl -X POST "http://localhost:8084/v1/payments/process-with-outcome?outcome=FAILURE" \
  -H "Content-Type: application/json" \
  -d '{"bookingId": "test-002", "userId": "user1", "amount": 299.99}'
```

## Redis Key Structure

| Key Pattern | Type | Description |
|------------|------|-------------|
| `flight:{flightId}:availableSeats` | Integer | Real-time seat counter |
| `flight:{flightId}:blocked:{bookingId}` | Integer (TTL) | Blocked seats during payment |
| `computed:{date}:{hops}:{src}_{dest}` | JSON Array | Cached computed routes |

## Error Handling

All services return consistent error responses:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable message",
  "details": { "field": "error description" },
  "timestamp": "2026-01-20T10:30:00"
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `FLIGHT_NOT_FOUND` | 404 | Flight does not exist |
| `BOOKING_NOT_FOUND` | 404 | Booking does not exist |
| `VALIDATION_ERROR` | 400 | Invalid input data |
| `NO_SEATS_AVAILABLE` | 409 | Insufficient seats |
| `SERVICE_UNAVAILABLE` | 503 | Downstream service error |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Design Patterns Used

1. **Repository Pattern** - Data access abstraction
2. **Service Layer Pattern** - Business logic separation
3. **DTO Pattern** - Data transfer objects (`*Entry` suffix)
4. **Builder Pattern** - Fluent object construction (Lombok)
5. **Strategy Pattern** - Payment outcome determination
6. **Observer Pattern** - Async payment callbacks
7. **Template Method** - Global exception handling

## Configuration

### Environment Variables

| Variable | Service | Default | Description |
|----------|---------|---------|-------------|
| `SPRING_DATASOURCE_URL` | Flight, Booking | `jdbc:mysql://localhost:3306/..._db` | MySQL URL |
| `SPRING_DATA_REDIS_HOST` | Flight, Booking | `localhost` | Redis host |
| `FLIGHT_SERVICE_URL` | Booking | `http://localhost:8081` | Flight service URL |
| `PAYMENT_SERVICE_URL` | Booking | `http://localhost:8084` | Payment service URL |
| `BOOKING_SERVICE_URL` | Payment | `http://localhost:8083` | Booking service URL |

### Application Properties

```yaml
# Flight Service
search:
  max-hops: 3
  cache-ttl-hours: 24
  min-connection-time-minutes: 60

# Booking Service  
booking:
  seat-block-ttl-minutes: 5

# Payment Service
payment:
  success-probability: 70
  failure-probability: 20
```

## Health Checks

All services expose health endpoints:

```bash
# Detailed health with dependencies
curl http://localhost:8081/actuator/health

# Response includes:
# - Application status
# - Database connectivity
# - Redis connectivity (where applicable)
```

## Observability

- **Metrics**: Available at `/actuator/metrics`
- **Health**: Available at `/actuator/health`
- **Info**: Available at `/actuator/info`
- **Logging**: DEBUG level for `com.flightmanagement` package

## Project Structure

```
flight_management_system/
├── docker-compose.yml           # Full system orchestration
├── test-flows.sh               # E2E test script
├── README.md                   # This file
│
├── flight-service/
│   ├── docker-compose.yml      # Standalone deployment
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/flightmanagement/flight/
│       │   ├── controller/v1/  # REST controllers
│       │   ├── service/        # Business logic
│       │   ├── repository/     # Data access
│       │   ├── model/          # JPA entities
│       │   ├── dto/            # DTOs (*Entry)
│       │   ├── enums/          # Enumerations
│       │   ├── config/         # Configuration
│       │   └── exception/      # Exception handling
│       └── resources/
│           ├── application.yml
│           └── db/migration/   # Flyway migrations
│
├── booking-service/
│   └── ... (similar structure)
│
└── payment-service/
    └── ... (similar structure)
```

## License

This project is for demonstration purposes.
