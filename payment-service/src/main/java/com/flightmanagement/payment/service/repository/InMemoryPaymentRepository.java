package com.flightmanagement.payment.service.repository;

import com.flightmanagement.payment.dto.PaymentEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of PaymentRepository.
 * Suitable for mock/demo purposes. In production, use JPA/DB implementation.
 */
@Repository
@Slf4j
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<String, PaymentEntry> paymentsById = new ConcurrentHashMap<>();
    private final Map<String, PaymentEntry> paymentsByBookingId = new ConcurrentHashMap<>();

    @Override
    public PaymentEntry save(PaymentEntry entry) {
        log.debug("Saving payment: id={}, bookingId={}", entry.getPaymentId(), entry.getBookingId());
        paymentsById.put(entry.getPaymentId(), entry);
        paymentsByBookingId.put(entry.getBookingId(), entry);
        return entry;
    }

    @Override
    public Optional<PaymentEntry> findById(String paymentId) {
        return Optional.ofNullable(paymentsById.get(paymentId));
    }

    @Override
    public Optional<PaymentEntry> findByBookingId(String bookingId) {
        return Optional.ofNullable(paymentsByBookingId.get(bookingId));
    }

    @Override
    public boolean existsByBookingId(String bookingId) {
        return paymentsByBookingId.containsKey(bookingId);
    }
}
