package com.flightmanagement.flight.service;

import com.flightmanagement.flight.dto.ComputedFlightEntry;
import com.flightmanagement.flight.mapper.SearchMapper;
import com.flightmanagement.flight.model.Flight;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core route finding algorithm using DFS.
 *
 */
@Service
@Slf4j
public class RouteFinderService {

    private final int minConnectionMinutes;

    public RouteFinderService(
            @Value("${search.min-connection-time-minutes:60}") int minConnectionMinutes) {
        this.minConnectionMinutes = minConnectionMinutes;
    }

    /**
     * Finds all routes from source to destination with exact hop count.
     *
     * @param source Starting location
     * @param destination Target location
     * @param date Travel date
     * @param exactHops Exact number of flights in route
     * @param flightGraph Adjacency list (source -> outbound flights)
     * @return List of computed routes
     */
    public List<ComputedFlightEntry> findRoutes(String source, String destination, LocalDate date,
                                                 int exactHops, Map<String, List<Flight>> flightGraph) {
        List<List<Flight>> paths = new ArrayList<>();
        List<Flight> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        findPaths(source, destination, date, exactHops, currentPath, visited, paths, null, flightGraph);

        List<ComputedFlightEntry> results = new ArrayList<>(paths.size());
        for (List<Flight> path : paths) {
            ComputedFlightEntry entry = SearchMapper.toComputedEntry(path);
            if (entry != null) {
                results.add(entry);
            }
        }
        return results;
    }

    /**
     * DFS algorithm to find all paths with exact hop count.
     */
    private void findPaths(String current, String destination, LocalDate date, int remainingHops,
                           List<Flight> path, Set<String> visited, List<List<Flight>> results,
                           LocalDateTime minDeparture, Map<String, List<Flight>> graph) {

        if (remainingHops == 0) {
            if (current.equals(destination)) {
                results.add(new ArrayList<>(path));
            }
            return;
        }

        if (visited.contains(current)) {
            return;
        }
        visited.add(current);

        List<Flight> outbound = graph.getOrDefault(current, Collections.emptyList());

        for (Flight flight : outbound) {
            if (!flight.getDepartureTime().toLocalDate().equals(date)) {
                continue;
            }

            if (minDeparture != null) {
                long gap = ChronoUnit.MINUTES.between(minDeparture, flight.getDepartureTime());
                if (gap < minConnectionMinutes) {
                    continue;
                }
            }

            path.add(flight);
            findPaths(flight.getDestination(), destination, date, remainingHops - 1, path, visited,
                    results, flight.getArrivalTime(), graph);
            path.remove(path.size() - 1);
        }

        visited.remove(current);
    }
}
