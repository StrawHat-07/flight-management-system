package com.flightmanagement.payment.controller.v1;

import com.flightmanagement.payment.dto.PaymentEntry;
import com.flightmanagement.payment.dto.PaymentRequest;
import com.flightmanagement.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for payment operations.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {

    PaymentService paymentService;

    /**
     * Processes payment asynchronously.
     * POST /v1/payments/process
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processPayment(@Valid @RequestBody PaymentRequest request) {
        log.info("POST /v1/payments/process - bookingId={}", request.getBookingId());

        paymentService.processPaymentAsync(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "status", "PROCESSING",
                        "message", "Payment is being processed",
                        "bookingId", request.getBookingId()
                ));
    }

    /**
     * Processes payment synchronously (for testing).
     * POST /v1/payments/process-sync
     */
    @PostMapping("/process-sync")
    public ResponseEntity<PaymentEntry> processPaymentSync(@Valid @RequestBody PaymentRequest request) {
        log.info("POST /v1/payments/process-sync - bookingId={}", request.getBookingId());

        PaymentEntry entry = paymentService.processPaymentSync(request);
        return ResponseEntity.ok(entry);
    }

    /**
     * Processes payment with forced outcome (for testing).
     * POST /v1/payments/process-with-outcome?outcome=SUCCESS
     */
    @PostMapping("/process-with-outcome")
    public ResponseEntity<PaymentEntry> processPaymentWithOutcome(
            @Valid @RequestBody PaymentRequest request,
            @RequestParam String outcome) {

        log.info("POST /v1/payments/process-with-outcome - bookingId={}, outcome={}",
                request.getBookingId(), outcome);

        PaymentEntry entry = paymentService.processPaymentWithOutcome(request, outcome);
        return ResponseEntity.ok(entry);
    }

    /**
     * Gets payment by ID.
     * GET /v1/payments/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentEntry> findById(@PathVariable String paymentId) {
        log.debug("GET /v1/payments/{}", paymentId);

        PaymentEntry entry = paymentService.findById(paymentId);
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(entry);
    }

    /**
     * Gets payment by booking ID.
     * GET /v1/payments/booking/{bookingId}
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<PaymentEntry> findByBooking(@PathVariable String bookingId) {
        log.debug("GET /v1/payments/booking/{}", bookingId);

        PaymentEntry entry = paymentService.findByBookingId(bookingId);
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(entry);
    }
}
