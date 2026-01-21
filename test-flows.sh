#!/bin/bash

# =================================================================
# Flight Management System - End-to-End Test Script
# =================================================================
# This script tests all flows of the flight management system.
# Prerequisites: All services must be running and healthy.
# =================================================================

set -e

# Configuration
FLIGHT_SERVICE_URL="${FLIGHT_SERVICE_URL:-http://localhost:8081}"
BOOKING_SERVICE_URL="${BOOKING_SERVICE_URL:-http://localhost:8083}"
PAYMENT_SERVICE_URL="${PAYMENT_SERVICE_URL:-http://localhost:8084}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
TESTS_PASSED=0
TESTS_FAILED=0

# =================================================================
# Utility Functions
# =================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((TESTS_PASSED++))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((TESTS_FAILED++))
}

log_section() {
    echo ""
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW}$1${NC}"
    echo -e "${YELLOW}========================================${NC}"
}

# Make HTTP request and check response
http_get() {
    local url="$1"
    local expected_status="${2:-200}"
    
    local response=$(curl -s -w "\n%{http_code}" "$url" 2>/dev/null)
    local body=$(echo "$response" | head -n -1)
    local status=$(echo "$response" | tail -n 1)
    
    if [ "$status" == "$expected_status" ]; then
        echo "$body"
        return 0
    else
        echo "Expected status $expected_status but got $status"
        return 1
    fi
}

http_post() {
    local url="$1"
    local data="$2"
    local expected_status="${3:-200}"
    local headers="${4:-}"
    
    local curl_cmd="curl -s -w \"\n%{http_code}\" -X POST -H \"Content-Type: application/json\""
    
    if [ -n "$headers" ]; then
        curl_cmd="$curl_cmd -H \"$headers\""
    fi
    
    curl_cmd="$curl_cmd -d '$data' \"$url\""
    
    local response=$(eval $curl_cmd 2>/dev/null)
    local body=$(echo "$response" | head -n -1)
    local status=$(echo "$response" | tail -n 1)
    
    if [ "$status" == "$expected_status" ]; then
        echo "$body"
        return 0
    else
        echo "Expected status $expected_status but got $status. Response: $body"
        return 1
    fi
}

# =================================================================
# Health Check Tests
# =================================================================

test_health_checks() {
    log_section "1. HEALTH CHECK TESTS"
    
    log_info "Checking Flight Service health..."
    if health=$(http_get "$FLIGHT_SERVICE_URL/actuator/health"); then
        if echo "$health" | grep -q '"status":"UP"'; then
            log_success "Flight Service is healthy"
        else
            log_error "Flight Service health check failed"
        fi
    else
        log_error "Flight Service is not reachable"
    fi
    
    log_info "Checking Booking Service health..."
    if health=$(http_get "$BOOKING_SERVICE_URL/actuator/health"); then
        if echo "$health" | grep -q '"status":"UP"'; then
            log_success "Booking Service is healthy"
        else
            log_error "Booking Service health check failed"
        fi
    else
        log_error "Booking Service is not reachable"
    fi
    
    log_info "Checking Payment Service health..."
    if health=$(http_get "$PAYMENT_SERVICE_URL/actuator/health"); then
        if echo "$health" | grep -q '"status":"UP"'; then
            log_success "Payment Service is healthy"
        else
            log_error "Payment Service health check failed"
        fi
    else
        log_error "Payment Service is not reachable"
    fi
}

# =================================================================
# Flight Service Tests
# =================================================================

test_flight_crud() {
    log_section "2. FLIGHT CRUD TESTS"
    
    # List all flights
    log_info "Listing all flights..."
    if flights=$(http_get "$FLIGHT_SERVICE_URL/v1/flights"); then
        log_success "List flights: $(echo $flights | jq -r 'length') flights found"
    else
        log_error "Failed to list flights: $flights"
    fi
    
    # Create a new flight
    log_info "Creating a new flight..."
    local tomorrow=$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d '+1 day' +%Y-%m-%d)
    local create_data='{
        "src": "SFO",
        "dest": "SEA",
        "departureTime": "'${tomorrow}'T10:00:00",
        "arrivalTime": "'${tomorrow}'T12:30:00",
        "totalSeats": 150,
        "price": 199.99
    }'
    
    if result=$(http_post "$FLIGHT_SERVICE_URL/v1/flights" "$create_data" "201"); then
        CREATED_FLIGHT_ID=$(echo $result | jq -r '.flightId')
        log_success "Created flight: $CREATED_FLIGHT_ID"
    else
        log_error "Failed to create flight: $result"
        CREATED_FLIGHT_ID=""
    fi
    
    # Get flight by ID
    if [ -n "$CREATED_FLIGHT_ID" ]; then
        log_info "Getting flight by ID..."
        if flight=$(http_get "$FLIGHT_SERVICE_URL/v1/flights/$CREATED_FLIGHT_ID"); then
            if echo "$flight" | jq -e '.flightId' > /dev/null 2>&1; then
                log_success "Get flight by ID works"
            else
                log_error "Flight not found"
            fi
        else
            log_error "Failed to get flight: $flight"
        fi
    fi
    
    # Test validation - same src and dest
    log_info "Testing validation (same src/dest)..."
    local invalid_data='{
        "src": "DEL",
        "dest": "DEL",
        "departureTime": "'${tomorrow}'T10:00:00",
        "arrivalTime": "'${tomorrow}'T12:00:00",
        "totalSeats": 100,
        "price": 5000.00
    }'
    
    if result=$(http_post "$FLIGHT_SERVICE_URL/v1/flights" "$invalid_data" "400"); then
        log_success "Validation correctly rejects same src/dest"
    else
        log_error "Validation should reject same src/dest"
    fi
    
    # Delete the created flight
    if [ -n "$CREATED_FLIGHT_ID" ]; then
        log_info "Deleting flight..."
        if curl -s -X DELETE "$FLIGHT_SERVICE_URL/v1/flights/$CREATED_FLIGHT_ID" -o /dev/null -w "%{http_code}" | grep -q "204"; then
            log_success "Flight deleted successfully"
        else
            log_warning "Flight deletion returned unexpected status"
        fi
    fi
}

# =================================================================
# Search Service Tests
# =================================================================

test_search() {
    log_section "3. SEARCH SERVICE TESTS"
    
    local tomorrow=$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d '+1 day' +%Y-%m-%d)
    
    # Search for direct flights (Delhi to Bangalore)
    log_info "Searching for flights DEL -> BLR..."
    if result=$(http_get "$FLIGHT_SERVICE_URL/v1/search?src=DEL&dest=BLR&date=$tomorrow&seats=1&maxHops=1"); then
        local count=$(echo "$result" | jq -r 'length' 2>/dev/null || echo "0")
        log_success "Direct flight search returned $count results"
    else
        log_warning "No direct flights found (may be expected): $result"
    fi
    
    # Search with multi-hop (Delhi to Chennai via Hyderabad)
    log_info "Searching for flights with connections (up to 2 hops) DEL -> MAA..."
    if result=$(http_get "$FLIGHT_SERVICE_URL/v1/search?src=DEL&dest=MAA&date=$tomorrow&seats=1&maxHops=2"); then
        local count=$(echo "$result" | jq -r 'length' 2>/dev/null || echo "0")
        log_success "Multi-hop search returned $count results"
    else
        log_warning "No connecting flights found: $result"
    fi
    
    # Test validation - past date
    log_info "Testing search validation (past date)..."
    local past=$(date -v-1d +%Y-%m-%d 2>/dev/null || date -d '-1 day' +%Y-%m-%d)
    if result=$(http_get "$FLIGHT_SERVICE_URL/v1/search?src=DEL&dest=BLR&date=$past" "400"); then
        log_success "Search correctly rejects past dates"
    else
        log_error "Search should reject past dates"
    fi
}

# =================================================================
# Booking Service Tests
# =================================================================

test_booking_flow() {
    log_section "4. BOOKING FLOW TESTS"
    
    # First, get available flights
    log_info "Getting available flights for booking..."
    if flights=$(http_get "$FLIGHT_SERVICE_URL/v1/flights"); then
        FLIGHT_ID=$(echo "$flights" | jq -r '.[0].flightId' 2>/dev/null)
        if [ "$FLIGHT_ID" != "null" ] && [ -n "$FLIGHT_ID" ]; then
            log_success "Found flight for booking: $FLIGHT_ID"
        else
            log_warning "No flights available for booking test"
            return
        fi
    else
        log_error "Cannot get flights for booking test"
        return
    fi
    
    # Create a booking
    log_info "Creating a booking..."
    local booking_data='{
        "userId": "test-user-001",
        "flightIdentifier": "'$FLIGHT_ID'",
        "seats": 2
    }'
    
    local idempotency_key="test-$(date +%s)"
    
    if result=$(http_post "$BOOKING_SERVICE_URL/v1/bookings" "$booking_data" "201" "Idempotency-Key: $idempotency_key"); then
        BOOKING_ID=$(echo "$result" | jq -r '.bookingId')
        BOOKING_STATUS=$(echo "$result" | jq -r '.status')
        log_success "Created booking: $BOOKING_ID (status: $BOOKING_STATUS)"
    else
        log_error "Failed to create booking: $result"
        return
    fi
    
    # Test idempotency - same key should return same booking
    log_info "Testing idempotency..."
    if result=$(http_post "$BOOKING_SERVICE_URL/v1/bookings" "$booking_data" "201" "Idempotency-Key: $idempotency_key"); then
        local returned_id=$(echo "$result" | jq -r '.bookingId')
        if [ "$returned_id" == "$BOOKING_ID" ]; then
            log_success "Idempotency works - same booking returned"
        else
            log_error "Idempotency failed - different booking returned"
        fi
    else
        log_error "Idempotency request failed: $result"
    fi
    
    # Get booking
    log_info "Getting booking by ID..."
    if result=$(http_get "$BOOKING_SERVICE_URL/v1/bookings/$BOOKING_ID"); then
        log_success "Booking retrieved successfully"
    else
        log_error "Failed to get booking: $result"
    fi
    
    # Wait for payment callback
    log_info "Waiting for payment processing (5 seconds)..."
    sleep 5
    
    # Check booking status after payment
    log_info "Checking final booking status..."
    if result=$(http_get "$BOOKING_SERVICE_URL/v1/bookings/$BOOKING_ID"); then
        local final_status=$(echo "$result" | jq -r '.status')
        log_success "Final booking status: $final_status"
    else
        log_error "Failed to check booking status"
    fi
    
    # Get bookings by user
    log_info "Getting bookings by user..."
    if result=$(http_get "$BOOKING_SERVICE_URL/v1/bookings/user/test-user-001"); then
        local count=$(echo "$result" | jq -r 'length' 2>/dev/null || echo "0")
        log_success "Found $count booking(s) for user"
    else
        log_error "Failed to get user bookings: $result"
    fi
}

# =================================================================
# Booking Validation Tests
# =================================================================

test_booking_validation() {
    log_section "5. BOOKING VALIDATION TESTS"
    
    # Test missing user ID
    log_info "Testing validation (missing user ID)..."
    local invalid_data='{"flightIdentifier": "FL001", "seats": 2}'
    if result=$(http_post "$BOOKING_SERVICE_URL/v1/bookings" "$invalid_data" "400"); then
        log_success "Validation correctly rejects missing user ID"
    else
        log_error "Validation should reject missing user ID"
    fi
    
    # Test invalid flight
    log_info "Testing validation (invalid flight)..."
    local invalid_flight_data='{"userId": "user1", "flightIdentifier": "INVALID_FLIGHT_ID", "seats": 2}'
    if result=$(http_post "$BOOKING_SERVICE_URL/v1/bookings" "$invalid_flight_data" "400"); then
        log_success "Validation correctly rejects invalid flight"
    else
        log_error "Validation should reject invalid flight"
    fi
    
    # Test too many seats
    log_info "Testing validation (too many seats)..."
    local too_many_seats='{"userId": "user1", "flightIdentifier": "FL001", "seats": 100}'
    if result=$(http_post "$BOOKING_SERVICE_URL/v1/bookings" "$too_many_seats" "400"); then
        log_success "Validation correctly rejects too many seats"
    else
        log_error "Validation should reject too many seats"
    fi
}

# =================================================================
# Payment Service Tests
# =================================================================

test_payment_service() {
    log_section "6. PAYMENT SERVICE TESTS"
    
    # Test sync payment
    log_info "Testing synchronous payment (for testing)..."
    local payment_data='{
        "bookingId": "TEST-BK-001",
        "userId": "test-user",
        "amount": 299.99,
        "callbackUrl": ""
    }'
    
    if result=$(http_post "$PAYMENT_SERVICE_URL/v1/payments/process-sync" "$payment_data"); then
        local status=$(echo "$result" | jq -r '.status')
        log_success "Sync payment completed with status: $status"
    else
        log_error "Sync payment failed: $result"
    fi
    
    # Test forced outcome
    log_info "Testing forced payment outcome (SUCCESS)..."
    local forced_data='{
        "bookingId": "TEST-BK-002",
        "userId": "test-user",
        "amount": 199.99,
        "callbackUrl": ""
    }'
    
    if result=$(http_post "$PAYMENT_SERVICE_URL/v1/payments/process-with-outcome?outcome=SUCCESS" "$forced_data"); then
        local status=$(echo "$result" | jq -r '.status')
        if [ "$status" == "SUCCESS" ]; then
            log_success "Forced SUCCESS outcome works"
        else
            log_error "Forced outcome returned wrong status: $status"
        fi
    else
        log_error "Forced payment failed: $result"
    fi
    
    # Test forced FAILURE
    log_info "Testing forced payment outcome (FAILURE)..."
    forced_data='{
        "bookingId": "TEST-BK-003",
        "userId": "test-user",
        "amount": 99.99,
        "callbackUrl": ""
    }'
    
    if result=$(http_post "$PAYMENT_SERVICE_URL/v1/payments/process-with-outcome?outcome=FAILURE" "$forced_data"); then
        local status=$(echo "$result" | jq -r '.status')
        if [ "$status" == "FAILURE" ]; then
            log_success "Forced FAILURE outcome works"
        else
            log_error "Forced outcome returned wrong status: $status"
        fi
    else
        log_error "Forced payment failed: $result"
    fi
}

# =================================================================
# Seat Blocking Tests
# =================================================================

test_seat_blocking() {
    log_section "7. SEAT BLOCKING TESTS"
    
    # Get a flight with available seats
    log_info "Getting flight for seat blocking test..."
    if flights=$(http_get "$FLIGHT_SERVICE_URL/v1/flights"); then
        FLIGHT_ID=$(echo "$flights" | jq -r '.[0].flightId' 2>/dev/null)
        AVAILABLE_SEATS=$(echo "$flights" | jq -r '.[0].availableSeats' 2>/dev/null)
        
        if [ "$FLIGHT_ID" != "null" ] && [ -n "$FLIGHT_ID" ]; then
            log_success "Testing with flight $FLIGHT_ID ($AVAILABLE_SEATS seats available)"
        else
            log_warning "No flights available for seat blocking test"
            return
        fi
    else
        log_error "Cannot get flights"
        return
    fi
    
    # Get initial seat count
    log_info "Getting initial seat count..."
    if result=$(http_get "$FLIGHT_SERVICE_URL/v1/flights/$FLIGHT_ID/available-seats"); then
        INITIAL_SEATS=$(echo "$result" | jq -r '.availableSeats')
        log_success "Initial available seats: $INITIAL_SEATS"
    else
        log_error "Cannot get seat count"
        return
    fi
    
    # Create a booking to block seats
    log_info "Creating booking to block seats..."
    local booking_data='{
        "userId": "seat-test-user",
        "flightIdentifier": "'$FLIGHT_ID'",
        "seats": 3
    }'
    
    if result=$(http_post "$BOOKING_SERVICE_URL/v1/bookings" "$booking_data" "201"); then
        log_success "Booking created, seats should be blocked"
    else
        log_error "Failed to create booking: $result"
        return
    fi
    
    # Check seat count immediately (should be reduced)
    log_info "Checking seat count after booking..."
    if result=$(http_get "$FLIGHT_SERVICE_URL/v1/flights/$FLIGHT_ID/available-seats"); then
        local current_seats=$(echo "$result" | jq -r '.availableSeats')
        local expected=$((INITIAL_SEATS - 3))
        
        if [ "$current_seats" -le "$expected" ]; then
            log_success "Seats correctly blocked (now: $current_seats, was: $INITIAL_SEATS)"
        else
            log_warning "Seat count: $current_seats (expected ~$expected)"
        fi
    else
        log_error "Cannot check seat count"
    fi
}

# =================================================================
# Summary
# =================================================================

print_summary() {
    log_section "TEST SUMMARY"
    
    local total=$((TESTS_PASSED + TESTS_FAILED))
    
    echo ""
    echo -e "Total Tests:  $total"
    echo -e "${GREEN}Passed:       $TESTS_PASSED${NC}"
    echo -e "${RED}Failed:       $TESTS_FAILED${NC}"
    echo ""
    
    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        exit 0
    else
        echo -e "${RED}Some tests failed. Please check the output above.${NC}"
        exit 1
    fi
}

# =================================================================
# Main Execution
# =================================================================

main() {
    echo ""
    echo "==========================================="
    echo "Flight Management System - E2E Test Suite"
    echo "==========================================="
    echo ""
    echo "Flight Service: $FLIGHT_SERVICE_URL"
    echo "Booking Service: $BOOKING_SERVICE_URL"
    echo "Payment Service: $PAYMENT_SERVICE_URL"
    echo ""
    
    test_health_checks
    test_flight_crud
    test_search
    test_booking_flow
    test_booking_validation
    test_payment_service
    test_seat_blocking
    
    print_summary
}

# Run the tests
main "$@"
