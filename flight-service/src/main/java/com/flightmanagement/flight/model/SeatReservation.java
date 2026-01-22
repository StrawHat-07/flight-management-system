package com.flightmanagement.flight.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat_reservations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SeatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "booking_id", nullable = false)
    String bookingId;

    @Column(name = "flight_id", nullable = false)
    String flightId;

    @Column(nullable = false)
    Integer seats;

    @Column(name = "expires_at", nullable = false)
    LocalDateTime expiresAt;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
