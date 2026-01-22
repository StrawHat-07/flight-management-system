package com.flightmanagement.booking.service;

import com.flightmanagement.booking.client.InventoryClient;
import com.flightmanagement.booking.client.PaymentServiceClient;
import com.flightmanagement.booking.dto.PaymentCallback;
import com.flightmanagement.booking.enums.BookingStatus;
import com.flightmanagement.booking.exception.BookingNotFoundException;
import com.flightmanagement.booking.model.Booking;
import com.flightmanagement.booking.repository.BookingRepository;
import com.flightmanagement.booking.util.BookingFlightResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestrator {

    private final BookingRepository bookingRepository;
    private final BookingFlightResolver flightResolver;
    private final InventoryClient inventoryClient;
    private final PaymentServiceClient paymentServiceClient;

    public void initiatePayment(String bookingId, String userId, BigDecimal amount) {
        log.info("Initiating payment: bookingId={}, amount={}", bookingId, amount);

        try {
            paymentServiceClient.initiatePayment(bookingId, userId, amount);
        } catch (Exception e) {
            log.error("Failed to initiate payment: bookingId={}, error={}", bookingId, e.getMessage());
        }
    }

    @Transactional
    public void handlePaymentCallback(PaymentCallback callback) {
        log.info("Payment callback: bookingId={}, status={}", callback.getBookingId(), callback.getStatus());

        Booking booking = bookingRepository.findById(callback.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException(callback.getBookingId()));

        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("Ignoring callback for non-pending booking: {}", booking.getBookingId());
            return;
        }

        List<String> flightIds = flightResolver.getFlightIds(booking.getBookingId());

        switch (callback.getStatus().toUpperCase()) {
            case "SUCCESS" -> confirmBooking(booking, flightIds);
            case "FAILURE", "TIMEOUT" -> failBooking(booking, flightIds, callback.getMessage());
            default -> log.warn("Unknown payment status: {}", callback.getStatus());
        }
    }

    private void confirmBooking(Booking booking, List<String> flightIds) {
        log.info("Confirming booking: {}", booking.getBookingId());

        boolean confirmed = inventoryClient.confirmReservation(
                booking.getBookingId(), flightIds, booking.getNoOfSeats());

        if (confirmed) {
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);
            log.info("Booking confirmed: {}", booking.getBookingId());
        } else {
            log.error("Reservation expired during payment: {}", booking.getBookingId());
            booking.setStatus(BookingStatus.FAILED);
            bookingRepository.save(booking);
        }
    }

    private void failBooking(Booking booking, List<String> flightIds, String reason) {
        log.info("Failing booking: {}, reason={}", booking.getBookingId(), reason);

        booking.setStatus(BookingStatus.FAILED);
        bookingRepository.save(booking);

        inventoryClient.releaseReservation(booking.getBookingId(), flightIds);
        log.info("Reservation released: {}", booking.getBookingId());
    }
}
