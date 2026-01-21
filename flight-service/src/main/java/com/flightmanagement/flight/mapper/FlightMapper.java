package com.flightmanagement.flight.mapper;

import com.flightmanagement.flight.dto.FlightEntry;
import com.flightmanagement.flight.enums.FlightStatus;
import com.flightmanagement.flight.model.Flight;
import com.flightmanagement.flight.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class FlightMapper {

    private FlightMapper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static FlightEntry toEntry(Flight flight) {
        if (flight == null) {
            return null;
        }

        return FlightEntry.builder()
                .id(flight.getId())
                .flightId(flight.getFlightId())
                .source(flight.getSource())
                .destination(flight.getDestination())
                .departureTime(flight.getDepartureTime())
                .arrivalTime(flight.getArrivalTime())
                .totalSeats(flight.getTotalSeats())
                .availableSeats(flight.getAvailableSeats())
                .price(flight.getPrice())
                .status(flight.getStatus() != null ? flight.getStatus().name() : null)
                .createdAt(flight.getCreatedAt())
                .updatedAt(flight.getUpdatedAt())
                .build();
    }

    public static List<FlightEntry> toEntryList(List<Flight> flights) {
        if (flights == null || flights.isEmpty()) {
            return new ArrayList<>();
        }

        List<FlightEntry> result = new ArrayList<>(flights.size());
        for (Flight flight : flights) {
            result.add(toEntry(flight));
        }
        return result;
    }

    public static Flight toEntity(String flightId, FlightEntry entry) {
        if (entry == null) {
            return null;
        }

        return Flight.builder()
                .flightId(flightId)
                .source(StringUtils.normalizeLocation(entry.getSource()))
                .destination(StringUtils.normalizeLocation(entry.getDestination()))
                .departureTime(entry.getDepartureTime())
                .arrivalTime(entry.getArrivalTime())
                .totalSeats(entry.getTotalSeats())
                .availableSeats(entry.getTotalSeats())
                .price(entry.getPrice())
                .status(FlightStatus.ACTIVE)
                .build();
    }

    public static void updateEntity(Flight flight, FlightEntry entry) {
        if (flight == null || entry == null) {
            return;
        }

        flight.setSource(StringUtils.normalizeLocation(entry.getSource()));
        flight.setDestination(StringUtils.normalizeLocation(entry.getDestination()));
        flight.setDepartureTime(entry.getDepartureTime());
        flight.setArrivalTime(entry.getArrivalTime());
        flight.setTotalSeats(entry.getTotalSeats());
        flight.setPrice(entry.getPrice());
    }
}
