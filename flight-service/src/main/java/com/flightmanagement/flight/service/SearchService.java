package com.flightmanagement.flight.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightmanagement.flight.dto.ComputedFlightEntry;
import com.flightmanagement.flight.dto.PageResponse;
import com.flightmanagement.flight.dto.SearchRequest;
import com.flightmanagement.flight.dto.SearchResponse;
import com.flightmanagement.flight.exception.FlightValidationException;
import com.flightmanagement.flight.model.Flight;
import com.flightmanagement.flight.repository.FlightRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
public class SearchService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "price", "durationMinutes", "departureTime", "hops");

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final FlightRepository flightRepository;

    private final int maxHops;
    private final int cacheTtlHours;
    private final int minConnectionMinutes;

    public SearchService(
            CacheService cacheService,
            ObjectMapper objectMapper,
            FlightRepository flightRepository,
            @Value("${search.max-hops:3}") int maxHops,
            @Value("${search.cache-ttl-hours:24}") int cacheTtlHours,
            @Value("${search.min-connection-time-minutes:60}") int minConnectionMinutes) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.flightRepository = flightRepository;
        this.maxHops = maxHops;
        this.cacheTtlHours = cacheTtlHours;
        this.minConnectionMinutes = minConnectionMinutes;
    }

    public PageResponse<SearchResponse> searchFlights(SearchRequest request, Map<String, List<Flight>> flightGraph) {
        validateRequest(request);

        String source = normalize(request.getSource());
        String destination = normalize(request.getDestination());
        LocalDate date = request.getDate();
        int seats = request.getSeats() != null ? request.getSeats() : 1;
        int hopsLimit = Math.min(request.getMaxHops() != null ? request.getMaxHops() : maxHops, maxHops);

        log.info("Search: {} -> {} on {}, seats={}, maxHops={}", source, destination, date, seats, hopsLimit);

        List<SearchResponse> allResults = new ArrayList<>();

        for (int hops = 1; hops <= hopsLimit; hops++) {
            List<ComputedFlightEntry> routes = findRoutes(source, destination, date, hops, flightGraph);

            for (ComputedFlightEntry route : routes) {
                int available = cacheService.getMinSeatsAcrossFlights(route.getFlightIds());
                if (available >= seats) {
                    allResults.add(toResponse(route, hops, available));
                }
            }
        }

        sortResults(allResults, request.getSortBy(), request.getSortDirection());

        int page = Math.max(0, request.getPage() != null ? request.getPage() : 0);
        int size = Math.min(Math.max(1, request.getSize() != null ? request.getSize() : 20), MAX_PAGE_SIZE);

        PageResponse<SearchResponse> pageResponse = paginateResults(allResults, page, size);

        log.info("Search complete: {} total results, returning page {} of {}",
                allResults.size(), page, pageResponse.getTotalPages());

        return pageResponse;
    }

    public ComputedFlightEntry getComputedFlight(String flightIdentifier, Map<String, List<Flight>> flightGraph) {
        if (!hasText(flightIdentifier)) {
            throw new FlightValidationException("Flight identifier is required");
        }

        if (flightIdentifier.startsWith("CF_")) {
            return reconstructMultiHopFlight(flightIdentifier, flightGraph);
        }
        return findDirectFlight(flightIdentifier, flightGraph);
    }

    public void runPrecomputation(Map<String, List<Flight>> flightGraph) {
        if (flightGraph == null || flightGraph.isEmpty()) {
            log.warn("Empty flight graph, skipping precomputation");
            return;
        }

        log.info("Starting precomputation...");
        long start = System.currentTimeMillis();

        Set<String> locations = collectLocations(flightGraph);
        int routeCount = 0;
        int daysAhead = 7;

        for (int day = 0; day < daysAhead; day++) {
            LocalDate date = LocalDate.now().plusDays(day);
            routeCount += precomputeForDate(date, locations, flightGraph);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Precomputation complete: {} routes in {}ms", routeCount, elapsed);
    }

    private void sortResults(List<SearchResponse> results, String sortBy, String sortDirection) {
        String field = hasText(sortBy) ? sortBy : "price";

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            log.warn("Invalid sort field '{}', defaulting to 'price'", field);
            field = "price";
        }

        boolean ascending = !"desc".equalsIgnoreCase(sortDirection);

        Comparator<SearchResponse> comparator = createComparator(field);
        if (!ascending) {
            comparator = comparator.reversed();
        }

        results.sort(comparator);
    }

    private Comparator<SearchResponse> createComparator(String field) {
        return switch (field) {
            case "price" -> Comparator.comparing(SearchResponse::getPrice,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "durationMinutes" -> Comparator.comparing(SearchResponse::getDurationMinutes,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "departureTime" -> Comparator.comparing(SearchResponse::getDepartureTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "hops" -> Comparator.comparing(SearchResponse::getHops,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(SearchResponse::getPrice,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private PageResponse<SearchResponse> paginateResults(List<SearchResponse> allResults, int page, int size) {
        int totalElements = allResults.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<SearchResponse> pageContent = (fromIndex < totalElements)
                ? allResults.subList(fromIndex, toIndex)
                : Collections.emptyList();

        return PageResponse.<SearchResponse>builder()
                .content(pageContent)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    private List<ComputedFlightEntry> findRoutes(String source, String destination, LocalDate date,
            int hops, Map<String, List<Flight>> flightGraph) {
        String dateStr = date.toString();

        Optional<Object> cached = cacheService.getCachedRoutes(dateStr, hops, source, destination);
        if (cached.isPresent()) {
            try {
                List<ComputedFlightEntry> routes = objectMapper.convertValue(
                        cached.get(), new TypeReference<>() {
                        });
                if (!routes.isEmpty()) {
                    return routes;
                }
            } catch (Exception e) {
                log.warn("Failed to deserialize cached routes: {}", e.getMessage());
            }
        }

        log.debug("Cache miss: date={}, hops={}, {}->{}", date, hops, source, destination);
        List<ComputedFlightEntry> computed = computeRoutes(source, destination, date, hops, flightGraph);

        if (!computed.isEmpty()) {
            cacheService.cacheRoutes(dateStr, hops, source, destination, computed, cacheTtlHours);
        }
        return computed;
    }

    private List<ComputedFlightEntry> computeRoutes(String source, String destination, LocalDate date,
            int exactHops, Map<String, List<Flight>> flightGraph) {
        List<List<Flight>> paths = new ArrayList<>();
        List<Flight> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        findPaths(source, destination, date, exactHops, currentPath, visited, paths, null, flightGraph);

        List<ComputedFlightEntry> results = new ArrayList<>(paths.size());
        for (List<Flight> path : paths) {
            ComputedFlightEntry entry = buildComputedEntry(path);
            if (entry != null) {
                results.add(entry);
            }
        }
        return results;
    }

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

    private ComputedFlightEntry buildComputedEntry(List<Flight> path) {
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
            legs.add(buildLeg(f));
        }

        long duration = ChronoUnit.MINUTES.between(first.getDepartureTime(), last.getArrivalTime());

        String id = path.size() == 1
                ? first.getFlightId()
                : "CF_" + first.getSource() + "_" + last.getDestination() + "_" + String.join("_", flightIds);

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

    private ComputedFlightEntry.FlightLeg buildLeg(Flight flight) {
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

    private ComputedFlightEntry findDirectFlight(String flightId, Map<String, List<Flight>> graph) {
        for (List<Flight> flights : graph.values()) {
            for (Flight f : flights) {
                if (f.getFlightId().equals(flightId)) {
                    return flightToEntry(f);
                }
            }
        }

        return flightRepository.findByFlightId(flightId)
                .map(this::flightToEntry)
                .orElse(null);
    }

    private ComputedFlightEntry reconstructMultiHopFlight(String computedId, Map<String, List<Flight>> graph) {
        String[] parts = computedId.split("_");
        if (parts.length < 4) {
            log.warn("Invalid computed flight ID format: {}", computedId);
            return null;
        }

        List<Flight> flights = new ArrayList<>();
        for (int i = 3; i < parts.length; i++) {
            String flightId = parts[i];
            Flight flight = findFlightInGraph(flightId, graph);
            if (flight == null) {
                flight = flightRepository.findByFlightId(flightId).orElse(null);
            }
            if (flight == null) {
                log.warn("Cannot reconstruct computed flight, missing: {}", flightId);
                return null;
            }
            flights.add(flight);
        }

        ComputedFlightEntry entry = buildComputedEntry(flights);
        if (entry != null) {
            int realTimeSeats = cacheService.getMinSeatsAcrossFlights(entry.getFlightIds());
            entry.setAvailableSeats(realTimeSeats);
        }
        return entry;
    }

    private Flight findFlightInGraph(String flightId, Map<String, List<Flight>> graph) {
        for (List<Flight> flights : graph.values()) {
            for (Flight f : flights) {
                if (f.getFlightId().equals(flightId)) {
                    return f;
                }
            }
        }
        return null;
    }

    private ComputedFlightEntry flightToEntry(Flight f) {
        return ComputedFlightEntry.builder()
                .computedFlightId(f.getFlightId())
                .flightIds(List.of(f.getFlightId()))
                .source(f.getSource())
                .destination(f.getDestination())
                .totalPrice(f.getPrice())
                .totalDurationMinutes(Duration.between(f.getDepartureTime(), f.getArrivalTime()).toMinutes())
                .availableSeats(f.getAvailableSeats())
                .numberOfHops(1)
                .departureTime(f.getDepartureTime())
                .arrivalTime(f.getArrivalTime())
                .legs(List.of(buildLeg(f)))
                .build();
    }

    private Set<String> collectLocations(Map<String, List<Flight>> graph) {
        Set<String> locations = new HashSet<>(graph.keySet());
        for (List<Flight> flights : graph.values()) {
            for (Flight f : flights) {
                locations.add(f.getDestination());
            }
        }
        return locations;
    }

    private int precomputeForDate(LocalDate date, Set<String> locations, Map<String, List<Flight>> graph) {
        int count = 0;
        String dateStr = date.toString();

        for (String source : locations) {
            for (String destination : locations) {
                if (source.equals(destination)) {
                    continue;
                }
                for (int hops = 1; hops <= maxHops; hops++) {
                    List<ComputedFlightEntry> routes = computeRoutes(source, destination, date, hops, graph);
                    if (!routes.isEmpty()) {
                        cacheService.cacheRoutes(dateStr, hops, source, destination, routes, cacheTtlHours);
                        count += routes.size();
                    }
                }
            }
        }

        return count;
    }

    private void validateRequest(SearchRequest request) {
        if (request == null) {
            throw new FlightValidationException("Search request is required");
        }
        if (!hasText(request.getSource())) {
            throw new FlightValidationException("Source is required");
        }
        if (!hasText(request.getDestination())) {
            throw new FlightValidationException("Destination is required");
        }
        if (normalize(request.getSource()).equals(normalize(request.getDestination()))) {
            throw new FlightValidationException("Source and destination cannot be the same");
        }
        if (request.getDate() == null) {
            throw new FlightValidationException("Date is required");
        }
        if (request.getDate().isBefore(LocalDate.now())) {
            throw new FlightValidationException("Date cannot be in the past");
        }
        if (request.getSeats() != null && request.getSeats() < 1) {
            throw new FlightValidationException("Seats must be at least 1");
        }
    }

    private SearchResponse toResponse(ComputedFlightEntry entry, int hops, int availableSeats) {
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

    private String normalize(String s) {
        return s.trim().toUpperCase();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
