package com.flightmanagement.flight.repository;

import com.flightmanagement.flight.model.SeatReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    @Query("SELECT r FROM SeatReservation r WHERE r.bookingId = :bookingId AND r.deletedAt IS NULL")
    List<SeatReservation> findByBookingId(@Param("bookingId") String bookingId);

    @Query("SELECT r FROM SeatReservation r WHERE r.bookingId = :bookingId AND r.flightId = :flightId AND r.deletedAt IS NULL")
    Optional<SeatReservation> findByBookingIdAndFlightId(@Param("bookingId") String bookingId, @Param("flightId") String flightId);

    @Query("SELECT COUNT(r) > 0 FROM SeatReservation r WHERE r.bookingId = :bookingId AND r.deletedAt IS NULL")
    boolean existsByBookingId(@Param("bookingId") String bookingId);

    @Query("SELECT r FROM SeatReservation r WHERE r.expiresAt < :now AND r.deletedAt IS NULL")
    List<SeatReservation> findExpiredReservations(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE SeatReservation r SET r.deletedAt = :now WHERE r.bookingId = :bookingId AND r.deletedAt IS NULL")
    int softDeleteByBookingId(@Param("bookingId") String bookingId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE SeatReservation r SET r.deletedAt = :now WHERE r.expiresAt < :now AND r.deletedAt IS NULL")
    int softDeleteExpiredReservations(@Param("now") LocalDateTime now);
}
