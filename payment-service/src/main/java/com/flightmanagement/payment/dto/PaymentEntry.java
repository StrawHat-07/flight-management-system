package com.flightmanagement.payment.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentEntry {

    String paymentId;
    String bookingId;
    String userId;
    BigDecimal amount;
    String status;
    String message;
    LocalDateTime processedAt;
}
