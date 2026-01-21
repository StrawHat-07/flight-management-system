package com.flightmanagement.flight.dto;

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

    @NotBlank(message = "Source is required")
    String source;

    @NotBlank(message = "Destination is required")
    String destination;

    @NotNull(message = "Date is required")
    LocalDate date;

    @Min(value = 1, message = "At least 1 seat required")
    @Builder.Default
    Integer seats = 1;

    @Builder.Default
    Integer maxHops = 3;

    @Builder.Default
    Integer page = 0;

    @Builder.Default
    Integer size = 20;

    String sortBy;

    String sortDirection;
}
