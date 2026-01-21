package com.flightmanagement.booking.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "booking_flights")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingFlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "booking_id", nullable = false, length = 36)
    String bookingId;

    @Column(name = "flight_id", nullable = false, length = 36)
    String flightId;

    @Column(name = "leg_order", nullable = false)
    Integer legOrder;
}
