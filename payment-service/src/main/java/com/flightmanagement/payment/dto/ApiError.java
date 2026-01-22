package com.flightmanagement.payment.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApiError {

    String error;
    String message;
    Map<String, String> details;
    boolean retryable;

    @Builder.Default
    String timestamp = Instant.now().toString();
}
