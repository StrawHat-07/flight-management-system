package com.flightmanagement.flight.constants;


public final class FlightConstants {

    private FlightConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    // ========== Search Defaults ==========

    public static final int DEFAULT_MAX_HOPS = 3;
    public static final int DEFAULT_SEATS_PER_REQUEST = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_PAGE_NUMBER = 0;

    // ========== Inventory Reservation ==========

    public static final int DEFAULT_SEAT_BLOCK_TTL_MINUTES = 5;

    // ========== Flight Constraints ==========

    public static final int MIN_SEATS = 1;
}
