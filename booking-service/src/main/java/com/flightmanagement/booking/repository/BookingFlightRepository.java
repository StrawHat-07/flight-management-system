package com.flightmanagement.booking.repository;

import com.flightmanagement.booking.model.BookingFlight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingFlightRepository extends JpaRepository<BookingFlight, Long> {

    List<BookingFlight> findByBookingIdOrderByLegOrder(String bookingId);

    void deleteByBookingId(String bookingId);
}
