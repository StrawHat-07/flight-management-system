package com.flightmanagement.flight.service;

import com.flightmanagement.flight.dto.FlightEntry;
import com.flightmanagement.flight.enums.FlightStatus;
import com.flightmanagement.flight.exception.FlightNotFoundException;
import com.flightmanagement.flight.exception.FlightValidationException;
import com.flightmanagement.flight.model.Flight;
import com.flightmanagement.flight.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FlightService")
class FlightServiceTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private InventoryService inventoryService;

    private FlightService flightService;

    private FlightEntry validEntry;
    private Flight existingFlight;

    @BeforeEach
    void setUp() {
        when(flightRepository.findByStatus(FlightStatus.ACTIVE)).thenReturn(List.of());
        when(inventoryService.syncAllFromDb()).thenReturn(0);
        doNothing().when(inventoryService).setSeats(anyString(), anyInt());

        flightService = new FlightService(flightRepository, inventoryService);

        validEntry = FlightEntry.builder()
                .source("DEL")
                .destination("BLR")
                .departureTime(LocalDateTime.now().plusDays(1))
                .arrivalTime(LocalDateTime.now().plusDays(1).plusHours(3))
                .totalSeats(180)
                .price(new BigDecimal("5500.00"))
                .build();

        existingFlight = Flight.builder()
                .id(1L)
                .flightId("FL101")
                .source("DEL")
                .destination("BLR")
                .departureTime(LocalDateTime.now().plusDays(1))
                .arrivalTime(LocalDateTime.now().plusDays(1).plusHours(3))
                .totalSeats(180)
                .availableSeats(150)
                .price(new BigDecimal("5500.00"))
                .status(FlightStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("createFlight")
    class CreateFlight {

        @Test
        @DisplayName("creates flight with valid data")
        void createsFlightWithValidData() {
            when(flightRepository.existsByFlightId(anyString())).thenReturn(false);
            when(flightRepository.save(any(Flight.class))).thenAnswer(inv -> inv.getArgument(0));

            FlightEntry result = flightService.createFlight(validEntry);

            assertThat(result).isNotNull();
            assertThat(result.getFlightId()).startsWith("FL");
            assertThat(result.getSource()).isEqualTo("DEL");
            assertThat(result.getDestination()).isEqualTo("BLR");
            assertThat(result.getStatus()).isEqualTo("ACTIVE");

            verify(flightRepository).save(any(Flight.class));
            verify(inventoryService).setSeats(anyString(), eq(180));
        }

        @Test
        @DisplayName("rejects null entry")
        void rejectsNullEntry() {
            assertThatThrownBy(() -> flightService.createFlight(null))
                    .isInstanceOf(FlightValidationException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("rejects missing source")
        void rejectsMissingSource() {
            validEntry.setSource(null);

            assertThatThrownBy(() -> flightService.createFlight(validEntry))
                    .isInstanceOf(FlightValidationException.class)
                    .hasMessageContaining("Source");
        }

        @Test
        @DisplayName("rejects same source and destination")
        void rejectsSameSourceDestination() {
            validEntry.setDestination("DEL");

            assertThatThrownBy(() -> flightService.createFlight(validEntry))
                    .isInstanceOf(FlightValidationException.class)
                    .hasMessageContaining("cannot be the same");
        }

        @Test
        @DisplayName("rejects arrival before departure")
        void rejectsInvalidTimes() {
            validEntry.setArrivalTime(validEntry.getDepartureTime().minusHours(1));

            assertThatThrownBy(() -> flightService.createFlight(validEntry))
                    .isInstanceOf(FlightValidationException.class)
                    .hasMessageContaining("after departure");
        }

        @Test
        @DisplayName("rejects zero seats")
        void rejectsZeroSeats() {
            validEntry.setTotalSeats(0);

            assertThatThrownBy(() -> flightService.createFlight(validEntry))
                    .isInstanceOf(FlightValidationException.class)
                    .hasMessageContaining("at least 1");
        }

        @Test
        @DisplayName("rejects negative price")
        void rejectsNegativePrice() {
            validEntry.setPrice(new BigDecimal("-100"));

            assertThatThrownBy(() -> flightService.createFlight(validEntry))
                    .isInstanceOf(FlightValidationException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        @DisplayName("rejects duplicate flight ID")
        void rejectsDuplicateId() {
            validEntry.setFlightId("FL101");
            when(flightRepository.existsByFlightId("FL101")).thenReturn(true);

            assertThatThrownBy(() -> flightService.createFlight(validEntry))
                    .isInstanceOf(FlightValidationException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("getFlightById")
    class GetFlightById {

        @Test
        @DisplayName("returns flight with real-time seat count")
        void returnsFlightWithRealTimeSeats() {
            when(flightRepository.findByFlightId("FL101")).thenReturn(Optional.of(existingFlight));
            when(inventoryService.getAvailableSeats("FL101")).thenReturn(145);

            FlightEntry result = flightService.getFlightById("FL101");

            assertThat(result.getFlightId()).isEqualTo("FL101");
            assertThat(result.getAvailableSeats()).isEqualTo(145);
        }

        @Test
        @DisplayName("throws when flight not found")
        void throwsWhenNotFound() {
            when(flightRepository.findByFlightId("INVALID")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> flightService.getFlightById("INVALID"))
                    .isInstanceOf(FlightNotFoundException.class)
                    .hasMessageContaining("INVALID");
        }

        @Test
        @DisplayName("rejects empty flight ID")
        void rejectsEmptyId() {
            assertThatThrownBy(() -> flightService.getFlightById(""))
                    .isInstanceOf(FlightValidationException.class)
                    .hasMessageContaining("required");
        }
    }

    @Nested
    @DisplayName("seat management")
    class SeatManagement {

        @Test
        @DisplayName("decrements seats successfully via InventoryService")
        void decrementsSeats() {
            when(inventoryService.decrementSeats("FL101", 2)).thenReturn(true);

            boolean result = flightService.decrementSeats("FL101", 2);

            assertThat(result).isTrue();
            verify(inventoryService).decrementSeats("FL101", 2);
        }

        @Test
        @DisplayName("returns false when insufficient seats")
        void returnsFalseWhenInsufficient() {
            when(inventoryService.decrementSeats("FL101", 200)).thenReturn(false);

            boolean result = flightService.decrementSeats("FL101", 200);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("increments seats successfully via InventoryService")
        void incrementsSeats() {
            flightService.incrementSeats("FL101", 5);

            verify(inventoryService).incrementSeats("FL101", 5);
        }

        @Test
        @DisplayName("rejects non-positive seat count")
        void rejectsNonPositiveCount() {
            assertThatThrownBy(() -> flightService.decrementSeats("FL101", 0))
                    .isInstanceOf(FlightValidationException.class)
                    .hasMessageContaining("positive");
        }
    }

    @Nested
    @DisplayName("cancelFlight")
    class CancelFlight {

        @Test
        @DisplayName("soft deletes by setting status to CANCELLED")
        void softDeletes() {
            when(flightRepository.findByFlightId("FL101")).thenReturn(Optional.of(existingFlight));
            when(flightRepository.save(any(Flight.class))).thenReturn(existingFlight);

            flightService.cancelFlight("FL101");

            verify(flightRepository).save(any(Flight.class));
            verify(inventoryService).deleteSeats("FL101");
            assertThat(existingFlight.getStatus()).isEqualTo(FlightStatus.CANCELLED);
        }
    }
}
