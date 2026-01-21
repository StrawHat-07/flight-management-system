package com.flightmanagement.booking.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing a computed (multi-hop) flight route.
 * Must match the structure returned by Flight Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ComputedFlightEntry {

    String computedFlightId;
    List<String> flightIds;
    String src;
    String dest;
    BigDecimal totalPrice;
    Long totalDurationMinutes;
    Integer availableSeats;
    Integer numberOfHops;
    LocalDateTime departureTime;
    LocalDateTime arrivalTime;
    List<FlightLeg> legs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class FlightLeg {
        String flightId;
        String src;
        String dest;
        LocalDateTime departureTime;
        LocalDateTime arrivalTime;
        BigDecimal price;
        Integer availableSeats;
    }
}
