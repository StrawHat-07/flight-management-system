package com.flightmanagement.booking.service;

import com.flightmanagement.booking.client.FlightServiceClient;
import com.flightmanagement.booking.client.InventoryClient;
import com.flightmanagement.booking.constants.BookingConstants;
import com.flightmanagement.booking.dto.*;
import com.flightmanagement.booking.enums.BookingStatus;
import com.flightmanagement.booking.enums.FlightType;
import com.flightmanagement.booking.exception.BookingException;
import com.flightmanagement.booking.exception.BookingNotFoundException;
import com.flightmanagement.booking.mapper.BookingMapper;
import com.flightmanagement.booking.model.Booking;
import com.flightmanagement.booking.repository.BookingRepository;
import com.flightmanagement.booking.util.BookingFlightResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingFlightResolver flightResolver;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private FlightServiceClient flightServiceClient;

    @Mock
    private PricingService pricingService;

    @Mock
    private PaymentOrchestrator paymentOrchestrator;

    @Mock
    private BookingMapper bookingMapper;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                flightResolver,
                inventoryClient,
                flightServiceClient,
                pricingService,
                paymentOrchestrator,
                bookingMapper
        );
    }

    @Test
    void createBooking_Success() {
        // Given
        BookingRequest request = BookingRequest.builder()
                .userId("user123")
                .flightIdentifier("FL101")
                .seats(2)
                .build();

        String idempotencyKey = "test-key-123";
        List<String> flightIds = List.of("FL101");
        BigDecimal totalPrice = new BigDecimal("500.00");

        when(flightServiceClient.getFlightIds("FL101")).thenReturn(flightIds);
        when(pricingService.calculateTotalPrice("FL101", 2)).thenReturn(totalPrice);
        InventoryReservationResponse reservationResponse = InventoryReservationResponse.builder()
                .success(true)
                .reservationId("BK12345678")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(inventoryClient.reserveSeats(anyString(), eq(flightIds), eq(2), 
                eq(BookingConstants.DEFAULT_SEAT_BLOCK_TTL_MINUTES)))
                .thenReturn(reservationResponse);

        Booking savedBooking = Booking.builder()
                .bookingId("BK12345678")
                .userId("user123")
                .flightType(FlightType.DIRECT)
                .flightIdentifier("FL101")
                .noOfSeats(2)
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .build();
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        BookingEntry expectedEntry = BookingEntry.builder()
                .bookingId("BK12345678")
                .userId("user123")
                .flightType("DIRECT")
                .flightIdentifier("FL101")
                .noOfSeats(2)
                .totalPrice(totalPrice)
                .status("PENDING")
                .flightIds(flightIds)
                .createdAt(savedBooking.getCreatedAt())
                .build();
        when(bookingMapper.toEntry(any(Booking.class), eq(flightIds))).thenReturn(expectedEntry);
        BookingEntry result = bookingService.createBooking(request, idempotencyKey);


        assertThat(result).isNotNull();
        assertThat(result.getBookingId()).isEqualTo("BK12345678");
        assertThat(result.getStatus()).isEqualTo("PENDING");

        verify(inventoryClient).reserveSeats(anyString(), eq(flightIds), eq(2), 
                eq(BookingConstants.DEFAULT_SEAT_BLOCK_TTL_MINUTES));
        verify(paymentOrchestrator).initiatePayment(anyString(), eq("user123"), eq(totalPrice));
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void createBooking_WithIdempotency_ReturnsExisting() {
        // Given
        String idempotencyKey = "test-key-123";
        Booking existingBooking = Booking.builder()
                .bookingId("BK12345678")
                .userId("user123")
                .flightIdentifier("FL101")
                .noOfSeats(2)
                .status(BookingStatus.PENDING)
                .build();

        when(bookingRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingBooking));

        when(flightResolver.getFlightIds("BK12345678"))
                .thenReturn(List.of());

        BookingEntry expectedEntry = BookingEntry.builder()
                .bookingId("BK12345678")
                .build();
        when(bookingMapper.toEntry(existingBooking, List.of())).thenReturn(expectedEntry);

        BookingRequest request = BookingRequest.builder()
                .userId("user123")
                .flightIdentifier("FL101")
                .seats(2)
                .build();

        // When
        BookingEntry result = bookingService.createBooking(request, idempotencyKey);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBookingId()).isEqualTo("BK12345678");
        verify(bookingRepository, never()).save(any());
        verify(inventoryClient, never()).reserveSeats(anyString(), anyList(), anyInt(), anyInt());
    }

    @Test
    void createBooking_FlightNotFound_ThrowsException() {
        // Given
        BookingRequest request = BookingRequest.builder()
                .userId("user123")
                .flightIdentifier("INVALID")
                .seats(2)
                .build();

        when(flightServiceClient.getFlightIds("INVALID")).thenReturn(List.of());

        // When/Then
        assertThatThrownBy(() -> bookingService.createBooking(request, null))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("Invalid flight identifier");

        verify(inventoryClient, never()).reserveSeats(anyString(), anyList(), anyInt(), anyInt());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_NoSeatsAvailable_ThrowsException() {
        // Given
        BookingRequest request = BookingRequest.builder()
                .userId("user123")
                .flightIdentifier("FL101")
                .seats(2)
                .build();

        List<String> flightIds = List.of("FL101");
        when(flightServiceClient.getFlightIds("FL101")).thenReturn(flightIds);
        when(pricingService.calculateTotalPrice("FL101", 2)).thenReturn(new BigDecimal("500.00"));

        // Mock reservation failure
        InventoryReservationResponse reservationResponse = InventoryReservationResponse.builder()
                .success(false)
                .errorCode("NO_SEATS_AVAILABLE")
                .message("Not enough seats available")
                .build();
        when(inventoryClient.reserveSeats(anyString(), eq(flightIds), eq(2), anyInt()))
                .thenReturn(reservationResponse);

        // When/Then
        assertThatThrownBy(() -> bookingService.createBooking(request, null))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("Not enough seats available");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void findById_Success() {
        // Given
        String bookingId = "BK12345678";
        Booking booking = Booking.builder()
                .bookingId(bookingId)
                .userId("user123")
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(flightResolver.getFlightIds(bookingId))
                .thenReturn(List.of());

        BookingEntry expectedEntry = BookingEntry.builder()
                .bookingId(bookingId)
                .status("CONFIRMED")
                .build();
        when(bookingMapper.toEntry(booking, List.of())).thenReturn(expectedEntry);

        // When
        BookingEntry result = bookingService.findById(bookingId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBookingId()).isEqualTo(bookingId);
        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void findById_NotFound_ThrowsException() {
        // Given
        String bookingId = "INVALID";
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> bookingService.findById(bookingId))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void findByUserId_Success() {
        // Given
        String userId = "user123";
        List<Booking> bookings = List.of(
                Booking.builder().bookingId("BK1").userId(userId).build(),
                Booking.builder().bookingId("BK2").userId(userId).build()
        );

        when(bookingRepository.findByUserId(userId)).thenReturn(bookings);
        when(flightResolver.getFlightIds(anyString()))
                .thenReturn(List.of());

        BookingEntry entry1 = BookingEntry.builder().bookingId("BK1").build();
        BookingEntry entry2 = BookingEntry.builder().bookingId("BK2").build();
        when(bookingMapper.toEntry(any(Booking.class), anyList()))
                .thenReturn(entry1, entry2);

        // When
        List<BookingEntry> results = bookingService.findByUserId(userId);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getBookingId()).isEqualTo("BK1");
        assertThat(results.get(1).getBookingId()).isEqualTo("BK2");
    }
}
