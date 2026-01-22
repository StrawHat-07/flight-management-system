package com.flightmanagement.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComputedFlightEntry {

    String computedFlightId;
    List<String> flightIds;
    String source;
    String destination;
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlightLeg {
        String flightId;
        String source;
        String destination;
        LocalDateTime departureTime;
        LocalDateTime arrivalTime;
        BigDecimal price;
        Integer availableSeats;
    }
}
