package com.flightmanagement.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a booking is not found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BookingNotFoundException extends RuntimeException {

    private final String bookingId;

    public BookingNotFoundException(String bookingId) {
        super("Booking not found: " + bookingId);
        this.bookingId = bookingId;
    }

    public String getBookingId() {
        return bookingId;
    }
}
