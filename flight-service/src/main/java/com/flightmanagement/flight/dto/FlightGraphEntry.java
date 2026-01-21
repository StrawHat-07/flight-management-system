package com.flightmanagement.flight.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FlightGraphEntry {

    Map<String, List<FlightEntry>> graph;
    List<String> locations;
}
