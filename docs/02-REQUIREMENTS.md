# Flight Management System - Requirements Specification

**Version:** 1.0  
**Last Updated:** January 2026  
**Status:** Approved

---

## 1. Functional Requirements

### 1.1 Flight Search (FR-SEARCH)

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-SEARCH-01 | System shall return direct flights matching source, destination, and date | P0 | Implemented |
| FR-SEARCH-02 | System shall discover and return multi-hop routes (up to 3 connections) | P0 | Implemented |
| FR-SEARCH-03 | System shall filter results by minimum seat availability | P0 | Implemented |
| FR-SEARCH-04 | System shall sort results by price (ascending/descending) | P1 | Implemented |
| FR-SEARCH-05 | System shall sort results by duration (ascending/descending) | P1 | Implemented |
| FR-SEARCH-06 | System shall enforce minimum connection time (60 minutes) between flights | P1 | Implemented |
| FR-SEARCH-07 | System shall paginate search results (default: 20 per page) | P2 | Implemented |
| FR-SEARCH-08 | System shall precompute popular routes for faster response | P1 | Implemented |

**Acceptance Criteria (FR-SEARCH-02):**
```
GIVEN flights DEL→BOM and BOM→BLR exist
WHEN user searches DEL→BLR with maxHops=2
THEN system returns computed route CF_DEL_BLR_xxx with both legs
```

---

### 1.2 Flight Management (FR-FLIGHT)

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-FLIGHT-01 | Admin shall create new flight schedules with source, destination, times, seats, price | P0 | Implemented |
| FR-FLIGHT-02 | Admin shall cancel existing flights (soft delete, status=CANCELLED) | P0 | Implemented |
| FR-FLIGHT-03 | Cancelled flights shall not appear in search results | P0 | Implemented |
| FR-FLIGHT-04 | System shall auto-generate unique flight IDs | P1 | Implemented |
| FR-FLIGHT-05 | System shall validate flight data (departure before arrival, positive seats) | P1 | Implemented |
| FR-FLIGHT-06 | System shall return all active flights for admin view | P2 | Implemented |

**Acceptance Criteria (FR-FLIGHT-03):**
```
GIVEN flight FL123 is ACTIVE and appears in search
WHEN admin cancels FL123
THEN FL123 status becomes CANCELLED
AND FL123 does not appear in subsequent searches
```

---

### 1.3 Booking (FR-BOOK)

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-BOOK-01 | User shall create booking for direct flight by flight ID | P0 | Implemented |
| FR-BOOK-02 | User shall create booking for computed route by computed flight ID | P0 | Implemented |
| FR-BOOK-03 | System shall reserve seats immediately upon booking creation | P0 | Implemented |
| FR-BOOK-04 | Booking shall have status: PENDING → CONFIRMED or FAILED | P0 | Implemented |
| FR-BOOK-05 | System shall support idempotent booking creation via idempotency key | P0 | Implemented |
| FR-BOOK-06 | User shall retrieve booking by booking ID | P1 | Implemented |
| FR-BOOK-07 | User shall list all bookings by user ID | P1 | Implemented |
| FR-BOOK-08 | System shall calculate total price based on flight prices and seat count | P1 | Implemented |

**Acceptance Criteria (FR-BOOK-03):**
```
GIVEN FL201 has 100 available seats
WHEN user creates booking for 2 seats
THEN seat_reservations record is created
AND FL201.available_seats becomes 98
AND booking status is PENDING
```

---

### 1.4 Inventory Management (FR-INV)

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-INV-01 | System shall reserve seats with configurable TTL (default: 5 minutes) | P0 | Implemented |
| FR-INV-02 | System shall confirm reservation on successful payment | P0 | Implemented |
| FR-INV-03 | System shall release seats on payment failure | P0 | Implemented |
| FR-INV-04 | System shall auto-release expired reservations via background job | P0 | Implemented |
| FR-INV-05 | System shall prevent overbooking under concurrent requests | P0 | Implemented |
| FR-INV-06 | System shall return current available seats for a flight | P1 | Implemented |
| FR-INV-07 | System shall support multi-flight atomic reservation | P1 | Implemented |

**Acceptance Criteria (FR-INV-05):**
```
GIVEN FL201 has 10 available seats
WHEN 20 concurrent requests each try to reserve 1 seat
THEN exactly 10 reservations succeed
AND exactly 10 reservations fail with NO_SEATS_AVAILABLE
AND FL201.available_seats is 0
```

---

### 1.5 Payment Processing (FR-PAY)

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-PAY-01 | System shall process payment asynchronously | P0 | Implemented |
| FR-PAY-02 | System shall send callback to booking service on completion | P0 | Implemented |
| FR-PAY-03 | System shall support forced outcome for testing (SUCCESS/FAILURE/TIMEOUT) | P1 | Implemented |
| FR-PAY-04 | System shall support configurable success/failure probabilities | P1 | Implemented |
| FR-PAY-05 | System shall provide payment status by payment ID or booking ID | P2 | Implemented |
| FR-PAY-06 | System shall handle idempotent payment requests | P1 | Implemented |

---

### 1.6 Edge Cases and Failure Scenarios

| Scenario | Expected Behavior | Status |
|----------|-------------------|--------|
| Search for non-existent route | Return empty results with 200 OK | Implemented |
| Book with insufficient seats | Return 400 with NO_SEATS_AVAILABLE | Implemented |
| Book with invalid flight ID | Return 400 with INVALID_FLIGHT | Implemented |
| Duplicate booking (same idempotency key) | Return existing booking | Implemented |
| Payment timeout | Booking remains PENDING; TTL expiry releases seats | Implemented |
| Payment failure | Booking status → FAILED; seats released | Implemented |
| Concurrent booking for last seat | One succeeds, others fail | Implemented |
| Cancel flight with pending bookings | Flight cancelled; bookings unaffected (manual handling) | Implemented |
| Redis unavailable | Fallback to DB; degraded performance | Implemented |

---

## 2. Non-Functional Requirements

### 2.1 Performance (NFR-PERF)

| ID | Requirement | Target | Measurement |
|----|-------------|--------|-------------|
| NFR-PERF-01 | Search API P50 latency | < 100ms | Prometheus histogram |
| NFR-PERF-02 | Search API P99 latency | < 500ms | Prometheus histogram |
| NFR-PERF-03 | Booking creation P99 latency | < 2s | Prometheus histogram |
| NFR-PERF-04 | Inventory operation P99 latency | < 500ms | Prometheus histogram |
| NFR-PERF-05 | Concurrent search throughput | > 500 req/s per instance | Load test |
| NFR-PERF-06 | Concurrent booking throughput | > 100 req/s per instance | Load test |

**Assumptions:**
- Single MySQL primary (8 vCPU, 32GB RAM)
- Redis cluster (3 nodes, 4GB each)
- 3 replicas per service

---

### 2.2 Scalability (NFR-SCALE)

| ID | Requirement | Target | Strategy |
|----|-------------|--------|----------|
| NFR-SCALE-01 | Horizontal scaling of services | 1 to 20 instances | Stateless design |
| NFR-SCALE-02 | Database read scaling | 5 read replicas | MySQL replication |
| NFR-SCALE-03 | Cache scaling | 10x current capacity | Redis Cluster |
| NFR-SCALE-04 | Handle 10x traffic spike | Within 5 minutes | Auto-scaling policies |

**Growth Projections:**
| Metric | Current | 6 Months | 12 Months |
|--------|---------|----------|-----------|
| Daily flights | 100 | 500 | 2,000 |
| Daily searches | 50,000 | 250,000 | 1,000,000 |
| Daily bookings | 5,000 | 25,000 | 100,000 |
| Data size (MySQL) | 1 GB | 10 GB | 50 GB |

---

### 2.3 Availability (NFR-AVAIL)

| ID | Requirement | Target | Measurement |
|----|-------------|--------|-------------|
| NFR-AVAIL-01 | System uptime | 99.9% (8.7h downtime/year) | Uptime monitoring |
| NFR-AVAIL-02 | Mean Time To Recovery (MTTR) | < 15 minutes | Incident tracking |
| NFR-AVAIL-03 | Zero data loss on failure | RPO = 0 | Sync replication |
| NFR-AVAIL-04 | Graceful degradation when Redis down | Search still works (slower) | Circuit breaker |

**Deployment Strategy:**
- Blue-green deployments for zero-downtime releases
- Health checks with 30s intervals
- Auto-restart on failure (container orchestration)

---

### 2.4 Consistency (NFR-CONS)

| ID | Requirement | Guarantee | Implementation |
|----|-------------|-----------|----------------|
| NFR-CONS-01 | Inventory updates | Strong consistency | DB-first + distributed lock |
| NFR-CONS-02 | Search results | Eventual consistency (< 1s lag) | Redis sync after DB write |
| NFR-CONS-03 | Booking status | Eventual consistency (< 5s lag) | Async callback |
| NFR-CONS-04 | No overbooking | Strict | Serialized writes per flight |

**CAP Trade-offs:**
- Inventory: CP (Consistency + Partition tolerance)
- Search: AP (Availability + Partition tolerance) with eventual consistency

---

### 2.5 Security (NFR-SEC)

| ID | Requirement | Target | Current Status |
|----|-------------|--------|----------------|
| NFR-SEC-01 | Authentication | JWT/OAuth2 | Not implemented (demo) |
| NFR-SEC-02 | Authorization | RBAC | Not implemented (demo) |
| NFR-SEC-03 | Transport encryption | TLS 1.3 | Not implemented (demo) |
| NFR-SEC-04 | Data encryption at rest | AES-256 | Not implemented |
| NFR-SEC-05 | Input validation | All endpoints | Implemented |
| NFR-SEC-06 | SQL injection prevention | Parameterized queries | Implemented (JPA) |
| NFR-SEC-07 | Rate limiting | 100 req/s per client | Not implemented |
| NFR-SEC-08 | Audit logging | All state changes | Partial (soft delete) |

**Production Recommendations:**
```
1. Add API Gateway (Kong/AWS API Gateway) for auth and rate limiting
2. Enable TLS termination at load balancer
3. Use managed secrets (Vault, AWS Secrets Manager)
4. Enable database audit logging
```

---

### 2.6 Observability (NFR-OBS)

| ID | Requirement | Implementation | Status |
|----|-------------|----------------|--------|
| NFR-OBS-01 | Health endpoints | `/actuator/health` | Implemented |
| NFR-OBS-02 | Metrics exposure | `/actuator/metrics` | Implemented |
| NFR-OBS-03 | Structured logging | JSON format (Logback) | Partial |
| NFR-OBS-04 | Distributed tracing | Zipkin/Jaeger integration | Not implemented |
| NFR-OBS-05 | Error tracking | Sentry/Rollbar integration | Not implemented |

**Key Metrics to Monitor:**
| Metric | Type | Alert Threshold |
|--------|------|-----------------|
| `http_requests_total` | Counter | N/A |
| `http_request_duration_seconds` | Histogram | P99 > 1s |
| `db_connections_active` | Gauge | > 80% of pool |
| `redis_operations_failed` | Counter | > 10/min |
| `inventory_overbooking_attempts` | Counter | > 0 |
| `reservations_expired` | Counter | > 100/min |

---

### 2.7 Maintainability (NFR-MAINT)

| ID | Requirement | Target | Implementation |
|----|-------------|--------|----------------|
| NFR-MAINT-01 | Code coverage | > 70% | Unit tests |
| NFR-MAINT-02 | API versioning | URL path (/v1/) | Implemented |
| NFR-MAINT-03 | Database migrations | Automated (Flyway) | Implemented |
| NFR-MAINT-04 | Configuration externalization | Environment variables | Implemented |
| NFR-MAINT-05 | Documentation | OpenAPI/Swagger | Partial |

---

### 2.8 Cost Considerations (NFR-COST)

**Infrastructure Estimates (AWS):**

| Component | Instance Type | Monthly Cost |
|-----------|---------------|--------------|
| Flight Service (3x) | t3.medium | $90 |
| Booking Service (3x) | t3.medium | $90 |
| Payment Service (2x) | t3.small | $30 |
| MySQL (RDS) | db.r5.large | $200 |
| Redis (ElastiCache) | cache.r5.large | $150 |
| Load Balancer | ALB | $50 |
| **Total** | | **~$610/month** |

**Cost Optimization Strategies:**
1. Reserved instances for predictable workloads (30% savings)
2. Spot instances for non-critical batch jobs
3. Right-sizing based on actual utilization metrics
4. Redis TTL to limit cache size

---

## 3. Requirements Traceability Matrix

| Use Case | Functional Req | Non-Functional Req | Test Coverage |
|----------|----------------|-------------------|---------------|
| Search direct flights | FR-SEARCH-01, 03, 04 | NFR-PERF-01, 02 | Unit + E2E |
| Search multi-hop | FR-SEARCH-02, 06, 08 | NFR-PERF-01, 02 | Unit + E2E |
| Create booking | FR-BOOK-01, 03, 05 | NFR-CONS-01, NFR-AVAIL-01 | Unit + E2E |
| Concurrent booking | FR-INV-05 | NFR-CONS-04, NFR-PERF-06 | Load test |
| Payment success | FR-PAY-01, 02 | NFR-AVAIL-01 | E2E |
| Payment failure | FR-PAY-01, 02, FR-INV-03 | NFR-CONS-01 | E2E |
| Add flight | FR-FLIGHT-01, 04, 05 | NFR-MAINT-03 | Unit |
| Cancel flight | FR-FLIGHT-02, 03 | NFR-MAINT-03 | Unit + E2E |

---

## 4. Open Questions / Future Considerations

| ID | Question | Owner | Target Date |
|----|----------|-------|-------------|
| FUT-01 | How to handle partial booking failure in multi-leg? | Engineering | Q2 2026 |
| FUT-02 | Should we support booking modifications? | Product | Q2 2026 |
| FUT-03 | International expansion requirements? | Product | Q3 2026 |
| FUT-04 | Real payment gateway integration timeline? | Payments Team | Q2 2026 |
| FUT-05 | Seat selection feature prioritization? | Product | Q3 2026 |
