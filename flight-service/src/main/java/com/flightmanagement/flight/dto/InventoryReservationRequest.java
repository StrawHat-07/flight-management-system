package com.flightmanagement.flight.dto;

import com.flightmanagement.flight.constants.FlightConstants;
import com.flightmanagement.flight.constants.ValidationMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationRequest {

    @NotBlank(message = ValidationMessages.BOOKING_ID_REQUIRED)
    private String bookingId;

    @NotEmpty(message = "At least one flight is required")
    private List<String> flightIds;

    @NotNull(message = ValidationMessages.SEATS_REQUIRED)
    @Min(value = FlightConstants.MIN_SEATS, message = ValidationMessages.SEATS_MIN)
    private Integer seats;

    @Min(value = FlightConstants.DEFAULT_SEAT_BLOCK_TTL_MINUTES, message = "TTL must be at least 1 minute")
    private Integer ttlMinutes;
}
