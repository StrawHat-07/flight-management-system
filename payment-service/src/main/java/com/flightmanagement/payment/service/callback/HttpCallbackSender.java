package com.flightmanagement.payment.service.callback;

import com.flightmanagement.payment.dto.PaymentCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP-based callback sender implementation.
 * Sends payment results back to booking service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HttpCallbackSender implements CallbackSender {

    private final RestTemplate restTemplate;

    @Value("${booking-service.url}")
    private String bookingServiceUrl;

    @Override
    public boolean send(String callbackUrl, PaymentCallback callback) {
        if (!StringUtils.hasText(callbackUrl)) {
            log.warn("No callback URL provided for payment {}", callback.getPaymentId());
            return false;
        }

        try {
            log.info("Sending callback to {} for payment {}", callbackUrl, callback.getPaymentId());
            restTemplate.postForEntity(callbackUrl, callback, String.class);
            log.info("Callback sent successfully for payment {}", callback.getPaymentId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send callback for payment {}: {}", callback.getPaymentId(), e.getMessage());
            return false;
        }
    }

    @Override
    public void sendWithRetry(String callbackUrl, PaymentCallback callback) {
        if (send(callbackUrl, callback)) {
            return;
        }

        // Retry with default URL
        String defaultUrl = bookingServiceUrl + "/v1/bookings/payment-callback";
        if (!defaultUrl.equals(callbackUrl)) {
            log.info("Retrying callback to default URL: {}", defaultUrl);
            send(defaultUrl, callback);
        }
    }
}
