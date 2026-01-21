package com.flightmanagement.flight.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {

    String flightIdentifier;
    String type;
    BigDecimal price;
    Long durationMinutes;
    Integer availableSeats;
    LocalDateTime departureTime;
    LocalDateTime arrivalTime;
    String source;
    String destination;
    Integer hops;
    List<ComputedFlightEntry.FlightLeg> legs;
}
