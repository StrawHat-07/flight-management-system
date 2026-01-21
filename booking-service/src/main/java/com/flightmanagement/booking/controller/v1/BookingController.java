package com.flightmanagement.booking.controller.v1;

import com.flightmanagement.booking.dto.BookingEntry;
import com.flightmanagement.booking.dto.BookingRequest;
import com.flightmanagement.booking.dto.PaymentCallback;
import com.flightmanagement.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/bookings")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BookingController {

    BookingService bookingService;

    /**
     * Creates a new booking.
     * POST /v1/bookings
     * 
     * @param request        Booking details
     * @param idempotencyKey Optional header for idempotent requests
     * @return Created booking entry
     */
    @PostMapping
    public ResponseEntity<BookingEntry> create(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        log.info("POST /v1/bookings - flight={}, user={}, idempotencyKey={}",
                request.getFlightIdentifier(), request.getUserId(),
                idempotencyKey != null ? "[present]" : "[absent]");

        BookingEntry entry = bookingService.createBooking(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    /**
     * Quick booking endpoint.
     * POST /v1/bookings/book/{flightIdentifier}?userId=xxx&seats=2
     */
    @PostMapping("/book/{flightIdentifier}")
    public ResponseEntity<BookingEntry> bookFlight(
            @PathVariable String flightIdentifier,
            @RequestParam String userId,
            @RequestParam(defaultValue = "1") Integer seats,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        log.info("POST /v1/bookings/book/{} - user={}, seats={}", flightIdentifier, userId, seats);

        BookingRequest request = BookingRequest.builder()
                .userId(userId)
                .flightIdentifier(flightIdentifier)
                .seats(seats)
                .build();

        BookingEntry entry = bookingService.createBooking(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    /**
     * Gets a booking by ID.
     * GET /v1/bookings/{bookingId}
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingEntry> findById(@PathVariable String bookingId) {
        log.debug("GET /v1/bookings/{}", bookingId);
        return ResponseEntity.ok(bookingService.findById(bookingId));
    }

    /**
     * Gets all bookings for a user.
     * GET /v1/bookings/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingEntry>> findByUser(@PathVariable String userId) {
        log.debug("GET /v1/bookings/user/{}", userId);
        return ResponseEntity.ok(bookingService.findByUserId(userId));
    }

    /**
     * Payment callback endpoint.
     * POST /v1/bookings/payment-callback
     * 
     * Called by payment service to report payment result.
     */
    @PostMapping("/payment-callback")
    public ResponseEntity<Map<String, String>> paymentCallback(@RequestBody PaymentCallback callback) {
        log.info("POST /v1/bookings/payment-callback - bookingId={}, status={}",
                callback.getBookingId(), callback.getStatus());

        bookingService.handlePaymentCallback(callback);
        return ResponseEntity.ok(Map.of(
                "status", "PROCESSED",
                "bookingId", callback.getBookingId()
        ));
    }
}


    