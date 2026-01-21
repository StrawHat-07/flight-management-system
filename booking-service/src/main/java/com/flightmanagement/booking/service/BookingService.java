package com.flightmanagement.booking.service;

import com.flightmanagement.booking.client.FlightServiceClient;
import com.flightmanagement.booking.client.PaymentServiceClient;
import com.flightmanagement.booking.dto.*;
import com.flightmanagement.booking.enums.BookingStatus;
import com.flightmanagement.booking.enums.FlightType;
import com.flightmanagement.booking.exception.BookingException;
import com.flightmanagement.booking.exception.BookingNotFoundException;
import com.flightmanagement.booking.model.Booking;
import com.flightmanagement.booking.model.BookingFlight;
import com.flightmanagement.booking.repository.BookingFlightRepository;
import com.flightmanagement.booking.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing flight bookings.
 * Implements Redis-based seat blocking with TTL for automatic rollback.
 */
@Service
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingFlightRepository bookingFlightRepository;
    private final CacheService cacheService;
    private final FlightServiceClient flightServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    private final int seatBlockTtlMinutes;

    // Lua script for atomic seat check and block
    private static final String BLOCK_SEATS_SCRIPT = """
            local seats = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])

            -- Check all flights have enough seats
            for i = 1, #KEYS / 2 do
                local availableKey = KEYS[i]
                local available = tonumber(redis.call('GET', availableKey) or '0')
                if available < seats then
                    return 0
                end
            end

            -- Block seats for all flights
            for i = 1, #KEYS / 2 do
                local availableKey = KEYS[i]
                local blockedKey = KEYS[#KEYS / 2 + i]
                redis.call('DECRBY', availableKey, seats)
                redis.call('SET', blockedKey, seats, 'EX', ttl)
            end

            return 1
            """;

    public BookingService(
            BookingRepository bookingRepository,
            BookingFlightRepository bookingFlightRepository,
            CacheService cacheService,
            FlightServiceClient flightServiceClient,
            PaymentServiceClient paymentServiceClient,
            @Value("${booking.seat-block-ttl-minutes:5}") int seatBlockTtlMinutes) {
        this.bookingRepository = bookingRepository;
        this.bookingFlightRepository = bookingFlightRepository;
        this.cacheService = cacheService;
        this.flightServiceClient = flightServiceClient;
        this.paymentServiceClient = paymentServiceClient;
        this.seatBlockTtlMinutes = seatBlockTtlMinutes;
    }

    /**
     * Creates a new booking with idempotency support.
     * Blocks seats atomically in Redis and initiates payment.
     */
    @Transactional
    public BookingEntry createBooking(BookingRequest request, String idempotencyKey) {
        validateBookingRequest(request);

        log.info("Creating booking: user={}, flight={}, seats={}",
                request.getUserId(), request.getFlightIdentifier(), request.getSeats());

        // Check idempotency
        if (StringUtils.hasText(idempotencyKey)) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Returning existing booking for idempotency key: {}", idempotencyKey);
                return toEntry(existing.get());
            }
        }

        // Get flight information
        List<String> flightIds = flightServiceClient.getFlightIds(request.getFlightIdentifier());
        if (flightIds.isEmpty()) {
            throw new BookingException("INVALID_FLIGHT", "Flight not found: " + request.getFlightIdentifier());
        }

        BigDecimal totalPrice = calculateTotalPrice(request.getFlightIdentifier(), request.getSeats());
        if (totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BookingException("PRICE_UNAVAILABLE", "Unable to determine flight price");
        }

        String bookingId = generateBookingId();

        // Atomic seat blocking
        boolean seatsBlocked = blockSeats(flightIds, request.getSeats(), bookingId);
        if (!seatsBlocked) {
            throw new BookingException("NO_SEATS_AVAILABLE", "Not enough seats available on one or more flights");
        }

        // Create booking
        Booking booking = Booking.builder()
                .bookingId(bookingId)
                .userId(request.getUserId())
                .flightType(flightIds.size() == 1 ? FlightType.DIRECT : FlightType.COMPUTED)
                .flightIdentifier(request.getFlightIdentifier())
                .noOfSeats(request.getSeats())
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        bookingRepository.save(booking);

        // Save flight associations
        saveBookingFlights(bookingId, flightIds);

        // Initiate payment (async)
        paymentServiceClient.initiatePayment(bookingId, request.getUserId(), totalPrice);

        log.info("Booking created: id={}, status=PENDING", bookingId);
        return toEntry(booking, flightIds);
    }

    /**
     * Handles payment callback from payment service.
     * Updates booking status and manages seat inventory.
     */
    @Transactional
    public void handlePaymentCallback(PaymentCallback callback) {
        log.info("Processing payment callback: bookingId={}, status={}",
                callback.getBookingId(), callback.getStatus());

        Booking booking = bookingRepository.findById(callback.getBookingId())
                .orElseThrow(() -> {
                    log.error("Booking not found for callback: {}", callback.getBookingId());
                    return new BookingNotFoundException(callback.getBookingId());
                });

        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("Ignoring callback for non-pending booking: id={}, status={}",
                    booking.getBookingId(), booking.getStatus());
            return;
        }

        List<String> flightIds = getFlightIdsForBooking(booking.getBookingId());

        switch (callback.getStatus().toUpperCase()) {
            case "SUCCESS" -> handlePaymentSuccess(booking, flightIds);
            case "FAILURE", "TIMEOUT" -> handlePaymentFailure(booking, flightIds, callback.getMessage());
            default -> log.warn("Unknown payment status: {}", callback.getStatus());
        }
    }

    /**
     * Finds a booking by ID.
     */
    public BookingEntry findById(String bookingId) {
        if (!StringUtils.hasText(bookingId)) {
            throw new BookingException("INVALID_BOOKING_ID", "Booking ID is required");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        return toEntry(booking);
    }

    /**
     * Finds all bookings for a user.
     */
    public List<BookingEntry> findByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BookingException("INVALID_USER_ID", "User ID is required");
        }

        return bookingRepository.findByUserId(userId).stream()
                .map(this::toEntry)
                .collect(Collectors.toList());
    }

    // ============ Private Methods ============

    private boolean blockSeats(List<String> flightIds, int seats, String bookingId) {
        log.debug("Blocking {} seats for {} flights, booking={}", seats, flightIds.size(), bookingId);

        List<String> keys = new ArrayList<>();
        // First half: available seat keys
        for (String flightId : flightIds) {
            keys.add(cacheService.formatAvailableSeatsKey(flightId));
        }
        // Second half: blocked seat keys
        for (String flightId : flightIds) {
            keys.add(cacheService.formatBlockedSeatsKey(flightId, bookingId));
        }

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(BLOCK_SEATS_SCRIPT);
            script.setResultType(Long.class);

            Long result = cacheService.executeScript(
                    script,
                    keys,
                    String.valueOf(seats),
                    String.valueOf(seatBlockTtlMinutes * 60));

            boolean success = result != null && result == 1L;
            log.info("Seat blocking {}: booking={}", success ? "SUCCESS" : "FAILED", bookingId);
            return success;

        } catch (Exception e) {
            log.error("Error blocking seats for booking {}: {}", bookingId, e.getMessage());
            return false;
        }
    }

    private void handlePaymentSuccess(Booking booking, List<String> flightIds) {
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // Remove blocked keys and update MySQL seats
        for (String flightId : flightIds) {
            cacheService.deleteBlockedSeats(flightId, booking.getBookingId());

            // Sync to MySQL (best effort)
            flightServiceClient.decrementSeats(flightId, booking.getNoOfSeats());
        }

        log.info("Booking confirmed: {}", booking.getBookingId());
    }

    private void handlePaymentFailure(Booking booking, List<String> flightIds, String reason) {
        booking.setStatus(BookingStatus.FAILED);
        bookingRepository.save(booking);

        // Release blocked seats
        for (String flightId : flightIds) {
            // Only release if blocked key still exists (TTL might have expired)
            Optional<Integer> blockedSeats = cacheService.getBlockedSeats(flightId, booking.getBookingId());
            if (blockedSeats.isPresent()) {
                cacheService.incrementAvailableSeats(flightId, booking.getNoOfSeats());
                cacheService.deleteBlockedSeats(flightId, booking.getBookingId());
            }
        }

        log.info("Booking failed: {}, reason={}", booking.getBookingId(), reason);
    }

    private BigDecimal calculateTotalPrice(String flightIdentifier, int seats) {
        if (!flightIdentifier.startsWith("CF_")) {
            // Direct flight
            return flightServiceClient.getFlightById(flightIdentifier)
                    .map(f -> f.getPrice().multiply(BigDecimal.valueOf(seats)))
                    .orElse(BigDecimal.ZERO);
        } else {
            // Computed flight
            return flightServiceClient.getComputedFlightById(flightIdentifier)
                    .map(cf -> cf.getTotalPrice().multiply(BigDecimal.valueOf(seats)))
                    .orElse(BigDecimal.ZERO);
        }
    }

    private void saveBookingFlights(String bookingId, List<String> flightIds) {
        for (int i = 0; i < flightIds.size(); i++) {
            BookingFlight bf = BookingFlight.builder()
                    .bookingId(bookingId)
                    .flightId(flightIds.get(i))
                    .legOrder(i)
                    .build();
            bookingFlightRepository.save(bf);
        }
    }

    private List<String> getFlightIdsForBooking(String bookingId) {
        return bookingFlightRepository.findByBookingIdOrderByLegOrder(bookingId).stream()
                .map(BookingFlight::getFlightId)
                .collect(Collectors.toList());
    }

    private void validateBookingRequest(BookingRequest request) {
        if (request == null) {
            throw new BookingException("INVALID_REQUEST", "Booking request is required");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new BookingException("INVALID_USER_ID", "User ID is required");
        }
        if (!StringUtils.hasText(request.getFlightIdentifier())) {
            throw new BookingException("INVALID_FLIGHT_ID", "Flight identifier is required");
        }
        if (request.getSeats() == null || request.getSeats() < 1) {
            throw new BookingException("INVALID_SEATS", "At least 1 seat is required");
        }
        if (request.getSeats() > 9) {
            throw new BookingException("INVALID_SEATS", "Maximum 9 seats per booking");
        }
    }

    private String generateBookingId() {
        return "BK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private BookingEntry toEntry(Booking booking) {
        List<String> flightIds = getFlightIdsForBooking(booking.getBookingId());
        return toEntry(booking, flightIds);
    }

    private BookingEntry toEntry(Booking booking, List<String> flightIds) {
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
}
