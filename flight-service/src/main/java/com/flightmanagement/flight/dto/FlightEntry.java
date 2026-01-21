package com.flightmanagement.flight.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    @NotBlank(message = "Source is required")
    String source;

    @NotBlank(message = "Destination is required")
    String destination;

    @NotNull(message = "Departure time is required")
    LocalDateTime departureTime;

    @NotNull(message = "Arrival time is required")
    LocalDateTime arrivalTime;

    @NotNull(message = "Total seats is required")
    @Min(value = 1, message = "Total seats must be at least 1")
    Integer totalSeats;

    Integer availableSeats;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price must be non-negative")
    BigDecimal price;

    String status;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;
}
