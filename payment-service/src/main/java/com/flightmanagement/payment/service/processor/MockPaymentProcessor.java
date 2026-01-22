package com.flightmanagement.payment.service.processor;

import com.flightmanagement.payment.constants.PaymentConstants;
import com.flightmanagement.payment.dto.PaymentEntry;
import com.flightmanagement.payment.dto.PaymentRequest;
import com.flightmanagement.payment.enums.PaymentStatus;
import com.flightmanagement.payment.service.outcome.OutcomeStrategy;
import com.flightmanagement.payment.service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Mock payment processor for testing.
 * Simulates payment gateway behavior with configurable delays.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MockPaymentProcessor implements PaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final OutcomeStrategy outcomeStrategy;
    private final Random random = new Random();

    @Value("${payment.min-processing-delay-ms:" + PaymentConstants.DEFAULT_MIN_PROCESSING_DELAY_MS + "}")
    private int minDelay;

    @Value("${payment.max-processing-delay-ms:" + PaymentConstants.DEFAULT_MAX_PROCESSING_DELAY_MS + "}")
    private int maxDelay;

    @Override
    public PaymentEntry process(PaymentRequest request) {
        String paymentId = generatePaymentId();

        log.info("Processing payment: id={}, bookingId={}, amount={}",
                paymentId, request.getBookingId(), request.getAmount());

        // Check idempotency
        var existing = paymentRepository.findByBookingId(request.getBookingId());
        if (existing.isPresent()) {
            log.info("Idempotent request - returning existing payment: {}", existing.get().getPaymentId());
            return existing.get();
        }

        // Simulate processing delay
        simulateDelay();

        // Determine outcome
        PaymentStatus status = outcomeStrategy.determine();
        String message = outcomeStrategy.getMessage(status);

        PaymentEntry entry = buildEntry(paymentId, request, status, message);
        paymentRepository.save(entry);

        log.info("Payment completed: id={}, status={}", paymentId, status);
        return entry;
    }

    @Override
    public PaymentEntry processWithOutcome(PaymentRequest request, String forcedOutcome) {
        String paymentId = generatePaymentId();

        // Check idempotency
        var existing = paymentRepository.findByBookingId(request.getBookingId());
        if (existing.isPresent()) {
            log.info("Idempotent request - returning existing payment: {}", existing.get().getPaymentId());
            return existing.get();
        }

        PaymentStatus status;
        try {
            status = PaymentStatus.valueOf(forcedOutcome.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid forced outcome '{}', defaulting to FAILURE", forcedOutcome);
            status = PaymentStatus.FAILURE;
        }

        String message = outcomeStrategy.getMessage(status);

        log.info("Processing payment with forced outcome: id={}, status={}", paymentId, status);

        PaymentEntry entry = buildEntry(paymentId, request, status, message);
        paymentRepository.save(entry);

        return entry;
    }

    private PaymentEntry buildEntry(String paymentId, PaymentRequest request,
                                    PaymentStatus status, String message) {
        return PaymentEntry.builder()
                .paymentId(paymentId)
                .bookingId(request.getBookingId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .status(status.name())
                .message(message)
                .processedAt(LocalDateTime.now())
                .build();
    }

    private void simulateDelay() {
        try {
            int delay = random.nextInt(maxDelay - minDelay) + minDelay;
            log.debug("Simulating processing delay: {}ms", delay);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String generatePaymentId() {
        return PaymentConstants.PAYMENT_ID_PREFIX
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
