package com.flightmanagement.booking.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentRequest {

    String bookingId;
    String userId;
    BigDecimal amount;
    String callbackUrl;
}
