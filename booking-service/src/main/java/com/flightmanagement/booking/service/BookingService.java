package com.flightmanagement.booking.service;

import com.flightmanagement.booking.client.FlightServiceClient;
import com.flightmanagement.booking.client.InventoryClient;
import com.flightmanagement.booking.constants.BookingConstants;
import com.flightmanagement.booking.constants.ValidationMessages;
import com.flightmanagement.booking.dto.BookingEntry;
import com.flightmanagement.booking.dto.BookingRequest;
import com.flightmanagement.booking.dto.InventoryReservationResponse;
import com.flightmanagement.booking.enums.BookingStatus;
import com.flightmanagement.booking.enums.FlightType;
import com.flightmanagement.booking.exception.BookingException;
import com.flightmanagement.booking.exception.BookingNotFoundException;
import com.flightmanagement.booking.mapper.BookingMapper;
import com.flightmanagement.booking.model.Booking;
import com.flightmanagement.booking.repository.BookingRepository;
import com.flightmanagement.booking.util.BookingFlightResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingFlightResolver flightResolver;
    private final InventoryClient inventoryClient;
    private final FlightServiceClient flightServiceClient;
    private final PricingService pricingService;
    private final PaymentOrchestrator paymentOrchestrator;
    private final BookingMapper bookingMapper;

    @Transactional
    public BookingEntry createBooking(BookingRequest request, String idempotencyKey) {
        log.info("Creating booking: userId={}, flight={}, seats={}",
                request.getUserId(), request.getFlightIdentifier(), request.getSeats());

        Optional<BookingEntry> existing = findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<String> flightIds = resolveFlightIds(request.getFlightIdentifier());
        BigDecimal totalPrice = calculatePrice(request.getFlightIdentifier(), request.getSeats());
        String bookingId = generateBookingId();

        reserveSeats(bookingId, flightIds, request.getSeats());

        Booking booking = createAndSaveBooking(bookingId, request, flightIds, totalPrice, idempotencyKey);

        paymentOrchestrator.initiatePayment(bookingId, request.getUserId(), totalPrice);

        log.info("Booking created: id={}, status=PENDING", bookingId);
        return bookingMapper.toEntry(booking, flightIds);
    }

    public BookingEntry findById(String bookingId) {
        if (!StringUtils.hasText(bookingId)) {
            throw new BookingException("INVALID_BOOKING_ID", ValidationMessages.BOOKING_ID_REQUIRED);
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        return bookingMapper.toEntry(booking, flightResolver.getFlightIds(bookingId));
    }

    public List<BookingEntry> findByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BookingException("INVALID_USER_ID", ValidationMessages.USER_ID_REQUIRED);
        }

        return bookingRepository.findByUserId(userId).stream()
                .map(b -> bookingMapper.toEntry(b, flightResolver.getFlightIds(b.getBookingId())))
                .collect(Collectors.toList());
    }

    private Optional<BookingEntry> findByIdempotencyKey(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }

        return bookingRepository.findByIdempotencyKey(key)
                .map(b -> {
                    log.info("Returning existing booking for idempotency key");
                    return bookingMapper.toEntry(b, flightResolver.getFlightIds(b.getBookingId()));
                });
    }

    private List<String> resolveFlightIds(String flightIdentifier) {
        List<String> flightIds = flightServiceClient.getFlightIds(flightIdentifier);

        if (flightIds.isEmpty()) {
            throw new BookingException("INVALID_FLIGHT", ValidationMessages.INVALID_FLIGHT);
        }

        return flightIds;
    }

    private BigDecimal calculatePrice(String flightIdentifier, int seats) {
        BigDecimal price = pricingService.calculateTotalPrice(flightIdentifier, seats);

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BookingException("PRICE_UNAVAILABLE", ValidationMessages.PRICE_UNAVAILABLE);
        }

        return price;
    }

    private void reserveSeats(String bookingId, List<String> flightIds, int seats) {
        InventoryReservationResponse response = inventoryClient.reserveSeats(
                bookingId, flightIds, seats, BookingConstants.DEFAULT_SEAT_BLOCK_TTL_MINUTES);

        if (!response.isSuccess()) {
            throw new BookingException(response.getErrorCode(), response.getMessage());
        }

        log.info("Seats reserved: expiresAt={}", response.getExpiresAt());
    }

    private Booking createAndSaveBooking(String bookingId, BookingRequest request,
                                         List<String> flightIds, BigDecimal totalPrice,
                                         String idempotencyKey) {
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

        booking = bookingRepository.save(booking);
        flightResolver.saveFlights(bookingId, flightIds);

        return booking;
    }

    private String generateBookingId() {
        return BookingConstants.BOOKING_ID_PREFIX +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
