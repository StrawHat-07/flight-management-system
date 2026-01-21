package com.flightmanagement.flight.constants;

public final class FlightConstants {

    private FlightConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    // ========== Search Defaults ==========

    public static final int DEFAULT_MAX_HOPS = 3;
    public static final int DEFAULT_CACHE_TTL_HOURS = 24;
    public static final int DEFAULT_MIN_CONNECTION_MINUTES = 60;
    public static final int DEFAULT_SEATS_PER_REQUEST = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_PAGE_NUMBER = 0;

    // ========== Pagination Limits ==========

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MIN_PAGE_SIZE = 1;

    // ========== Precomputation ==========

    public static final int DEFAULT_PRECOMPUTE_DAYS_AHEAD = 7;
    public static final int DEFAULT_THREAD_POOL_SIZE = 10;
    public static final String DEFAULT_PRECOMPUTE_CRON = "0 0 */6 * * *"; // Every 6 hours

    // ========== Flight Constraints ==========

    public static final int MIN_SEATS = 1;
    public static final int MIN_PRICE = 0;

    // ========== Sort Fields ==========

    public static final String SORT_BY_PRICE = "price";
    public static final String SORT_BY_DURATION = "duration";
    public static final String SORT_BY_DEPARTURE = "departureTime";
    public static final String SORT_BY_ARRIVAL = "arrivalTime";
    public static final String DEFAULT_SORT_FIELD = SORT_BY_PRICE;

    // ========== Sort Directions ==========

    public static final String SORT_ASC = "asc";
    public static final String SORT_DESC = "desc";
    public static final String DEFAULT_SORT_DIRECTION = SORT_ASC;

    // ========== Flight Types ==========

    public static final String FLIGHT_TYPE_DIRECT = "DIRECT";
    public static final String FLIGHT_TYPE_COMPUTED = "COMPUTED";

    // ========== ID Generation ==========

    public static final String FLIGHT_ID_PREFIX = "FL";
    public static final String COMPUTED_FLIGHT_ID_PREFIX = "CF_";
    public static final String ID_SEPARATOR = "_";

    // ========== Redis Keys ==========

    public static final String REDIS_FLIGHT_SEATS_PREFIX = "flight:";
    public static final String REDIS_FLIGHT_SEATS_SUFFIX = ":availableSeats";
    public static final String REDIS_ROUTE_KEY_SEPARATOR = "_";
}
