package com.flightmanagement.payment.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * DTO for runtime mock configuration.
 * Allows dynamic control of payment behavior for testing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MockConfiguration {

    /**
     * Force all payments to this outcome (SUCCESS, FAILURE, TIMEOUT).
     * If null, uses probability-based outcomes.
     */
    String forcedOutcome;

    /**
     * Success probability (0-100). Used when forcedOutcome is null.
     */
    Integer successProbability;

    /**
     * Failure probability (0-100). Used when forcedOutcome is null.
     */
    Integer failureProbability;

    /**
     * Minimum processing delay in milliseconds.
     */
    Integer minDelayMs;

    /**
     * Maximum processing delay in milliseconds.
     */
    Integer maxDelayMs;

    /**
     * If true, skip processing delay entirely.
     */
    Boolean skipDelay;
}
