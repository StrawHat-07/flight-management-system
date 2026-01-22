package com.flightmanagement.payment.service;

import com.flightmanagement.payment.constants.PaymentConstants;
import com.flightmanagement.payment.dto.PaymentCallback;
import com.flightmanagement.payment.dto.PaymentEntry;
import com.flightmanagement.payment.dto.PaymentRequest;
import com.flightmanagement.payment.enums.PaymentStatus;
import com.flightmanagement.payment.service.callback.CallbackSender;
import com.flightmanagement.payment.service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment orchestration service.
 * Coordinates payment processing, storage, and callbacks.
 * 
 * Design principles:
 * - Delegates to specialized components (SRP)
 * - Depends on abstractions (DIP)
 * - Idempotent operations
 * - Mock-friendly for E2E testing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CallbackSender callbackSender;
    private final MockConfigurationService mockConfig;

    /**
     * Processes payment asynchronously.
     * Simulates processing delay and sends callback.
     */
    @Async
    public void processPaymentAsync(PaymentRequest request) {
        log.info("Processing payment async: bookingId={}, amount={}",
                request.getBookingId(), request.getAmount());

        // Idempotency check
        Optional<PaymentEntry> existing = paymentRepository.findByBookingId(request.getBookingId());
        if (existing.isPresent()) {
            log.info("Idempotent request - payment already exists: {}", existing.get().getPaymentId());
            sendCallbackIfNeeded(request.getCallbackUrl(), existing.get());
            return;
        }

        // Simulate processing
        simulateProcessingDelay();
        PaymentEntry entry = processAndSave(request, null);
        sendCallbackIfNeeded(request.getCallbackUrl(), entry);
    }

    /**
     * Processes payment synchronously.
     * Used for testing when immediate response is needed.
     */
    public PaymentEntry processPaymentSync(PaymentRequest request) {
        log.info("Processing payment sync: bookingId={}, amount={}",
                request.getBookingId(), request.getAmount());

        // Idempotency check
        Optional<PaymentEntry> existing = paymentRepository.findByBookingId(request.getBookingId());
        if (existing.isPresent()) {
            log.info("Idempotent request - returning existing payment: {}", existing.get().getPaymentId());
            return existing.get();
        }

        // Shorter delay for sync
        int delay = mockConfig.getProcessingDelay() / 3;
        if (delay > 0) {
            sleep(Math.min(delay, 1500));
        }

        // Process and save
        PaymentEntry entry = processAndSave(request, null);
        if (StringUtils.hasText(request.getCallbackUrl())) {
            sendCallbackIfNeeded(request.getCallbackUrl(), entry);
        }

        return entry;
    }

    /**
     * Processes payment with forced outcome.
     * Used for deterministic testing scenarios.
     */
    public PaymentEntry processPaymentWithOutcome(PaymentRequest request, String forcedOutcome) {
        log.info("Processing payment with forced outcome: bookingId={}, outcome={}",
                request.getBookingId(), forcedOutcome);

        // Idempotency check
        Optional<PaymentEntry> existing = paymentRepository.findByBookingId(request.getBookingId());
        if (existing.isPresent()) {
            log.info("Idempotent request - returning existing payment: {}", existing.get().getPaymentId());
            return existing.get();
        }

        // Process with forced outcome
        PaymentEntry entry = processAndSave(request, forcedOutcome);

        // Send callback if URL provided
        if (StringUtils.hasText(request.getCallbackUrl())) {
            sendCallbackIfNeeded(request.getCallbackUrl(), entry);
        }

        return entry;
    }

    /**
     * Finds payment by payment ID.
     */
    public Optional<PaymentEntry> findById(String paymentId) {
        return paymentRepository.findById(paymentId);
    }

    /**
     * Finds payment by booking ID.
     */
    public Optional<PaymentEntry> findByBookingId(String bookingId) {
        return paymentRepository.findByBookingId(bookingId);
    }

    // ============ Private Methods ============

    private PaymentEntry processAndSave(PaymentRequest request, String forcedOutcome) {
        String paymentId = generatePaymentId();

        PaymentStatus status;
        if (forcedOutcome != null) {
            try {
                status = PaymentStatus.valueOf(forcedOutcome.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid forced outcome '{}', using configured behavior", forcedOutcome);
                status = mockConfig.determineOutcome();
            }
        } else {
            status = mockConfig.determineOutcome();
        }

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

        paymentRepository.save(entry);
        log.info("Payment processed: id={}, status={}", paymentId, status);

        return entry;
    }

    private void sendCallbackIfNeeded(String callbackUrl, PaymentEntry entry) {
        PaymentCallback callback = PaymentCallback.builder()
                .bookingId(entry.getBookingId())
                .paymentId(entry.getPaymentId())
                .status(entry.getStatus())
                .message(entry.getMessage())
                .build();

        callbackSender.sendWithRetry(callbackUrl, callback);
    }

    private void simulateProcessingDelay() {
        int delay = mockConfig.getProcessingDelay();
        if (delay > 0) {
            sleep(delay);
        }
    }

    private void sleep(int ms) {
        try {
            log.debug("Simulating delay: {}ms", ms);
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String getStatusMessage(PaymentStatus status) {
        return switch (status) {
            case SUCCESS -> PaymentConstants.MESSAGE_SUCCESS;
            case FAILURE -> PaymentConstants.MESSAGE_FAILURE;
            case TIMEOUT -> PaymentConstants.MESSAGE_TIMEOUT;
        };
    }

    private String generatePaymentId() {
        return PaymentConstants.PAYMENT_ID_PREFIX
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
