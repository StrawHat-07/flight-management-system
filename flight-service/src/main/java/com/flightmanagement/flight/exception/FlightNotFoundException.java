package com.flightmanagement.flight.exception;

/**
 * Thrown when a requested flight does not exist.
 */
public class FlightNotFoundException extends FlightException {

    private static final String ERROR_CODE = "FLIGHT_NOT_FOUND";

    public FlightNotFoundException(String flightId) {
        super(ERROR_CODE, "Flight not found: " + flightId);
    }
}
