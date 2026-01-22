# Flight Management System - API Documentation

**Version:** 1.0  
**Base URLs:**
- Flight Service: `http://localhost:8081`
- Booking Service: `http://localhost:8083`
- Payment Service: `http://localhost:8084`

**Content-Type:** `application/json`

---

## 1. Flight Service APIs

### 1.1 Flight Management

#### List All Flights

```http
GET /v1/flights/all
```

**Description:** Returns all active flights in the system.

**Response:**
```json
[
  {
    "id": 1,
    "flightId": "FL201",
    "source": "DEL",
    "destination": "BLR",
    "departureTime": "2026-01-23T06:00:00",
    "arrivalTime": "2026-01-23T08:45:00",
    "totalSeats": 180,
    "availableSeats": 175,
    "price": 5400.00,
    "status": "ACTIVE",
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-22T10:30:00"
  }
]
```

| Status | Description |
|--------|-------------|
| 200 | Success |
| 500 | Internal server error |

---

#### Get Flight by ID

```http
GET /v1/flights/{flightId}
```

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| flightId | string | Yes | Flight identifier (e.g., FL201) |

**Response:**
```json
{
  "id": 1,
  "flightId": "FL201",
  "source": "DEL",
  "destination": "BLR",
  "departureTime": "2026-01-23T06:00:00",
  "arrivalTime": "2026-01-23T08:45:00",
  "totalSeats": 180,
  "availableSeats": 175,
  "price": 5400.00,
  "status": "ACTIVE",
  "createdAt": "2026-01-01T00:00:00",
  "updatedAt": "2026-01-22T10:30:00"
}
```

| Status | Description |
|--------|-------------|
| 200 | Success |
| 404 | Flight not found |

---

#### Create Flight

```http
POST /v1/flights
```

**Request Body:**
```json
{
  "source": "DEL",
  "destination": "BLR",
  "departureTime": "2026-01-25T10:00:00",
  "arrivalTime": "2026-01-25T12:45:00",
  "totalSeats": 180,
  "price": 5500.00
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| source | string | Yes | 2-10 chars, airport code |
| destination | string | Yes | 2-10 chars, airport code |
| departureTime | datetime | Yes | ISO 8601, future date |
| arrivalTime | datetime | Yes | After departureTime |
| totalSeats | integer | Yes | 1-500 |
| price | decimal | Yes | > 0 |

**Response (201 Created):**
```json
{
  "id": 100,
  "flightId": "FL7A3B2C1D",
  "source": "DEL",
  "destination": "BLR",
  "departureTime": "2026-01-25T10:00:00",
  "arrivalTime": "2026-01-25T12:45:00",
  "totalSeats": 180,
  "availableSeats": 180,
  "price": 5500.00,
  "status": "ACTIVE",
  "createdAt": "2026-01-22T12:00:00",
  "updatedAt": "2026-01-22T12:00:00"
}
```

| Status | Description |
|--------|-------------|
| 201 | Created successfully |
| 400 | Validation error |

**Error Response (400):**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid request",
  "details": {
    "arrivalTime": "Arrival time must be after departure time"
  },
  "retryable": false,
  "timestamp": "2026-01-22T12:00:00Z"
}
```

---

#### Cancel Flight

```http
DELETE /v1/flights/{flightId}
```

**Description:** Soft-deletes a flight by setting status to CANCELLED.

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| flightId | string | Yes | Flight identifier |

**Response:** 204 No Content

| Status | Description |
|--------|-------------|
| 204 | Successfully cancelled |
| 404 | Flight not found |

---

### 1.2 Search APIs

#### Search Flights

```http
GET /v1/search
```

**Query Parameters:**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| source | string | Yes | - | Origin airport code |
| destination | string | Yes | - | Destination airport code |
| date | date | Yes | - | Travel date (YYYY-MM-DD) |
| seats | integer | Yes | - | Number of passengers |
| maxHops | integer | No | 3 | Maximum connections (1-3) |
| sortBy | string | No | price | Sort field: price, duration, departureTime |
| sortDirection | string | No | asc | Sort direction: asc, desc |
| page | integer | No | 0 | Page number (0-indexed) |
| size | integer | No | 20 | Results per page (max 100) |

**Example Request:**
```http
GET /v1/search?source=DEL&destination=BLR&date=2026-01-23&seats=2&maxHops=3&sortBy=price&sortDirection=asc
```

**Response:**
```json
{
  "content": [
    {
      "flightIdentifier": "FL201",
      "type": "DIRECT",
      "price": 5400.00,
      "durationMinutes": 165,
      "availableSeats": 175,
      "departureTime": "2026-01-23T06:00:00",
      "arrivalTime": "2026-01-23T08:45:00",
      "source": "DEL",
      "destination": "BLR",
      "hops": 1,
      "legs": [
        {
          "flightId": "FL201",
          "source": "DEL",
          "destination": "BLR",
          "departureTime": "2026-01-23T06:00:00",
          "arrivalTime": "2026-01-23T08:45:00",
          "price": 5400.00,
          "availableSeats": 175
        }
      ]
    },
    {
      "flightIdentifier": "CF_DEL_BLR_FL202_FL210",
      "type": "COMPUTED",
      "price": 8700.00,
      "durationMinutes": 315,
      "availableSeats": 158,
      "departureTime": "2026-01-23T07:30:00",
      "arrivalTime": "2026-01-23T12:45:00",
      "source": "DEL",
      "destination": "BLR",
      "hops": 2,
      "legs": [
        {
          "flightId": "FL202",
          "source": "DEL",
          "destination": "BOM",
          "departureTime": "2026-01-23T07:30:00",
          "arrivalTime": "2026-01-23T09:45:00",
          "price": 4700.00,
          "availableSeats": 198
        },
        {
          "flightId": "FL210",
          "source": "BOM",
          "destination": "BLR",
          "departureTime": "2026-01-23T11:00:00",
          "arrivalTime": "2026-01-23T12:45:00",
          "price": 4000.00,
          "availableSeats": 158
        }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true,
  "hasNext": false,
  "hasPrevious": false
}
```

| Status | Description |
|--------|-------------|
| 200 | Success (may return empty content) |
| 400 | Invalid parameters |

---

#### Get Computed Flight Details

```http
GET /v1/search/computed/{computedFlightId}
```

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| computedFlightId | string | Yes | Computed flight ID (e.g., CF_DEL_BLR_FL202_FL210) |

**Response:**
```json
{
  "computedFlightId": "CF_DEL_BLR_FL202_FL210",
  "flightIds": ["FL202", "FL210"],
  "source": "DEL",
  "destination": "BLR",
  "totalPrice": 8700.00,
  "totalDurationMinutes": 315,
  "availableSeats": 158,
  "numberOfHops": 2,
  "departureTime": "2026-01-23T07:30:00",
  "arrivalTime": "2026-01-23T12:45:00",
  "legs": [...]
}
```

| Status | Description |
|--------|-------------|
| 200 | Success |
| 404 | Computed flight not found |

---

### 1.3 Inventory APIs

#### Get Available Seats

```http
GET /v1/flights/{flightId}/available-seats
```

**Response:**
```json
{
  "flightId": "FL201",
  "availableSeats": 175
}
```

---

#### Reserve Seats

```http
POST /v1/inventory/reserve
```

**Request Body:**
```json
{
  "bookingId": "BK84137EF0",
  "flightIds": ["FL201"],
  "seats": 2,
  "ttlMinutes": 5
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| bookingId | string | Yes | Unique booking identifier |
| flightIds | array | Yes | List of flight IDs to reserve |
| seats | integer | Yes | 1-10 |
| ttlMinutes | integer | Yes | 1-30, reservation hold time |

**Response (Success):**
```json
{
  "success": true,
  "reservationId": "BK84137EF0",
  "message": null,
  "errorCode": null,
  "expiresAt": "2026-01-22T18:30:00"
}
```

**Response (Failure):**
```json
{
  "success": false,
  "reservationId": null,
  "message": "Not enough seats available on flight FL201",
  "errorCode": "NO_SEATS_AVAILABLE",
  "expiresAt": null
}
```

| Status | Error Code | Description |
|--------|------------|-------------|
| 200 | - | Reservation successful |
| 200 | NO_SEATS_AVAILABLE | Insufficient seats |
| 200 | LOCK_ACQUISITION_FAILED | Concurrent conflict |
| 400 | VALIDATION_ERROR | Invalid request |

---

#### Confirm Reservation

```http
POST /v1/inventory/confirm
```

**Request Body:**
```json
{
  "bookingId": "BK84137EF0",
  "flightIds": ["FL201"],
  "seats": 2
}
```

**Response:**
```json
{
  "status": "confirmed",
  "message": "Reservation confirmed"
}
```

| Status | Description |
|--------|-------------|
| 200 | Confirmed |
| 200 | status: "expired" if reservation not found |

---

#### Release Reservation

```http
DELETE /v1/inventory/release/{bookingId}?flightIds=FL201,FL202
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| flightIds | string | Yes | Comma-separated flight IDs |

**Response:**
```json
{
  "status": "released",
  "message": "Reservation released successfully"
}
```

---

## 2. Booking Service APIs

### 2.1 Booking Management

#### Create Booking

```http
POST /v1/bookings
```

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| X-Idempotency-Key | No | Prevents duplicate bookings |
| Content-Type | Yes | application/json |

**Request Body:**
```json
{
  "userId": "user-001",
  "flightIdentifier": "FL201",
  "seats": 2
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| userId | string | Yes | Non-empty |
| flightIdentifier | string | Yes | Flight ID or Computed Flight ID |
| seats | integer | Yes | 1-10 |

**Response (201 Created):**
```json
{
  "bookingId": "BK84137EF0",
  "userId": "user-001",
  "flightType": "DIRECT",
  "flightIdentifier": "FL201",
  "noOfSeats": 2,
  "totalPrice": 10800.00,
  "status": "PENDING",
  "flightIds": ["FL201"],
  "createdAt": "2026-01-22T18:25:00"
}
```

**Booking Status Values:**
| Status | Description |
|--------|-------------|
| PENDING | Awaiting payment |
| CONFIRMED | Payment successful |
| FAILED | Payment failed |
| CANCELLED | User cancelled |

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 201 | - | Booking created |
| 200 | - | Idempotent return (existing booking) |
| 400 | INVALID_FLIGHT | Flight not found |
| 400 | NO_SEATS_AVAILABLE | Insufficient seats |
| 400 | VALIDATION_ERROR | Invalid input |
| 500 | INTERNAL_ERROR | Unexpected error |

---

#### Get Booking

```http
GET /v1/bookings/{bookingId}
```

**Response:**
```json
{
  "bookingId": "BK84137EF0",
  "userId": "user-001",
  "flightType": "DIRECT",
  "flightIdentifier": "FL201",
  "noOfSeats": 2,
  "totalPrice": 10800.00,
  "status": "CONFIRMED",
  "flightIds": ["FL201"],
  "createdAt": "2026-01-22T18:25:00",
  "updatedAt": "2026-01-22T18:25:30"
}
```

| Status | Description |
|--------|-------------|
| 200 | Success |
| 404 | Booking not found |

---

#### Get User's Bookings

```http
GET /v1/bookings/user/{userId}
```

**Response:**
```json
[
  {
    "bookingId": "BK84137EF0",
    "userId": "user-001",
    "flightType": "DIRECT",
    "flightIdentifier": "FL201",
    "noOfSeats": 2,
    "totalPrice": 10800.00,
    "status": "CONFIRMED",
    "flightIds": ["FL201"],
    "createdAt": "2026-01-22T18:25:00"
  }
]
```

---

#### Payment Callback (Internal)

```http
POST /v1/bookings/payment-callback
```

**Request Body:**
```json
{
  "bookingId": "BK84137EF0",
  "paymentId": "PAY15514D42",
  "status": "SUCCESS",
  "message": "Payment processed successfully"
}
```

| Status Value | Booking Update |
|--------------|----------------|
| SUCCESS | PENDING → CONFIRMED |
| FAILURE | PENDING → FAILED |
| TIMEOUT | PENDING → FAILED |

---

## 3. Payment Service APIs

### 3.1 Payment Processing

#### Process Payment (Async)

```http
POST /v1/payments/process
```

**Request Body:**
```json
{
  "bookingId": "BK84137EF0",
  "userId": "user-001",
  "amount": 10800.00,
  "callbackUrl": "http://booking-service:8083/v1/bookings/payment-callback"
}
```

**Response (202 Accepted):**
```json
{
  "status": "PROCESSING",
  "message": "Payment is being processed. Result will be sent to callback URL.",
  "bookingId": "BK84137EF0"
}
```

---

#### Process Payment (Sync)

```http
POST /v1/payments/process-sync
```

**Description:** Processes payment synchronously and returns result immediately. For testing.

**Request Body:** Same as async

**Response:**
```json
{
  "paymentId": "PAY15514D42",
  "bookingId": "BK84137EF0",
  "userId": "user-001",
  "amount": 10800.00,
  "status": "SUCCESS",
  "message": "Payment processed successfully",
  "processedAt": "2026-01-22T18:25:30"
}
```

---

#### Process with Forced Outcome

```http
POST /v1/payments/process-with-outcome?outcome=SUCCESS
```

**Query Parameters:**
| Parameter | Type | Values | Description |
|-----------|------|--------|-------------|
| outcome | string | SUCCESS, FAILURE, TIMEOUT | Force specific result |

---

#### Get Payment

```http
GET /v1/payments/{paymentId}
```

```http
GET /v1/payments/booking/{bookingId}
```

**Response:**
```json
{
  "paymentId": "PAY15514D42",
  "bookingId": "BK84137EF0",
  "userId": "user-001",
  "amount": 10800.00,
  "status": "SUCCESS",
  "message": "Payment processed successfully",
  "processedAt": "2026-01-22T18:25:30"
}
```

---

### 3.2 Mock Configuration (Testing)

#### Get Configuration

```http
GET /v1/mock/config
```

**Response:**
```json
{
  "forcedOutcome": null,
  "successProbability": 70,
  "failureProbability": 20,
  "minDelayMs": 1000,
  "maxDelayMs": 5000,
  "skipDelay": false
}
```

---

#### Update Configuration

```http
PUT /v1/mock/config
```

**Request Body:**
```json
{
  "forcedOutcome": "SUCCESS",
  "skipDelay": true
}
```

---

#### Quick Configuration

```http
POST /v1/mock/force-success
POST /v1/mock/force-failure
POST /v1/mock/reset
```

---

## 4. Health & Monitoring

All services expose:

```http
GET /actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

```http
GET /actuator/info
GET /actuator/metrics
```

---

## 5. Error Response Format

All errors follow this structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "details": {
    "field": "Specific validation error"
  },
  "retryable": true,
  "timestamp": "2026-01-22T12:00:00Z"
}
```

### Common Error Codes

| Code | HTTP | Description | Retryable |
|------|------|-------------|-----------|
| VALIDATION_ERROR | 400 | Invalid input parameters | No |
| INVALID_FLIGHT | 400 | Flight not found or inactive | No |
| NO_SEATS_AVAILABLE | 400 | Insufficient seat inventory | No |
| BOOKING_NOT_FOUND | 404 | Booking ID doesn't exist | No |
| LOCK_ACQUISITION_FAILED | 409 | Concurrent modification conflict | Yes |
| INTERNAL_ERROR | 500 | Unexpected server error | Yes |
| SERVICE_UNAVAILABLE | 503 | Downstream service down | Yes |

---

## 6. Rate Limits (Production Recommendation)

| Endpoint | Limit | Window |
|----------|-------|--------|
| GET /v1/search | 100 req/s | Per IP |
| POST /v1/bookings | 10 req/s | Per user |
| POST /v1/inventory/* | 50 req/s | Per IP |

---

## 7. Authentication (Production Recommendation)

**Header:** `Authorization: Bearer <jwt_token>`

All endpoints except health checks should require authentication in production.
