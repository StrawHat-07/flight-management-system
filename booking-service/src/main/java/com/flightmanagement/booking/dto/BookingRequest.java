package com.flightmanagement.booking.dto;

import com.flightmanagement.booking.constants.BookingConstants;
import com.flightmanagement.booking.constants.ValidationMessages;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingRequest {

    @NotBlank(message = ValidationMessages.USER_ID_REQUIRED)
    String userId;

    @NotBlank(message = ValidationMessages.FLIGHT_IDENTIFIER_REQUIRED)
    String flightIdentifier;

    @NotNull(message = ValidationMessages.SEATS_REQUIRED)
    @Min(value = BookingConstants.MIN_SEATS_PER_BOOKING, message = ValidationMessages.SEATS_MIN)
    @Max(value = BookingConstants.MAX_SEATS_PER_BOOKING, message = ValidationMessages.SEATS_MAX)
    @Builder.Default
    Integer seats = BookingConstants.MIN_SEATS_PER_BOOKING;
}
