package com.flightmanagement.booking.service;

import com.flightmanagement.booking.client.InventoryClient;
import com.flightmanagement.booking.constants.BookingConstants;
import com.flightmanagement.booking.enums.BookingStatus;
import com.flightmanagement.booking.model.Booking;
import com.flightmanagement.booking.repository.BookingRepository;
import com.flightmanagement.booking.util.BookingFlightResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryService {

    private final BookingRepository bookingRepository;
    private final BookingFlightResolver flightResolver;
    private final InventoryClient inventoryClient;

    @Value("${booking.seat-block-ttl-minutes:5}")
    private int seatBlockTtlMinutes;

    @Scheduled(fixedDelay = BookingConstants.BOOKING_EXPIRY_CHECK_INTERVAL_MS)
    @Transactional
    public void processExpiredBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minus(seatBlockTtlMinutes, ChronoUnit.MINUTES);

        List<Booking> expiredBookings = bookingRepository
                .findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff);

        if (expiredBookings.isEmpty()) {
            return;
        }

        log.info("Processing {} expired bookings", expiredBookings.size());
        expiredBookings.forEach(this::processExpiredBooking);
    }

    private void processExpiredBooking(Booking booking) {
        try {
            List<String> flightIds = flightResolver.getFlightIds(booking.getBookingId());

            inventoryClient.releaseReservation(booking.getBookingId(), flightIds);

            booking.setStatus(BookingStatus.TIMEOUT);
            bookingRepository.save(booking);

            log.info("Expired booking processed: id={}, seats={}", 
                    booking.getBookingId(), booking.getNoOfSeats());

        } catch (Exception e) {
            log.error("Error processing expired booking {}: {}", booking.getBookingId(), e.getMessage());
        }
    }
}
