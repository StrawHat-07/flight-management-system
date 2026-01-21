package com.flightmanagement.flight.exception;


public class FlightValidationException extends FlightException {

    private static final String ERROR_CODE = "VALIDATION_ERROR";

    public FlightValidationException(String message) {
        super(ERROR_CODE, message);
    }
}
