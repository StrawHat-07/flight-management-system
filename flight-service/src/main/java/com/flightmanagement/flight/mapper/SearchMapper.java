package com.flightmanagement.flight.mapper;

import com.flightmanagement.flight.dto.ComputedFlightEntry;
import com.flightmanagement.flight.dto.SearchResponse;
import com.flightmanagement.flight.model.Flight;
import com.flightmanagement.flight.util.IdGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class SearchMapper {

    private SearchMapper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static SearchResponse toSearchResponse(ComputedFlightEntry entry, int hops, int availableSeats) {
        if (entry == null) {
            return null;
        }

        return SearchResponse.builder()
                .flightIdentifier(entry.getComputedFlightId())
                .type(hops == 1 ? "DIRECT" : "COMPUTED")
                .price(entry.getTotalPrice())
                .durationMinutes(entry.getTotalDurationMinutes())
                .availableSeats(availableSeats)
                .departureTime(entry.getDepartureTime())
                .arrivalTime(entry.getArrivalTime())
                .source(entry.getSource())
                .destination(entry.getDestination())
                .hops(hops)
                .legs(entry.getLegs())
                .build();
    }

    public static ComputedFlightEntry toComputedEntry(List<Flight> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        Flight first = path.get(0);
        Flight last = path.get(path.size() - 1);

        List<String> flightIds = new ArrayList<>(path.size());
        BigDecimal totalPrice = BigDecimal.ZERO;
        int minSeats = Integer.MAX_VALUE;
        List<ComputedFlightEntry.FlightLeg> legs = new ArrayList<>(path.size());

        for (Flight f : path) {
            flightIds.add(f.getFlightId());
            totalPrice = totalPrice.add(f.getPrice());
            minSeats = Math.min(minSeats, f.getAvailableSeats());
            legs.add(toFlightLeg(f));
        }

        long duration = ChronoUnit.MINUTES.between(first.getDepartureTime(), last.getArrivalTime());

        String id = path.size() == 1
                ? first.getFlightId()
                : IdGenerator.generateComputedFlightId(first.getSource(), last.getDestination(), flightIds);

        return ComputedFlightEntry.builder()
                .computedFlightId(id)
                .flightIds(flightIds)
                .source(first.getSource())
                .destination(last.getDestination())
                .totalPrice(totalPrice)
                .totalDurationMinutes(duration)
                .availableSeats(minSeats == Integer.MAX_VALUE ? 0 : minSeats)
                .numberOfHops(path.size())
                .departureTime(first.getDepartureTime())
                .arrivalTime(last.getArrivalTime())
                .legs(legs)
                .build();
    }

    public static ComputedFlightEntry toComputedEntry(Flight flight) {
        if (flight == null) {
            return null;
        }

        return ComputedFlightEntry.builder()
                .computedFlightId(flight.getFlightId())
                .flightIds(List.of(flight.getFlightId()))
                .source(flight.getSource())
                .destination(flight.getDestination())
                .totalPrice(flight.getPrice())
                .totalDurationMinutes(Duration.between(flight.getDepartureTime(), flight.getArrivalTime()).toMinutes())
                .availableSeats(flight.getAvailableSeats())
                .numberOfHops(1)
                .departureTime(flight.getDepartureTime())
                .arrivalTime(flight.getArrivalTime())
                .legs(List.of(toFlightLeg(flight)))
                .build();
    }

    public static ComputedFlightEntry.FlightLeg toFlightLeg(Flight flight) {
        if (flight == null) {
            return null;
        }

        return ComputedFlightEntry.FlightLeg.builder()
                .flightId(flight.getFlightId())
                .source(flight.getSource())
                .destination(flight.getDestination())
                .departureTime(flight.getDepartureTime())
                .arrivalTime(flight.getArrivalTime())
                .price(flight.getPrice())
                .availableSeats(flight.getAvailableSeats())
                .build();
    }
}
