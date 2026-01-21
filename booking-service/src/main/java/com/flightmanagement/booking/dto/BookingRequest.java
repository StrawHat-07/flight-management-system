package com.flightmanagement.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingRequest {

    @NotBlank(message = "User ID is required")
    String userId;

    @NotBlank(message = "Flight identifier is required")
    String flightIdentifier;

    @Min(value = 1, message = "At least 1 seat required")
    @Builder.Default
    Integer seats = 1;
}
