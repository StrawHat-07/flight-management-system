package com.flightmanagement.flight.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryConfirmRequest {

    @NotBlank(message = "Booking ID is required")
    String bookingId;

    @NotEmpty(message = "At least one flight ID is required")
    List<String> flightIds;

    @Min(value = 1, message = "Seats must be at least 1")
    int seats;
}
