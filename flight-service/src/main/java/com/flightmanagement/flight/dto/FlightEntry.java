package com.flightmanagement.flight.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flightmanagement.flight.constants.ValidationMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlightEntry {

    Long id;

    String flightId;

    @NotBlank(message = ValidationMessages.SOURCE_REQUIRED)
    String source;

    @NotBlank(message = ValidationMessages.DESTINATION_REQUIRED)
    String destination;

    @NotNull(message = ValidationMessages.DEPARTURE_TIME_REQUIRED)
    LocalDateTime departureTime;

    @NotNull(message = ValidationMessages.ARRIVAL_TIME_REQUIRED)
    LocalDateTime arrivalTime;

    @NotNull(message = ValidationMessages.TOTAL_SEATS_REQUIRED)
    @Min(value = 1, message = ValidationMessages.TOTAL_SEATS_MIN)
    Integer totalSeats;

    Integer availableSeats;

    @NotNull(message = ValidationMessages.PRICE_REQUIRED)
    @Min(value = 0, message = ValidationMessages.PRICE_NON_NEGATIVE)
    BigDecimal price;

    String status;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;
}
