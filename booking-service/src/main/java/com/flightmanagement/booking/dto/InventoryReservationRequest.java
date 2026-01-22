package com.flightmanagement.booking.dto;

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
    private String bookingId;
    private List<String> flightIds;
    private Integer seats;
    private Integer ttlMinutes;
}
