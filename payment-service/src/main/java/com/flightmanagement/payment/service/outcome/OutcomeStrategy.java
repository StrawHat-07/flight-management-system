package com.flightmanagement.payment.service.outcome;

import com.flightmanagement.payment.enums.PaymentStatus;

/**
 * Strategy interface for determining payment outcomes.
 * Allows different mock behaviors for testing.
 */
public interface OutcomeStrategy {

    /**
     * Determines the payment outcome.
     * @return Payment status
     */
    PaymentStatus determine();

    /**
     * Gets the message for a given status.
     * @param status Payment status
     * @return Human-readable message
     */
    String getMessage(PaymentStatus status);
}
