package com.flightmanagement.payment.service.callback;

import com.flightmanagement.payment.dto.PaymentCallback;

/**
 * Interface for sending payment callbacks.
 * Follows Interface Segregation - only callback concerns.
 */
public interface CallbackSender {


    boolean send(String callbackUrl, PaymentCallback callback);


    void sendWithRetry(String callbackUrl, PaymentCallback callback);
}
