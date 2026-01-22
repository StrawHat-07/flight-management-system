package com.flightmanagement.booking.util;

import com.flightmanagement.booking.model.BookingFlight;
import com.flightmanagement.booking.repository.BookingFlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BookingFlightResolver {

    private final BookingFlightRepository bookingFlightRepository;

    public List<String> getFlightIds(String bookingId) {
        return bookingFlightRepository.findByBookingIdOrderByLegOrder(bookingId).stream()
                .map(BookingFlight::getFlightId)
                .collect(Collectors.toList());
    }

    public void saveFlights(String bookingId, List<String> flightIds) {
        for (int i = 0; i < flightIds.size(); i++) {
            bookingFlightRepository.save(BookingFlight.builder()
                    .bookingId(bookingId)
                    .flightId(flightIds.get(i))
                    .legOrder(i)
                    .build());
        }
    }
}
