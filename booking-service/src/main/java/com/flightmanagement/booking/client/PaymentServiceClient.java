package com.flightmanagement.booking.client;

import com.flightmanagement.booking.dto.PaymentRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client for communicating with the Payment Service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentServiceClient {

    final RestTemplate restTemplate;

    @Value("${payment-service.url}")
    String paymentServiceUrl;

    @Value("${booking-service.callback-url:http://booking-service:8083/v1/bookings/payment-callback}")
    String callbackUrl;

    @Async
    public void initiatePayment(String bookingId, String userId, java.math.BigDecimal amount) {
        log.info("Initiating payment for booking {}", bookingId);

        PaymentRequest request = PaymentRequest.builder()
                .bookingId(bookingId)
                .userId(userId)
                .amount(amount)
                .callbackUrl(callbackUrl)
                .build();

        String url = paymentServiceUrl + "/v1/payments/process";

        try {
            restTemplate.postForEntity(url, request, Void.class);
            log.info("Payment initiated successfully for booking {}", bookingId);
        } catch (Exception e) {
            log.error("Failed to initiate payment for booking {}: {}", bookingId, e.getMessage());
            // Payment service will handle retry via TTL expiry
        }
    }
}
