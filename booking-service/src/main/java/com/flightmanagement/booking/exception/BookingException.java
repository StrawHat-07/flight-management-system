package com.flightmanagement.booking.exception;

import lombok.Getter;

@Getter
public class BookingException extends RuntimeException {

    private final String errorCode;

    public BookingException(String message) {
        super(message);
        this.errorCode = "BOOKING_ERROR";
    }

    public BookingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
