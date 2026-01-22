package com.flightmanagement.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryReservationResponse {
    private boolean success;
    private String reservationId;
    private String message;
    private String errorCode;
    private LocalDateTime expiresAt;
}
