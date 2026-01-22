package com.flightmanagement.booking.mapper;

import com.flightmanagement.booking.dto.BookingEntry;
import com.flightmanagement.booking.model.Booking;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BookingMapper {

    public BookingEntry toEntry(Booking booking, List<String> flightIds) {
        if (booking == null) {
            return null;
        }

        return BookingEntry.builder()
                .bookingId(booking.getBookingId())
                .userId(booking.getUserId())
                .flightType(booking.getFlightType().name())
                .flightIdentifier(booking.getFlightIdentifier())
                .noOfSeats(booking.getNoOfSeats())
                .totalPrice(booking.getTotalPrice())
                .status(booking.getStatus().name())
                .flightIds(flightIds)
                .createdAt(booking.getCreatedAt())
                .build();
    }

    public BookingEntry toEntry(Booking booking) {
        return toEntry(booking, null);
    }
}
