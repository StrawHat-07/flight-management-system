package com.flightmanagement.booking.constants;

public final class ValidationMessages {

    private ValidationMessages() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    public static final String BOOKING_REQUEST_REQUIRED = "Booking request is required";
    public static final String USER_ID_REQUIRED = "User ID is required";
    public static final String FLIGHT_IDENTIFIER_REQUIRED = "Flight identifier is required";
    public static final String BOOKING_ID_REQUIRED = "Booking ID is required";
    
    public static final String SEATS_REQUIRED = "Number of seats is required";
    public static final String SEATS_MIN = "At least 1 seat is required";
    public static final String SEATS_MAX = "Maximum 9 seats per booking";
    
    public static final String INVALID_FLIGHT = "Invalid flight identifier";
    public static final String NO_SEATS_AVAILABLE = "Not enough seats available on one or more flights";
    public static final String PRICE_UNAVAILABLE = "Unable to determine flight price";
    
    public static final String BOOKING_NOT_FOUND = "Booking not found";
}
