package com.flightmanagement.booking.model;

import com.flightmanagement.booking.enums.BookingStatus;
import com.flightmanagement.booking.enums.FlightType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Booking {

    @Id
    @Column(name = "booking_id", length = 36)
    String bookingId;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "flight_type", nullable = false)
    FlightType flightType;

    @Column(name = "flight_identifier", nullable = false, length = 255)
    String flightIdentifier;

    @Column(name = "no_of_seats", nullable = false)
    Integer noOfSeats;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    BookingStatus status = BookingStatus.PENDING;

    @Column(name = "idempotency_key", unique = true, length = 100)
    String idempotencyKey;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
