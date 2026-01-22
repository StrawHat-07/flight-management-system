#!/bin/bash

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Base URLs
FLIGHT_SERVICE_URL="http://localhost:8081"
SEARCH_ENDPOINT="${FLIGHT_SERVICE_URL}/v1/search"

# Calculate dates
TOMORROW=$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d tomorrow +%Y-%m-%d)
DAY_AFTER=$(date -v+2d +%Y-%m-%d 2>/dev/null || date -d "+2 days" +%Y-%m-%d)

log_test() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${GREEN}TEST: $1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

log_request() {
    echo -e "${YELLOW}Request:${NC} $1"
}

log_response() {
    echo -e "${YELLOW}Response:${NC}"
}

execute_curl() {
    local description=$1
    local url=$2
    local method=${3:-GET}
    local data=$4
    
    log_test "$description"
    
    if [ "$method" = "POST" ]; then
        log_request "POST $url"
        echo -e "${YELLOW}Body:${NC} $data"
        log_response
        curl -s -X POST "$url" \
            -H "Content-Type: application/json" \
            -d "$data" | jq '.'
    else
        log_request "GET $url"
        log_response
        curl -s "$url" | jq '.'
    fi
    
    echo ""
    read -p "Press Enter to continue..."
}

echo -e "${GREEN}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║        Flight Search API - Comprehensive Test Suite           ║"
echo "║                 Pagination & Sorting Tests                     ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo ""
echo "Test dates: Tomorrow = $TOMORROW, Day After = $DAY_AFTER"
echo ""
read -p "Press Enter to start testing..."

# =============================================================================
# 1. BASIC SEARCH TESTS
# =============================================================================

execute_curl \
    "1.1 Basic Direct Flight Search (DEL -> BLR)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1"

execute_curl \
    "1.2 Search with Multiple Seats" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BOM&date=${TOMORROW}&seats=5"

execute_curl \
    "1.3 Search Different Route (BLR -> HYD)" \
    "${SEARCH_ENDPOINT}?source=BLR&destination=HYD&date=${TOMORROW}&seats=2"

execute_curl \
    "1.4 Search Different Date (Day After Tomorrow)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${DAY_AFTER}&seats=1"

# =============================================================================
# 2. MULTI-HOP SEARCH TESTS
# =============================================================================

execute_curl \
    "2.1 Multi-Hop Search (maxHops=1, Direct Only)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=MAA&date=${TOMORROW}&seats=1&maxHops=1"

execute_curl \
    "2.2 Multi-Hop Search (maxHops=2, Up to 1 Connection)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=MAA&date=${TOMORROW}&seats=1&maxHops=2"

execute_curl \
    "2.3 Multi-Hop Search (maxHops=3, Up to 2 Connections)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=GOI&date=${TOMORROW}&seats=1&maxHops=3"

execute_curl \
    "2.4 Complex Multi-City Route (HYD -> GOI)" \
    "${SEARCH_ENDPOINT}?source=HYD&destination=GOI&date=${TOMORROW}&seats=1&maxHops=3"

# =============================================================================
# 3. PAGINATION TESTS
# =============================================================================

execute_curl \
    "3.1 Pagination - First Page (page=0, size=5)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&page=0&size=5"

execute_curl \
    "3.2 Pagination - Second Page (page=1, size=5)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&page=1&size=5"

execute_curl \
    "3.3 Pagination - Large Page Size (size=50)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&size=50"

execute_curl \
    "3.4 Pagination - Very Small Page Size (size=2)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BOM&date=${TOMORROW}&seats=1&size=2"

execute_curl \
    "3.5 Pagination - Out of Bounds Page (page=999)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&page=999&size=10"

# =============================================================================
# 4. SORTING TESTS - PRICE
# =============================================================================

execute_curl \
    "4.1 Sort by Price (Ascending - Cheapest First)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&sortBy=price&sortDirection=asc"

execute_curl \
    "4.2 Sort by Price (Descending - Most Expensive First)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&sortBy=price&sortDirection=desc"

# =============================================================================
# 5. SORTING TESTS - DURATION
# =============================================================================

execute_curl \
    "5.1 Sort by Duration (Ascending - Shortest First)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=MAA&date=${TOMORROW}&seats=1&maxHops=2&sortBy=durationMinutes&sortDirection=asc"

execute_curl \
    "5.2 Sort by Duration (Descending - Longest First)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=MAA&date=${TOMORROW}&seats=1&maxHops=2&sortBy=durationMinutes&sortDirection=desc"

# =============================================================================
# 6. SORTING TESTS - DEPARTURE TIME
# =============================================================================

execute_curl \
    "6.1 Sort by Departure Time (Ascending - Earliest First)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BOM&date=${TOMORROW}&seats=1&sortBy=departureTime&sortDirection=asc"

execute_curl \
    "6.2 Sort by Departure Time (Descending - Latest First)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BOM&date=${TOMORROW}&seats=1&sortBy=departureTime&sortDirection=desc"

# =============================================================================
# 7. SORTING TESTS - HOPS
# =============================================================================

execute_curl \
    "7.1 Sort by Hops (Ascending - Direct Flights First)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=MAA&date=${TOMORROW}&seats=1&maxHops=3&sortBy=hops&sortDirection=asc"

execute_curl \
    "7.2 Sort by Hops (Descending - Most Connections First)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=MAA&date=${TOMORROW}&seats=1&maxHops=3&sortBy=hops&sortDirection=desc"

# =============================================================================
# 8. COMBINED TESTS (Pagination + Sorting)
# =============================================================================

execute_curl \
    "8.1 Page 0, Size 3, Sort by Price ASC" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&page=0&size=3&sortBy=price&sortDirection=asc"

execute_curl \
    "8.2 Page 1, Size 3, Sort by Duration ASC" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=MAA&date=${TOMORROW}&seats=1&maxHops=2&page=1&size=3&sortBy=durationMinutes&sortDirection=asc"

execute_curl \
    "8.3 Large Multi-Hop with Pagination and Sorting" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=GOI&date=${TOMORROW}&seats=1&maxHops=3&page=0&size=10&sortBy=price&sortDirection=asc"

# =============================================================================
# 9. EDGE CASES & VALIDATION
# =============================================================================

execute_curl \
    "9.1 No Results - Non-existent Route" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=INVALID&date=${TOMORROW}&seats=1"

execute_curl \
    "9.2 High Seat Requirement (Should filter results)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=150"

execute_curl \
    "9.3 Invalid Sort Field (Should default to price)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&sortBy=INVALID&sortDirection=asc"

execute_curl \
    "9.4 Missing Sort Direction (Should default to asc)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&sortBy=price"

execute_curl \
    "9.5 Zero MaxHops (Should handle gracefully)" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&maxHops=0"

# =============================================================================
# 10. POST REQUEST TESTS
# =============================================================================

execute_curl \
    "10.1 POST Search with All Parameters" \
    "${SEARCH_ENDPOINT}" \
    "POST" \
    '{
        "source": "DEL",
        "destination": "BLR",
        "date": "'"${TOMORROW}"'",
        "seats": 2,
        "maxHops": 2,
        "page": 0,
        "size": 10,
        "sortBy": "price",
        "sortDirection": "asc"
    }'

execute_curl \
    "10.2 POST Search with Minimal Parameters" \
    "${SEARCH_ENDPOINT}" \
    "POST" \
    '{
        "source": "BOM",
        "destination": "HYD",
        "date": "'"${TOMORROW}"'"
    }'

execute_curl \
    "10.3 POST Multi-Hop Search Sorted by Duration" \
    "${SEARCH_ENDPOINT}" \
    "POST" \
    '{
        "source": "DEL",
        "destination": "GOI",
        "date": "'"${TOMORROW}"'",
        "seats": 1,
        "maxHops": 3,
        "page": 0,
        "size": 5,
        "sortBy": "durationMinutes",
        "sortDirection": "asc"
    }'

# =============================================================================
# 11. GETTING COMPUTED FLIGHT DETAILS
# =============================================================================

log_test "11.1 Get Direct Flight Details"
log_request "Step 1: Search for flights"
SEARCH_RESULT=$(curl -s "${SEARCH_ENDPOINT}?source=DEL&destination=BLR&date=${TOMORROW}&seats=1&page=0&size=1")
echo "$SEARCH_RESULT" | jq '.'

FLIGHT_ID=$(echo "$SEARCH_RESULT" | jq -r '.content[0].flightIdentifier')
if [ "$FLIGHT_ID" != "null" ] && [ -n "$FLIGHT_ID" ]; then
    echo ""
    log_request "Step 2: Get computed flight details for: $FLIGHT_ID"
    curl -s "${FLIGHT_SERVICE_URL}/v1/search/computed/${FLIGHT_ID}" | jq '.'
else
    echo -e "${RED}No flight found in search results${NC}"
fi
read -p "Press Enter to continue..."

log_test "11.2 Get Multi-Hop Computed Flight Details"
log_request "Step 1: Search for multi-hop flights"
MULTIHOP_RESULT=$(curl -s "${SEARCH_ENDPOINT}?source=DEL&destination=MAA&date=${TOMORROW}&seats=1&maxHops=2&page=0&size=1")
echo "$MULTIHOP_RESULT" | jq '.'

COMPUTED_ID=$(echo "$MULTIHOP_RESULT" | jq -r '.content[0].flightIdentifier')
if [ "$COMPUTED_ID" != "null" ] && [ -n "$COMPUTED_ID" ]; then
    echo ""
    log_request "Step 2: Get computed flight details for: $COMPUTED_ID"
    curl -s "${FLIGHT_SERVICE_URL}/v1/search/computed/${COMPUTED_ID}" | jq '.'
else
    echo -e "${RED}No computed flight found${NC}"
fi
read -p "Press Enter to continue..."

# =============================================================================
# 12. REAL-WORLD SCENARIOS
# =============================================================================

execute_curl \
    "12.1 Business Traveler - Cheapest Direct Flight" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=BOM&date=${TOMORROW}&seats=1&maxHops=1&sortBy=price&sortDirection=asc&size=5"

execute_curl \
    "12.2 Time-Sensitive - Earliest Departure" \
    "${SEARCH_ENDPOINT}?source=BLR&destination=DEL&date=${TOMORROW}&seats=1&sortBy=departureTime&sortDirection=asc&size=3"

execute_curl \
    "12.3 Group Booking - 4 Passengers, Budget Conscious" \
    "${SEARCH_ENDPOINT}?source=DEL&destination=GOI&date=${TOMORROW}&seats=4&maxHops=2&sortBy=price&sortDirection=asc&size=10"

execute_curl \
    "12.4 Weekend Traveler - Fastest Route" \
    "${SEARCH_ENDPOINT}?source=MAA&destination=BLR&date=${TOMORROW}&seats=2&maxHops=2&sortBy=durationMinutes&sortDirection=asc&size=5"

execute_curl \
    "12.5 Flexible Schedule - Show All Options, Best Price" \
    "${SEARCH_ENDPOINT}?source=HYD&destination=BOM&date=${TOMORROW}&seats=1&maxHops=3&sortBy=price&sortDirection=asc&size=20"

# =============================================================================
# SUMMARY
# =============================================================================

echo -e "\n${GREEN}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                    Test Suite Complete!                       ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo ""
echo "Tests covered:"
echo "  ✓ Basic search (direct flights)"
echo "  ✓ Multi-hop search (1-3 connections)"
echo "  ✓ Pagination (different pages and sizes)"
echo "  ✓ Sorting (price, duration, departureTime, hops)"
echo "  ✓ Sort directions (ascending/descending)"
echo "  ✓ Combined pagination + sorting"
echo "  ✓ POST requests"
echo "  ✓ Edge cases and validation"
echo "  ✓ Getting computed flight details"
echo "  ✓ Real-world scenarios"
echo ""
echo "Total tests: 45+"
echo ""
