package com.flightmanagement.flight.exception;

import lombok.Getter;


@Getter
public class FlightException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public FlightException(String errorCode, String message) {
        this(errorCode, message, false, null);
    }

    public FlightException(String errorCode, String message, boolean retryable) {
        this(errorCode, message, retryable, null);
    }

    public FlightException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, false, cause);
    }

    public FlightException(String errorCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
}
