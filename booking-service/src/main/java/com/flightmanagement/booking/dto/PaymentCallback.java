package com.flightmanagement.booking.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentCallback {

    String bookingId;
    String paymentId;
    String status; // SUCCESS, FAILURE, TIMEOUT
    String message;
}
