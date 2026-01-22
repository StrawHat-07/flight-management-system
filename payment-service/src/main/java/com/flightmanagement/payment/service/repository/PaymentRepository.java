package com.flightmanagement.payment.service.repository;

import com.flightmanagement.payment.dto.PaymentEntry;

import java.util.Optional;

/**
 * Interface for payment storage operations.
 * Follows Interface Segregation - only storage concerns.
 * In production, this would be backed by a database.
 */
public interface PaymentRepository {

    PaymentEntry save(PaymentEntry entry);

    Optional<PaymentEntry> findById(String paymentId);

    Optional<PaymentEntry> findByBookingId(String bookingId);

    boolean existsByBookingId(String bookingId);
}
