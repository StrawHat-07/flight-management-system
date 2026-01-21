package com.flightmanagement.flight.exception;

/**
 * Thrown when a flight operation fails (e.g., seat booking, cancellation).
 */
public class FlightOperationException extends FlightException {

    public FlightOperationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public FlightOperationException(String errorCode, String message, boolean retryable) {
        super(errorCode, message, retryable);
    }

    public static FlightOperationException noSeatsAvailable(String flightId) {
        return new FlightOperationException("NO_SEATS_AVAILABLE",
                "Insufficient seats on flight: " + flightId, false);
    }

    public static FlightOperationException seatUpdateFailed(String flightId) {
        return new FlightOperationException("SEAT_UPDATE_FAILED",
                "Failed to update seats for flight: " + flightId, true);
    }
}
