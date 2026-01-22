package com.flightmanagement.flight.constants;

public final class ValidationMessages {

    private ValidationMessages() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    // ========== Flight Validation Messages ==========

    public static final String FLIGHT_DATA_REQUIRED = "Flight data is required";
    public static final String FLIGHT_ID_REQUIRED = "Flight ID is required";
    public static final String SOURCE_REQUIRED = "Source is required";
    public static final String DESTINATION_REQUIRED = "Destination is required";
    public static final String SOURCE_DESTINATION_SAME = "Source and destination cannot be the same";

    public static final String DEPARTURE_TIME_REQUIRED = "Departure time is required";
    public static final String ARRIVAL_TIME_REQUIRED = "Arrival time is required";
    public static final String ARRIVAL_AFTER_DEPARTURE = "Arrival must be after departure";

    public static final String TOTAL_SEATS_REQUIRED = "Total seats is required";
    public static final String TOTAL_SEATS_MIN = "Total seats must be at least 1";
    public static final String SEAT_COUNT_POSITIVE = "Seat count must be positive";

    public static final String PRICE_REQUIRED = "Price is required";
    public static final String PRICE_NON_NEGATIVE = "Price must be non-negative";

    // ========== Search Validation Messages ==========

    public static final String SEARCH_REQUEST_REQUIRED = "Search request is required";
    public static final String DATE_REQUIRED = "Date is required";
    public static final String DATE_NOT_PAST = "Date cannot be in the past";
    public static final String SEATS_MIN = "Seats must be at least 1";
    public static final String SEATS_REQUIRED = "Number of seats is required";
    public static final String BOOKING_ID_REQUIRED = "Booking ID is required";
    public static final String FLIGHT_IDENTIFIER_REQUIRED = "Flight identifier is required";
}
