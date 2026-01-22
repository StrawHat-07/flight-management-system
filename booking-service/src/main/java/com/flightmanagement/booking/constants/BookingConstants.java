package com.flightmanagement.booking.constants;

public final class BookingConstants {

    private BookingConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    public static final int DEFAULT_SEAT_BLOCK_TTL_MINUTES = 5;
    public static final int MIN_SEATS_PER_BOOKING = 1;
    public static final int MAX_SEATS_PER_BOOKING = 9;
    
    public static final String BOOKING_ID_PREFIX = "BK";
    
    public static final int BOOKING_EXPIRY_CHECK_INTERVAL_MS = 30000;
    
    public static final String REDIS_AVAILABLE_SEATS_PREFIX = "flight:";
    public static final String REDIS_AVAILABLE_SEATS_SUFFIX = ":availableSeats";
    public static final String REDIS_BLOCKED_SEATS_PREFIX = "flight:";
    public static final String REDIS_BLOCKED_SEATS_INFIX = ":blocked:";
}
