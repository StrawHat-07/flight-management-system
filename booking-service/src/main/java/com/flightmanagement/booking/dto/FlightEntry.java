package com.flightmanagement.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlightEntry {

    String flightId;
    String source;
    String destination;
    LocalDateTime departureTime;
    LocalDateTime arrivalTime;
    Integer totalSeats;
    Integer availableSeats;
    BigDecimal price;
    String status;
}
