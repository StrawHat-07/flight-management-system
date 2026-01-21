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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookingService Unit Tests")
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingFlightRepository bookingFlightRepository;

    @Mock
    private CacheService cacheService;

    @Mock
    private FlightServiceClient flightServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    private BookingService bookingService;

    private BookingRequest validRequest;
    private Booking existingBooking;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                bookingFlightRepository,
                cacheService,
                flightServiceClient,
                paymentServiceClient,
                5 // seatBlockTtlMinutes
        );

        validRequest = BookingRequest.builder()
                .userId("user123")
                .flightIdentifier("FL001")
                .seats(2)
                .build();

        existingBooking = Booking.builder()
                .bookingId("BK12345678")
                .userId("user123")
                .flightType(FlightType.DIRECT)
                .flightIdentifier("FL001")
                .noOfSeats(2)
                .totalPrice(new BigDecimal("599.98"))
                .status(BookingStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        // Default key format stubs
        when(cacheService.formatAvailableSeatsKey(anyString()))
                .thenAnswer(inv -> "flight:" + inv.getArgument(0) + ":availableSeats");
        when(cacheService.formatBlockedSeatsKey(anyString(), anyString()))
                .thenAnswer(inv -> "flight:" + inv.getArgument(0) + ":blocked:" + inv.getArgument(1));
    }

    @Nested
    @DisplayName("Create Booking Validation Tests")
    class CreateBookingValidationTests {

        @Test
        @DisplayName("Should reject null request")
        void createBooking_NullRequest_ThrowsException() {
            assertThatThrownBy(() -> bookingService.createBooking(null, null))
                    .isInstanceOf(BookingException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "INVALID_REQUEST");
        }

        @Test
        @DisplayName("Should reject missing user ID")
        void createBooking_MissingUserId_ThrowsException() {
            validRequest.setUserId(null);

            assertThatThrownBy(() -> bookingService.createBooking(validRequest, null))
                    .isInstanceOf(BookingException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "INVALID_USER_ID");
        }

        @Test
        @DisplayName("Should reject missing flight identifier")
        void createBooking_MissingFlightId_ThrowsException() {
            validRequest.setFlightIdentifier(null);

            assertThatThrownBy(() -> bookingService.createBooking(validRequest, null))
                    .isInstanceOf(BookingException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "INVALID_FLIGHT_ID");
        }

        @Test
        @DisplayName("Should reject zero seats")
        void createBooking_ZeroSeats_ThrowsException() {
            validRequest.setSeats(0);

            assertThatThrownBy(() -> bookingService.createBooking(validRequest, null))
                    .isInstanceOf(BookingException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "INVALID_SEATS");
        }

        @Test
        @DisplayName("Should reject more than 9 seats")
        void createBooking_TooManySeats_ThrowsException() {
            validRequest.setSeats(10);

            assertThatThrownBy(() -> bookingService.createBooking(validRequest, null))
                    .isInstanceOf(BookingException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "INVALID_SEATS")
                    .hasMessageContaining("Maximum 9 seats");
        }
    }

    @Nested
    @DisplayName("Create Booking Idempotency Tests")
    class CreateBookingIdempotencyTests {

        @Test
        @DisplayName("Should return existing booking for duplicate idempotency key")
        void createBooking_DuplicateIdempotencyKey_ReturnsExisting() {
            when(bookingRepository.findByIdempotencyKey("idemp-123"))
                    .thenReturn(Optional.of(existingBooking));
            when(bookingFlightRepository.findByBookingIdOrderByLegOrder(anyString()))
                    .thenReturn(List.of(BookingFlight.builder().flightId("FL001").build()));

            BookingEntry result = bookingService.createBooking(validRequest, "idemp-123");

            assertThat(result.getBookingId()).isEqualTo("BK12345678");
            verify(flightServiceClient, never()).getFlightIds(anyString());
        }
    }

    @Nested
    @DisplayName("Create Booking Flow Tests")
    class CreateBookingFlowTests {

        @Test
        @DisplayName("Should throw when flight not found")
        void createBooking_FlightNotFound_ThrowsException() {
            when(bookingRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(flightServiceClient.getFlightIds("INVALID")).thenReturn(List.of());

            BookingRequest invalidRequest = BookingRequest.builder()
                    .userId("user123")
                    .flightIdentifier("INVALID")
                    .seats(2)
                    .build();

            assertThatThrownBy(() -> bookingService.createBooking(invalidRequest, null))
                    .isInstanceOf(BookingException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "INVALID_FLIGHT");
        }

        @Test
        @DisplayName("Should throw when no seats available")
        void createBooking_NoSeats_ThrowsException() {
            when(bookingRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(flightServiceClient.getFlightIds("FL001")).thenReturn(List.of("FL001"));
            when(flightServiceClient.getFlightById("FL001"))
                    .thenReturn(Optional.of(FlightEntry.builder()
                            .flightId("FL001")
                            .price(new BigDecimal("299.99"))
                            .build()));
            when(cacheService.executeScript(any(RedisScript.class), anyList(), any()))
                    .thenReturn(0L); // Seat blocking fails

            assertThatThrownBy(() -> bookingService.createBooking(validRequest, null))
                    .isInstanceOf(BookingException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "NO_SEATS_AVAILABLE");
        }
    }

    @Nested
    @DisplayName("Payment Callback Tests")
    class PaymentCallbackTests {

        @Test
        @DisplayName("Should confirm booking on successful payment")
        void handlePaymentCallback_Success_ConfirmsBooking() {
            when(bookingRepository.findById("BK12345678"))
                    .thenReturn(Optional.of(existingBooking));
            when(bookingFlightRepository.findByBookingIdOrderByLegOrder("BK12345678"))
                    .thenReturn(List.of(BookingFlight.builder().flightId("FL001").build()));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentCallback callback = PaymentCallback.builder()
                    .bookingId("BK12345678")
                    .paymentId("PAY123")
                    .status("SUCCESS")
                    .message("Payment successful")
                    .build();

            bookingService.handlePaymentCallback(callback);

            verify(bookingRepository).save(argThat(b -> 
                    b.getStatus() == BookingStatus.CONFIRMED));
            verify(cacheService).deleteBlockedSeats("FL001", "BK12345678");
        }

        @Test
        @DisplayName("Should fail booking on payment failure")
        void handlePaymentCallback_Failure_FailsBooking() {
            when(bookingRepository.findById("BK12345678"))
                    .thenReturn(Optional.of(existingBooking));
            when(bookingFlightRepository.findByBookingIdOrderByLegOrder("BK12345678"))
                    .thenReturn(List.of(BookingFlight.builder().flightId("FL001").build()));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cacheService.getBlockedSeats("FL001", "BK12345678"))
                    .thenReturn(Optional.of(2));

            PaymentCallback callback = PaymentCallback.builder()
                    .bookingId("BK12345678")
                    .paymentId("PAY123")
                    .status("FAILURE")
                    .message("Insufficient funds")
                    .build();

            bookingService.handlePaymentCallback(callback);

            verify(bookingRepository).save(argThat(b -> 
                    b.getStatus() == BookingStatus.FAILED));
            verify(cacheService).incrementAvailableSeats("FL001", 2);
            verify(cacheService).deleteBlockedSeats("FL001", "BK12345678");
        }

        @Test
        @DisplayName("Should ignore callback for non-pending booking")
        void handlePaymentCallback_NotPending_IgnoresCallback() {
            existingBooking.setStatus(BookingStatus.CONFIRMED);
            when(bookingRepository.findById("BK12345678"))
                    .thenReturn(Optional.of(existingBooking));

            PaymentCallback callback = PaymentCallback.builder()
                    .bookingId("BK12345678")
                    .status("SUCCESS")
                    .build();

            bookingService.handlePaymentCallback(callback);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when booking not found")
        void handlePaymentCallback_BookingNotFound_ThrowsException() {
            when(bookingRepository.findById("INVALID"))
                    .thenReturn(Optional.empty());

            PaymentCallback callback = PaymentCallback.builder()
                    .bookingId("INVALID")
                    .status("SUCCESS")
                    .build();

            assertThatThrownBy(() -> bookingService.handlePaymentCallback(callback))
                    .isInstanceOf(BookingNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Find Booking Tests")
    class FindBookingTests {

        @Test
        @DisplayName("Should find booking by ID")
        void findById_Success() {
            when(bookingRepository.findById("BK12345678"))
                    .thenReturn(Optional.of(existingBooking));
            when(bookingFlightRepository.findByBookingIdOrderByLegOrder("BK12345678"))
                    .thenReturn(List.of(BookingFlight.builder().flightId("FL001").build()));

            BookingEntry result = bookingService.findById("BK12345678");

            assertThat(result.getBookingId()).isEqualTo("BK12345678");
            assertThat(result.getUserId()).isEqualTo("user123");
        }

        @Test
        @DisplayName("Should throw when booking not found")
        void findById_NotFound_ThrowsException() {
            when(bookingRepository.findById("INVALID"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.findById("INVALID"))
                    .isInstanceOf(BookingNotFoundException.class);
        }

        @Test
        @DisplayName("Should reject empty booking ID")
        void findById_EmptyId_ThrowsException() {
            assertThatThrownBy(() -> bookingService.findById(""))
                    .isInstanceOf(BookingException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "INVALID_BOOKING_ID");
        }
    }
}
