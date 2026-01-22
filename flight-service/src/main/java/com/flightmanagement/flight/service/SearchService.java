package com.flightmanagement.flight.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightmanagement.flight.dto.ComputedFlightEntry;
import com.flightmanagement.flight.dto.PageResponse;
import com.flightmanagement.flight.dto.SearchRequest;
import com.flightmanagement.flight.dto.SearchResponse;
import com.flightmanagement.flight.mapper.SearchMapper;
import com.flightmanagement.flight.model.Flight;
import com.flightmanagement.flight.util.IdGenerator;
import com.flightmanagement.flight.util.PaginationUtils;
import com.flightmanagement.flight.util.SortingUtils;
import com.flightmanagement.flight.util.StringUtils;
import com.flightmanagement.flight.validator.SearchValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Flight search service.
 */
@Service
@Slf4j
public class SearchService {

    private final FlightGraphService graphService;
    private final CacheService cacheService;
    private final RouteFinderService routeFinderService;
    private final ObjectMapper objectMapper;

    private final int maxHops;
    private final int cacheTtlHours;

    public SearchService(
            FlightGraphService graphService,
            CacheService cacheService,
            RouteFinderService routeFinderService,
            ObjectMapper objectMapper,
            @Value("${search.max-hops:3}") int maxHops,
            @Value("${search.cache-ttl-hours:24}") int cacheTtlHours) {
        this.graphService = graphService;
        this.cacheService = cacheService;
        this.routeFinderService = routeFinderService;
        this.objectMapper = objectMapper;
        this.maxHops = maxHops;
        this.cacheTtlHours = cacheTtlHours;
    }

    /**
     * Searches for flights with specified criteria.
     * 
     * Flow:
     * 1. Validate request
     * 2. Try Redis cache (fast path)
     * 3. If cache miss, compute in real-time (fallback)
     * 4. Filter by seat availability
     * 5. Sort and paginate
     * 
     * @param request Search criteria
     * @return Paginated search results
     */
    public PageResponse<SearchResponse> searchFlights(SearchRequest request) {
        SearchValidator.validateSearchRequest(request);

        String source = StringUtils.normalizeLocation(request.getSource());
        String destination = StringUtils.normalizeLocation(request.getDestination());
        LocalDate date = request.getDate();
        int seats = request.getSeats() != null ? request.getSeats() : 1;
        int hopsLimit = Math.min(request.getMaxHops() != null ? request.getMaxHops() : maxHops, maxHops);

        log.info("Search: {} -> {} on {}, seats={}, maxHops={}", source, destination, date, seats, hopsLimit);

        List<SearchResponse> allResults = new ArrayList<>();

        for (int hops = 1; hops <= hopsLimit; hops++) {
            // Try cache first, compute on miss
            List<ComputedFlightEntry> routes = findRoutes(source, destination, date, hops);

            for (ComputedFlightEntry route : routes) {
                int available = cacheService.getMinSeatsAcrossFlights(route.getFlightIds());
                if (available >= seats) {
                    allResults.add(SearchMapper.toSearchResponse(route, hops, available));
                }
            }
        }

        SortingUtils.sortSearchResults(allResults, request.getSortBy(), request.getSortDirection());

        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;

        PageResponse<SearchResponse> pageResponse = PaginationUtils.paginate(allResults, page, size);

        log.info("Search complete: {} total results, returning page {} of {}",
                allResults.size(), page, pageResponse.getTotalPages());

        return pageResponse;
    }

    /**
     * Gets computed flight details by identifier.
     * Used for displaying complete route information.
     */
    public ComputedFlightEntry getComputedFlight(String flightIdentifier) {
        SearchValidator.validateFlightIdentifier(flightIdentifier);

        Map<String, List<Flight>> graph = graphService.getGraphSnapshot();

        if (IdGenerator.isComputedFlightId(flightIdentifier)) {
            return reconstructMultiHopFlight(flightIdentifier, graph);
        }
        return findDirectFlight(flightIdentifier, graph);
    }

    /**
     * Finds routes from cache or computes on miss.
     * 
     * Flow:
     * 1. Try Redis (fast path)
     * 2. If miss, get graph and compute (fallback)
     * 3. Cache result for future requests
     * 
     * @return List of computed routes (empty if none found)
     */
    private List<ComputedFlightEntry> findRoutes(String source, String destination, LocalDate date, int hops) {
        String dateStr = date.toString();

        // Try Redis cache first
        Optional<Object> cached = cacheService.getCachedRoutes(dateStr, hops, source, destination);
        if (cached.isPresent()) {
            try {
                List<ComputedFlightEntry> routes = objectMapper.convertValue(
                        cached.get(), new TypeReference<>() {
                        });
                if (!routes.isEmpty()) {
                    log.debug("Cache hit: {}->{} on {} ({} hops)", source, destination, date, hops);
                    return routes;
                }
            } catch (Exception e) {
                log.warn("Failed to deserialize cached routes: {}", e.getMessage());
            }
        }

        // Cache miss: compute in real-time using shared RouteFinderService
        log.debug("Cache miss: date={}, hops={}, {}->{}, computing...", date, hops, source, destination);

        Map<String, List<Flight>> graph = graphService.getGraphSnapshot();
        List<ComputedFlightEntry> computed = routeFinderService.findRoutes(source, destination, date, hops, graph);

        if (!computed.isEmpty()) {
            cacheService.cacheRoutes(dateStr, hops, source, destination, computed, cacheTtlHours);
            log.debug("Computed and cached {} routes: {}->{} on {} ({} hops)",
                    computed.size(), source, destination, date, hops);
        }

        return computed;
    }

    private ComputedFlightEntry findDirectFlight(String flightId, Map<String, List<Flight>> graph) {
        // Search in graph first (in-memory, fast)
        for (List<Flight> flights : graph.values()) {
            for (Flight f : flights) {
                if (f.getFlightId().equals(flightId)) {
                    return SearchMapper.toComputedEntry(f);
                }
            }
        }

        // Not found in graph (shouldn't happen for active flights)
        log.warn("Flight {} not found in graph", flightId);
        return null;
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
                log.warn("Cannot reconstruct computed flight, missing flight in graph: {}", flightId);
                return null;
            }
            flights.add(flight);
        }

        ComputedFlightEntry entry = SearchMapper.toComputedEntry(flights);
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

}
