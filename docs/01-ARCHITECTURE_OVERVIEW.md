# Flight Management System - Architecture Overview

**Version:** 1.0  
**Last Updated:** January 2026  
**Author:** Engineering Team  
**Status:** Production Ready

---

## 1. Problem Statement & Business Context

### Business Problem

Airlines and travel platforms need a reliable system to:
1. Allow customers to search for flights across multiple routes (direct and connecting)
2. Handle concurrent booking requests without overbooking
3. Process payments with proper inventory rollback on failures
4. Manage flight schedules dynamically

### Why This Matters

- **Revenue Impact**: Overbooking leads to compensation costs and brand damage
- **Customer Experience**: Slow search or failed bookings cause customer churn
- **Operational Efficiency**: Manual flight management doesn't scale

### Scope

This system handles domestic flight operations for a mid-to-large airline with:
- ~100 daily flights across 7+ cities
- Peak booking load of 1000+ concurrent users
- Real-time inventory synchronization

---

## 2. High-Level Goals and Non-Goals

### Goals

| ID | Goal | Success Metric |
|----|------|----------------|
| G1 | Accurate real-time seat availability | Zero overbookings |
| G2 | Sub-second search response | P99 < 500ms |
| G3 | Reliable booking with payment integration | 99.9% success rate for valid requests |
| G4 | Support multi-hop route discovery | Find routes with up to 3 connections |
| G5 | Graceful handling of payment failures | 100% inventory release on failure |

### Non-Goals (Out of Scope)

| ID | Non-Goal | Rationale |
|----|----------|-----------|
| NG1 | International flights | Different regulatory requirements |
| NG2 | Seat selection | Phase 2 feature |
| NG3 | Loyalty/rewards integration | Separate bounded context |
| NG4 | Real payment gateway integration | Mock service sufficient for MVP |
| NG5 | Multi-currency pricing | Domestic only |

---

## 3. System Context Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL ACTORS                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│    ┌──────────┐         ┌──────────┐         ┌──────────────┐              │
│    │ Customer │         │  Admin   │         │ External     │              │
│    │   App    │         │  Portal  │         │ Systems      │              │
│    └────┬─────┘         └────┬─────┘         └──────┬───────┘              │
│         │                    │                      │                       │
└─────────┼────────────────────┼──────────────────────┼───────────────────────┘
          │                    │                      │
          ▼                    ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         FLIGHT MANAGEMENT SYSTEM                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │  Flight Service │  │ Booking Service │  │ Payment Service │             │
│  │     :8081       │  │     :8083       │  │     :8084       │             │
│  │                 │  │                 │  │                 │             │
│  │ • Search        │  │ • Create Booking│  │ • Process       │             │
│  │ • Inventory     │  │ • Payment Orch  │  │   Payment       │             │
│  │ • Flight CRUD   │  │ • Status Mgmt   │  │ • Callback      │             │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘             │
│           │                    │                    │                       │
│           └────────────────────┼────────────────────┘                       │
│                                │                                            │
│                    ┌───────────┴───────────┐                               │
│                    ▼                       ▼                               │
│           ┌──────────────┐        ┌──────────────┐                         │
│           │    MySQL     │        │    Redis     │                         │
│           │  (Primary)   │        │   (Cache)    │                         │
│           │              │        │              │                         │
│           │ • flights    │        │ • seat cache │                         │
│           │ • bookings   │        │ • dist locks │                         │
│           │ • seat_resv  │        │ • routes     │                         │
│           └──────────────┘        └──────────────┘                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Key Architectural Decisions

### ADR-001: Microservices over Monolith

**Decision**: Split into 3 services (Flight, Booking, Payment)

**Context**: Need independent scaling and deployment of search-heavy vs write-heavy workloads

**Consequences**:
- (+) Independent scaling of search (read-heavy) vs booking (write-heavy)
- (+) Team autonomy and clear ownership
- (+) Fault isolation
- (-) Distributed transaction complexity
- (-) Network latency between services

---

### ADR-002: DB-First Inventory with Redis Cache

**Decision**: MySQL is source of truth; Redis is synchronized cache

**Context**: Need strong consistency for seat inventory while maintaining fast reads

**Alternatives Considered**:
| Option | Pros | Cons |
|--------|------|------|
| Redis-only | Fast | Data loss risk, no ACID |
| DB-only | Consistent | Slow reads under load |
| **DB-first + Redis sync** | **Consistent + Fast** | **Sync complexity** |

**Implementation**:
```
WRITE: DB Transaction → Sync to Redis → Release Lock
READ: Redis (cache hit) || DB (cache miss) → Populate Redis
```

**Consequences**:
- (+) ACID guarantees for inventory
- (+) Fast search with cached availability
- (-) Brief inconsistency window during sync (acceptable: ~10ms)

---

### ADR-003: Decrement Seats on Reservation (not Confirmation)

**Decision**: Reduce `available_seats` immediately when reservation is created

**Context**: Users searching for flights should see accurate availability

**Alternatives Considered**:
| Option | Behavior | Issue |
|--------|----------|-------|
| Decrement on confirm | Seats show available until payment | Users see stale availability |
| **Decrement on reserve** | **Seats reflect pending bookings** | **Requires cleanup for abandoned** |

**Implementation**:
- RESERVE: Decrement DB → Create `seat_reservation` record → Sync Redis
- CONFIRM: Soft-delete `seat_reservation` (seats stay decremented)
- RELEASE/EXPIRE: Increment DB → Soft-delete reservation → Sync Redis

**Consequences**:
- (+) Accurate search results
- (-) Need background job to clean expired reservations

---

### ADR-004: Distributed Locks for Inventory Operations

**Decision**: Redis-based distributed locks per flight ID

**Context**: Concurrent booking requests for same flight must not cause race conditions

**Implementation**:
```java
lock = acquireLock("inventory:lock:" + flightId, timeout=5s)
try {
    // DB transaction
    // Redis sync
} finally {
    releaseLock(lock)
}
```

**Consequences**:
- (+) Prevents double-booking
- (+) Serializes writes per flight (not globally)
- (-) Lock contention under extreme load (mitigated by short lock duration)

---

### ADR-005: Async Payment with Callback

**Decision**: Payment processing is asynchronous; result delivered via webhook

**Context**: Payment gateways have variable latency; blocking hurts UX

**Flow**:
```
Booking Service → Payment Service (async)
                        ↓
               [Process Payment]
                        ↓
Payment Service → Booking Service (callback)
                        ↓
               [Update Booking Status]
               [Confirm/Release Inventory]
```

**Consequences**:
- (+) Non-blocking booking creation
- (+) Handles payment gateway timeouts gracefully
- (-) Eventual consistency for booking status

---

### ADR-006: Soft Delete for Audit Trail

**Decision**: Use `deleted_at` timestamp instead of hard DELETE

**Context**: Need audit trail for reservations; debugging production issues

**Implementation**:
- All queries include `WHERE deleted_at IS NULL`
- Cleanup jobs set `deleted_at = NOW()` instead of DELETE
- Historical data available for analysis

**Consequences**:
- (+) Full audit trail
- (+) Easy debugging of "where did seats go" issues
- (-) Slightly larger tables (mitigated by archival)

---

## 5. Service Boundaries and Ownership

| Service | Ownership | Responsibilities | Data Owned |
|---------|-----------|------------------|------------|
| **Flight Service** | Flight Operations Team | Flight CRUD, Search, Inventory | `flights`, `seat_reservations` |
| **Booking Service** | Commerce Team | Booking lifecycle, Payment orchestration | `bookings`, `booking_flights` |
| **Payment Service** | Payments Team | Payment processing, Callbacks | In-memory (mock) |

### Service Communication

```
┌─────────────┐      REST (sync)       ┌─────────────┐
│   Booking   │ ──────────────────────▶│   Flight    │
│   Service   │                        │   Service   │
└─────────────┘                        └─────────────┘
       │                                      │
       │ REST (async)                         │
       ▼                                      │
┌─────────────┐      REST (callback)          │
│   Payment   │ ──────────────────────────────┘
│   Service   │        (via Booking)
└─────────────┘
```

---

## 6. Data Flow Summary

### Search Flow
```
User → Flight Service → [Check Redis Cache]
                              ↓ (miss)
                        [Query MySQL]
                              ↓
                        [Populate Cache]
                              ↓
                        [Apply Filters]
                              ↓
                        [Sort Results]
                              ↓
                        Response
```

### Booking Flow
```
User → Booking Service → [Resolve Flight IDs]
                              ↓
                        [Reserve Inventory] → Flight Service
                              ↓
                        [Save Booking]
                              ↓
                        [Initiate Payment] → Payment Service
                              ↓
                        Response (PENDING)
                              
Payment Service → [Process] → [Callback] → Booking Service
                                                ↓
                                    [Confirm/Release Inventory]
                                                ↓
                                    [Update Booking Status]
```

---

## 7. Scalability Considerations

### Current Capacity Planning

| Metric | Current | Scalable To |
|--------|---------|-------------|
| Flights/day | 100 | 10,000 |
| Concurrent searches | 500 | 50,000 |
| Bookings/minute | 100 | 10,000 |
| Data retention | 90 days | Unlimited (with archival) |

### Horizontal Scaling Strategy

| Component | Scaling Approach |
|-----------|------------------|
| Flight Service | Stateless; add replicas behind LB |
| Booking Service | Stateless; add replicas behind LB |
| Payment Service | Stateless; add replicas behind LB |
| MySQL | Read replicas for search; Primary for writes |
| Redis | Redis Cluster for sharding |

### Bottleneck Mitigation

| Bottleneck | Mitigation |
|------------|------------|
| Search latency | Route precomputation, Redis caching |
| Inventory contention | Per-flight locking (not global) |
| Payment latency | Async processing |
| DB connections | Connection pooling (HikariCP) |

---

## 8. Reliability Considerations

### Failure Modes and Handling

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Redis down | Slow searches | Fallback to DB; graceful degradation |
| MySQL down | Service unavailable | Retry with backoff; circuit breaker |
| Payment timeout | Booking stuck | TTL-based expiry; background cleanup |
| Network partition | Stale data | Health checks; leader election for locks |

### Recovery Mechanisms

1. **Expired Reservation Cleanup**: Scheduled job every 10 seconds
2. **Inventory Reconciliation**: Can be triggered manually if drift detected
3. **Idempotency**: All write operations support idempotent retries

---

## 9. Extensibility Points

| Extension | How to Implement |
|-----------|------------------|
| Add new city | Insert flight records; graph rebuilds automatically |
| New payment provider | Implement `PaymentProcessor` interface |
| International flights | Add currency/timezone to `flights` table |
| Seat selection | Add `seat_map` table; extend reservation |
| Notifications | Add event listeners on booking status change |

---

## 10. Security Considerations

| Concern | Current State | Production Recommendation |
|---------|---------------|---------------------------|
| Authentication | None (demo) | JWT/OAuth2 |
| Authorization | None | RBAC for admin operations |
| Data encryption | None | TLS in transit; encryption at rest |
| Input validation | Bean Validation | Add rate limiting |
| Secrets management | application.yml | Vault/AWS Secrets Manager |

---

## 11. Glossary

| Term | Definition |
|------|------------|
| **Direct Flight** | Single-leg journey from source to destination |
| **Computed Flight** | Multi-hop journey with connections |
| **Reservation** | Temporary seat hold pending payment |
| **Booking** | Confirmed purchase after successful payment |
| **Inventory** | Available seat count for a flight |
| **TTL** | Time-To-Live for reservation expiry |

---

## Appendix: Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Runtime | Java | 17 |
| Framework | Spring Boot | 3.x |
| Database | MySQL | 8.0 |
| Cache | Redis | 7.x |
| Build | Maven | 3.9 |
| Container | Docker | 20.x |
| Orchestration | Docker Compose | 1.29 |
