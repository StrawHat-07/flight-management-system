package com.flightmanagement.flight.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComputedFlightEntry implements Serializable {

    private static final long serialVersionUID = 1L;

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FlightLeg implements Serializable {

        private static final long serialVersionUID = 1L;

        String flightId;
        String source;
        String destination;
        LocalDateTime departureTime;
        LocalDateTime arrivalTime;
        BigDecimal price;
        Integer availableSeats;
    }
}
