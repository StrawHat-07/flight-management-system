package com.flightmanagement.flight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationResponse {

    private boolean success;
    private String reservationId;
    private String message;
    private String errorCode;
    private LocalDateTime expiresAt;

    public static InventoryReservationResponse success(String reservationId, LocalDateTime expiresAt) {
        return InventoryReservationResponse.builder()
                .success(true)
                .reservationId(reservationId)
                .expiresAt(expiresAt)
                .build();
    }

    public static InventoryReservationResponse failure(String errorCode, String message) {
        return InventoryReservationResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}
