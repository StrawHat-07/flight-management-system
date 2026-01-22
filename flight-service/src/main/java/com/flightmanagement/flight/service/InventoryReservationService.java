package com.flightmanagement.flight.service;

import com.flightmanagement.flight.constants.FlightConstants;
import com.flightmanagement.flight.dto.InventoryReservationRequest;
import com.flightmanagement.flight.dto.InventoryReservationResponse;
import com.flightmanagement.flight.model.SeatReservation;
import com.flightmanagement.flight.repository.SeatReservationRepository;
import com.flightmanagement.flight.service.lock.LockOperations;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DB-First Inventory Reservation Service.
 * 
 * Architecture:
 * - DB is the SINGLE source of truth for both inventory AND reservations
 * - seat_reservations table tracks active holds (with expiry timestamp)
 * - Soft delete used for audit trail (deleted_at timestamp)
 * - Background job cleans expired reservations and restores inventory
 * - Redis is just a cache, synced from DB
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryReservationService {

    private final InventoryService inventoryService;
    private final SeatReservationRepository reservationRepository;
    private final DistributedLockService lockService;
    private final MeterRegistry meterRegistry;

    @Value("${flight.inventory.reservation-ttl-minutes:" + FlightConstants.DEFAULT_SEAT_BLOCK_TTL_MINUTES + "}")
    private int defaultReservationTtlMinutes;


    public InventoryReservationResponse reserveSeats(InventoryReservationRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Reserving {} seats for booking {}: flights={}",
                request.getSeats(), request.getBookingId(), request.getFlightIds());

        try {
            return lockService.executeWithMultiLock(request.getFlightIds(), () -> doReserveSeats(request));
        } catch (LockOperations.LockAcquisitionException e) {
            meterRegistry.counter("inventory.reserve.total", "result", "lock_failed").increment();
            log.warn("Failed to acquire lock for reservation: bookingId={}", request.getBookingId());
            return InventoryReservationResponse.failure("LOCK_FAILED", "Could not acquire lock, please retry");
        } catch (Exception e) {
            meterRegistry.counter("inventory.reserve.total", "result", "error").increment();
            log.error("Error reserving seats: bookingId={}, error={}", request.getBookingId(), e.getMessage(), e);
            return InventoryReservationResponse.failure("INTERNAL_ERROR", e.getMessage());
        } finally {
            sample.stop(Timer.builder("inventory.reserve.duration").register(meterRegistry));
        }
    }

    @Transactional
    protected InventoryReservationResponse doReserveSeats(InventoryReservationRequest request) {
        int ttlMinutes = request.getTtlMinutes() != null ? request.getTtlMinutes() : defaultReservationTtlMinutes;
        int seats = request.getSeats();
        List<String> flightIds = request.getFlightIds();
        String bookingId = request.getBookingId();

        // Check for existing reservation (idempotency)
        if (reservationRepository.existsByBookingId(bookingId)) {
            log.info("Reservation already exists for bookingId={}", bookingId);
            List<SeatReservation> existing = reservationRepository.findByBookingId(bookingId);
            LocalDateTime expiresAt = existing.isEmpty() ? LocalDateTime.now() : existing.get(0).getExpiresAt();
            return InventoryReservationResponse.success(bookingId, expiresAt);
        }

        // Decrement DB inventory for all flights
        for (String flightId : flightIds) {
            boolean decremented = inventoryService.decrementSeatsDbOnly(flightId, seats);
            if (!decremented) {
                // Rollback any decrements already done
                rollbackDecrements(flightIds, flightIds.indexOf(flightId), seats);
                log.warn("Insufficient seats: flightId={}, requested={}", flightId, seats);
                meterRegistry.counter("inventory.reserve.total", "result", "no_seats").increment();
                return InventoryReservationResponse.failure("NO_SEATS_AVAILABLE",
                        "Not enough seats available on flight " + flightId);
            }
        }

        // Record reservations in DB
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes);
        for (String flightId : flightIds) {
            reservationRepository.save(SeatReservation.builder()
                    .bookingId(bookingId)
                    .flightId(flightId)
                    .seats(seats)
                    .expiresAt(expiresAt)
                    .build());
        }

        // Sync Redis from DB
        for (String flightId : flightIds) {
            inventoryService.syncCacheFromDb(flightId);
        }

        meterRegistry.counter("inventory.reserve.total", "result", "success").increment();
        log.info("Reservation created: bookingId={}, expiresAt={}", bookingId, expiresAt);
        return InventoryReservationResponse.success(bookingId, expiresAt);
    }

    /**
     * CONFIRM Phase: Convert reservation to permanent booking.
     * Seats already decremented, just soft delete the reservation record.
     */
    @Transactional
    public boolean confirmReservation(String bookingId, List<String> flightIds, int seats) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Confirming reservation: bookingId={}", bookingId);

        try {
            return lockService.executeWithMultiLock(flightIds, () -> doConfirmReservation(bookingId));
        } catch (LockOperations.LockAcquisitionException e) {
            meterRegistry.counter("inventory.confirm.total", "result", "lock_failed").increment();
            log.warn("Failed to acquire lock for confirmation: bookingId={}", bookingId);
            return false;
        } catch (Exception e) {
            meterRegistry.counter("inventory.confirm.total", "result", "error").increment();
            log.error("Error confirming reservation: bookingId={}, error={}", bookingId, e.getMessage(), e);
            return false;
        } finally {
            sample.stop(Timer.builder("inventory.confirm.duration").register(meterRegistry));
        }
    }

    @Transactional
    protected boolean doConfirmReservation(String bookingId) {
        List<SeatReservation> reservations = reservationRepository.findByBookingId(bookingId);

        if (reservations.isEmpty()) {
            log.warn("Reservation not found or expired: bookingId={}", bookingId);
            meterRegistry.counter("inventory.confirm.total", "result", "expired").increment();
            return false;
        }

        // Check if expired
        if (reservations.get(0).isExpired()) {
            log.warn("Reservation expired: bookingId={}", bookingId);
            meterRegistry.counter("inventory.confirm.total", "result", "expired").increment();
            return false;
        }

        // Soft delete reservation (seats stay decremented - now permanent)
        reservationRepository.softDeleteByBookingId(bookingId, LocalDateTime.now());

        meterRegistry.counter("inventory.confirm.total", "result", "success").increment();
        log.info("Reservation confirmed: bookingId={}", bookingId);
        return true;
    }

    /**
     * RELEASE Phase: Cancel reservation and restore inventory.
     */
    @Transactional
    public boolean releaseReservation(String bookingId, List<String> flightIds) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Releasing reservation: bookingId={}", bookingId);

        try {
            return lockService.executeWithMultiLock(flightIds, () -> doReleaseReservation(bookingId));
        } catch (LockOperations.LockAcquisitionException e) {
            meterRegistry.counter("inventory.release.total", "result", "lock_failed").increment();
            log.warn("Failed to acquire lock for release: bookingId={}", bookingId);
            return false;
        } catch (Exception e) {
            meterRegistry.counter("inventory.release.total", "result", "error").increment();
            log.error("Error releasing reservation: bookingId={}, error={}", bookingId, e.getMessage(), e);
            return false;
        } finally {
            sample.stop(Timer.builder("inventory.release.duration").register(meterRegistry));
        }
    }

    @Transactional
    protected boolean doReleaseReservation(String bookingId) {
        List<SeatReservation> reservations = reservationRepository.findByBookingId(bookingId);

        if (reservations.isEmpty()) {
            log.debug("Reservation not found (already released/expired): bookingId={}", bookingId);
            meterRegistry.counter("inventory.release.total", "result", "not_found").increment();
            return false;
        }

        for (SeatReservation reservation : reservations) {
            inventoryService.incrementSeatsDbOnly(reservation.getFlightId(), reservation.getSeats());
            inventoryService.syncCacheFromDb(reservation.getFlightId());
        }

        reservationRepository.softDeleteByBookingId(bookingId, LocalDateTime.now());

        meterRegistry.counter("inventory.release.total", "result", "success").increment();
        log.info("Reservation released and {} seats restored: bookingId={}",
                reservations.get(0).getSeats(), bookingId);
        return true;
    }

    /**
     * Background job to clean up expired reservations and restore inventory.
     * Runs every 10 seconds.
     */
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void cleanupExpiredReservations() {
        List<SeatReservation> expired = reservationRepository.findExpiredReservations(LocalDateTime.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("Found {} expired reservations to clean up", expired.size());

        LocalDateTime now = LocalDateTime.now();
        for (SeatReservation reservation : expired) {
            try {
                inventoryService.incrementSeatsDbOnly(reservation.getFlightId(), reservation.getSeats());
                inventoryService.syncCacheFromDb(reservation.getFlightId());

                reservation.softDelete();
                reservationRepository.save(reservation);

                meterRegistry.counter("inventory.expiry.total", "result", "success").increment();
                log.info("Expired reservation cleaned up: bookingId={}, flightId={}, seats={}",
                        reservation.getBookingId(), reservation.getFlightId(), reservation.getSeats());
            } catch (Exception e) {
                meterRegistry.counter("inventory.expiry.total", "result", "error").increment();
                log.error("Failed to clean up expired reservation: bookingId={}, error={}",
                        reservation.getBookingId(), e.getMessage());
            }
        }
    }


    private void rollbackDecrements(List<String> flightIds, int failedIndex, int seats) {
        for (int i = 0; i < failedIndex; i++) {
            inventoryService.incrementSeatsDbOnly(flightIds.get(i), seats);
        }
    }
}
