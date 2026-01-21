package com.flightmanagement.flight.dto;

import com.flightmanagement.flight.constants.FlightConstants;
import com.flightmanagement.flight.constants.ValidationMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchRequest {

    @NotBlank(message = ValidationMessages.SOURCE_REQUIRED)
    String source;

    @NotBlank(message = ValidationMessages.DESTINATION_REQUIRED)
    String destination;

    @NotNull(message = ValidationMessages.DATE_REQUIRED)
    LocalDate date;

    @Min(value = FlightConstants.MIN_SEATS, message = ValidationMessages.SEATS_MIN)
    @Builder.Default
    Integer seats = FlightConstants.DEFAULT_SEATS_PER_REQUEST;

    @Builder.Default
    Integer maxHops = FlightConstants.DEFAULT_MAX_HOPS;

    @Builder.Default
    Integer page = FlightConstants.DEFAULT_PAGE_NUMBER;

    @Builder.Default
    Integer size = FlightConstants.DEFAULT_PAGE_SIZE;

    String sortBy;

    String sortDirection;
}
