package com.flightmanagement.booking.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingEntry {

    String bookingId;
    String userId;
    String flightType;
    String flightIdentifier;
    Integer noOfSeats;
    BigDecimal totalPrice;
    String status;
    List<String> flightIds;
    LocalDateTime createdAt;
}
