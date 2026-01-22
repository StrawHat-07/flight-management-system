package com.flightmanagement.payment.service;

import com.flightmanagement.payment.constants.PaymentConstants;
import com.flightmanagement.payment.dto.PaymentCallback;
import com.flightmanagement.payment.dto.PaymentEntry;
import com.flightmanagement.payment.dto.PaymentRequest;
import com.flightmanagement.payment.enums.PaymentStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock payment service for simulating payment processing.
 * 
 * Staff Engineer Design:
 * - Configurable success/failure rates for testing
 * - Async processing with callbacks
 * - Deterministic outcomes for testing (forced outcome)
 * - In-memory storage (would be DB in production)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentService {

    final RestTemplate restTemplate;

    @Value("${booking-service.url}")
    String bookingServiceUrl;

    @Value("${payment.success-probability:" + PaymentConstants.DEFAULT_SUCCESS_PROBABILITY + "}")
    int successProbability;

    @Value("${payment.failure-probability:" + PaymentConstants.DEFAULT_FAILURE_PROBABILITY + "}")
    int failureProbability;

    @Value("${payment.min-processing-delay-ms:" + PaymentConstants.DEFAULT_MIN_PROCESSING_DELAY_MS + "}")
    int minProcessingDelay;

    @Value("${payment.max-processing-delay-ms:" + PaymentConstants.DEFAULT_MAX_PROCESSING_DELAY_MS + "}")
    int maxProcessingDelay;

    final Random random = new Random();

    // In-memory storage for demo (would be DB in production)
    final Map<String, PaymentEntry> payments = new ConcurrentHashMap<>();

    /**
     * Processes payment asynchronously.
     * Simulates processing delay and random outcome.
     */
    @Async
    public void processPaymentAsync(PaymentRequest request) {
        String paymentId = generatePaymentId();

        log.info("Processing payment: id={}, bookingId={}, amount={}",
                paymentId, request.getBookingId(), request.getAmount());

        // Simulate processing time
        simulateProcessingDelay();

        // Determine outcome
        PaymentStatus status = determineOutcome();
        String message = getStatusMessage(status);

        PaymentEntry entry = PaymentEntry.builder()
                .paymentId(paymentId)
                .bookingId(request.getBookingId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .status(status.name())
                .message(message)
                .processedAt(LocalDateTime.now())
                .build();

        payments.put(paymentId, entry);

        log.info("Payment completed: id={}, status={}", paymentId, status);

        // Send callback
        sendCallback(request.getCallbackUrl(), paymentId, request.getBookingId(), status, message);
    }

    /**
     * Processes payment synchronously (for testing).
     */
    public PaymentEntry processPaymentSync(PaymentRequest request) {
        String paymentId = generatePaymentId();

        log.info("Processing payment (sync): paymentId={}, bookingId={}, amount={}", 
                paymentId, request.getBookingId(), request.getAmount());

        // Shorter delay for sync
        try {
            int delay = random.nextInt(PaymentConstants.SYNC_PROCESSING_MAX_DELAY_MS 
                            - PaymentConstants.SYNC_PROCESSING_MIN_DELAY_MS) 
                    + PaymentConstants.SYNC_PROCESSING_MIN_DELAY_MS;
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        PaymentStatus status = determineOutcome();
        String message = getStatusMessage(status);

        PaymentEntry entry = PaymentEntry.builder()
                .paymentId(paymentId)
                .bookingId(request.getBookingId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .status(status.name())
                .message(message)
                .processedAt(LocalDateTime.now())
                .build();

        payments.put(paymentId, entry);

        // Send callback if URL provided
        if (StringUtils.hasText(request.getCallbackUrl())) {
            sendCallback(request.getCallbackUrl(), paymentId, request.getBookingId(), status, message);
        }

        return entry;
    }

    /**
     * Processes payment with forced outcome (for testing).
     */
    public PaymentEntry processPaymentWithOutcome(PaymentRequest request, String forcedOutcome) {
        String paymentId = generatePaymentId();

        PaymentStatus status;
        try {
            status = PaymentStatus.valueOf(forcedOutcome.toUpperCase());
        } catch (IllegalArgumentException e) {
            status = PaymentStatus.FAILURE;
        }

        String message = getStatusMessage(status);

        log.info("Processing payment with forced outcome: id={}, status={}", paymentId, status);

        PaymentEntry entry = PaymentEntry.builder()
                .paymentId(paymentId)
                .bookingId(request.getBookingId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .status(status.name())
                .message(message)
                .processedAt(LocalDateTime.now())
                .build();

        payments.put(paymentId, entry);

        if (StringUtils.hasText(request.getCallbackUrl())) {
            sendCallback(request.getCallbackUrl(), paymentId, request.getBookingId(), status, message);
        }

        return entry;
    }

    /**
     * Finds payment by ID.
     */
    public PaymentEntry findById(String paymentId) {
        return payments.get(paymentId);
    }

    /**
     * Finds payment by booking ID.
     */
    public PaymentEntry findByBookingId(String bookingId) {
        return payments.values().stream()
                .filter(p -> p.getBookingId().equals(bookingId))
                .findFirst()
                .orElse(null);
    }

    // ============ Private Methods ============

    private PaymentStatus determineOutcome() {
        int roll = random.nextInt(100);

        if (roll < successProbability) {
            return PaymentStatus.SUCCESS;
        } else if (roll < successProbability + failureProbability) {
            return PaymentStatus.FAILURE;
        } else {
            return PaymentStatus.TIMEOUT;
        }
    }

    private String getStatusMessage(PaymentStatus status) {
        return switch (status) {
            case SUCCESS -> PaymentConstants.MESSAGE_SUCCESS;
            case FAILURE -> PaymentConstants.MESSAGE_FAILURE;
            case TIMEOUT -> PaymentConstants.MESSAGE_TIMEOUT;
        };
    }

    private void simulateProcessingDelay() {
        try {
            int delay = random.nextInt(maxProcessingDelay - minProcessingDelay) + minProcessingDelay;
            log.debug("Simulating processing delay: {}ms", delay);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendCallback(String callbackUrl, String paymentId, String bookingId,
                              PaymentStatus status, String message) {
        if (!StringUtils.hasText(callbackUrl)) {
            log.warn("No callback URL provided for payment {}", paymentId);
            return;
        }

        PaymentCallback callback = PaymentCallback.builder()
                .bookingId(bookingId)
                .paymentId(paymentId)
                .status(status.name())
                .message(message)
                .build();

        try {
            log.info("Sending callback to {} for payment {}", callbackUrl, paymentId);
            restTemplate.postForEntity(callbackUrl, callback, String.class);
            log.info("Callback sent successfully for payment {}", paymentId);
        } catch (Exception e) {
            log.error("Failed to send callback for payment {}: {}", paymentId, e.getMessage());

            // Retry with default URL
            try {
                String defaultUrl = bookingServiceUrl + "/v1/bookings/payment-callback";
                log.info("Retrying callback to {}", defaultUrl);
                restTemplate.postForEntity(defaultUrl, callback, String.class);
            } catch (Exception retryEx) {
                log.error("Callback retry failed for payment {}: {}", paymentId, retryEx.getMessage());
            }
        }
    }

    private String generatePaymentId() {
        return PaymentConstants.PAYMENT_ID_PREFIX 
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
