package com.flightmanagement.payment.dto;

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
    String status;
    String message;
}
