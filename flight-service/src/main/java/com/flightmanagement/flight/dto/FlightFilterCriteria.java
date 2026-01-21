package com.flightmanagement.flight.dto;

import com.flightmanagement.flight.enums.FlightStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FlightFilterCriteria {

    String source;
    String destination;
    LocalDate departureDate;
    FlightStatus status;
    BigDecimal minPrice;
    BigDecimal maxPrice;
    Integer minAvailableSeats;
}
