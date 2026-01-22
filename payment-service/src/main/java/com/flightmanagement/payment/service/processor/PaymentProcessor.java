package com.flightmanagement.payment.service.processor;

import com.flightmanagement.payment.dto.PaymentEntry;
import com.flightmanagement.payment.dto.PaymentRequest;

/**
 * Interface for payment processing operations.
 * Follows Interface Segregation - only processing concerns.
 */
public interface PaymentProcessor {


    PaymentEntry process(PaymentRequest request);

    PaymentEntry processWithOutcome(PaymentRequest request, String forcedOutcome);
}
