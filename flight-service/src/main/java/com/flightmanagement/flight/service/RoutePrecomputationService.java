package com.flightmanagement.flight.service;

import com.flightmanagement.flight.dto.ComputedFlightEntry;
import com.flightmanagement.flight.event.GraphRebuildEvent;
import com.flightmanagement.flight.mapper.SearchMapper;
import com.flightmanagement.flight.model.Flight;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Precomputes multi-hop routes asynchronously and caches in Redis.
 * 
 * Single Responsibility: Route precomputation ONLY
 * - Runs DFS in background (never blocks user requests)
 * - @Scheduled: Every 6 hours
 * - Event-driven: When graph changes
 * - Publishes to Redis (no database storage)
 */
@Service
@Slf4j
public class RoutePrecomputationService {

    private final FlightGraphService graphService;
    private final CacheService cacheService;
    private final ExecutorService precomputationExecutor;

    private final int maxHops;
    private final int daysAhead;
    private final int cacheTtlHours;
    private final int minConnectionMinutes;
    private final boolean enabled;

    public RoutePrecomputationService(
            FlightGraphService graphService,
            CacheService cacheService,
            @Value("${route.precomputation.enabled:true}") boolean enabled,
            @Value("${route.precomputation.max-hops:3}") int maxHops,
            @Value("${route.precomputation.days-ahead:7}") int daysAhead,
            @Value("${route.precomputation.cache-ttl-hours:24}") int cacheTtlHours,
            @Value("${route.precomputation.thread-pool-size:10}") int threadPoolSize,
            @Value("${search.min-connection-time-minutes:60}") int minConnectionMinutes) {
        
        this.graphService = graphService;
        this.cacheService = cacheService;
        this.enabled = enabled;
        this.maxHops = maxHops;
        this.daysAhead = daysAhead;
        this.cacheTtlHours = cacheTtlHours;
        this.minConnectionMinutes = minConnectionMinutes;
        
        // Bounded thread pool: prevents system overload
        this.precomputationExecutor = Executors.newFixedThreadPool(threadPoolSize);
        
        log.info("RoutePrecomputationService initialized: enabled={}, maxHops={}, daysAhead={}, threads={}", 
                enabled, maxHops, daysAhead, threadPoolSize);
    }

    /**
     * Scheduled precomputation: runs every 6 hours.
     * Precomputes routes for next N days.
     */
    @Scheduled(cron = "${route.precomputation.cron:0 0 */6 * * *}")
    public void scheduledPrecomputation() {
        if (!enabled) {
            log.debug("Precomputation disabled, skipping scheduled run");
            return;
        }

        log.info("Starting scheduled route precomputation...");
        long start = System.currentTimeMillis();

        Map<String, List<Flight>> graph = graphService.getGraphSnapshot();
        Set<String> locations = graphService.getActiveLocations();

        if (graph.isEmpty() || locations.isEmpty()) {
            log.warn("Empty graph or locations, skipping precomputation");
            return;
        }

        int totalRoutes = precomputeAllRoutes(graph, locations);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Scheduled precomputation complete: {} routes computed in {}ms", totalRoutes, elapsed);
    }

    /**
     * Event-driven precomputation: when graph changes.
     * Runs asynchronously (doesn't block graph rebuild).
     */
    @Async
    @EventListener
    public void onGraphRebuild(GraphRebuildEvent event) {
        if (!enabled) {
            log.debug("Precomputation disabled, skipping event-driven run");
            return;
        }

        log.info("Graph rebuilt ({} locations, {} flights), triggering precomputation...", 
                event.getLocationCount(), event.getFlightCount());
        
        Map<String, List<Flight>> graph = graphService.getGraphSnapshot();
        Set<String> locations = graphService.getActiveLocations();

        precomputeAllRoutes(graph, locations);
    }

    /**
     * Precomputes routes for all location pairs and all dates.
     * 
     * @return total number of routes computed
     */
    private int precomputeAllRoutes(Map<String, List<Flight>> graph, Set<String> locations) {
        AtomicInteger routeCount = new AtomicInteger(0);
        AtomicInteger taskCount = new AtomicInteger(0);

        for (int day = 0; day < daysAhead; day++) {
            LocalDate date = LocalDate.now().plusDays(day);

            for (String source : locations) {
                for (String destination : locations) {
                    if (source.equals(destination)) {
                        continue;
                    }

                    taskCount.incrementAndGet();

                    // Submit to bounded thread pool (doesn't block)
                    precomputationExecutor.execute(() -> {
                        try {
                            int routes = precomputeRouteForPair(source, destination, date, graph);
                            routeCount.addAndGet(routes);
                        } catch (Exception e) {
                            log.error("Failed to precompute route {}->{} on {}: {}", 
                                    source, destination, date, e.getMessage());
                        }
                    });
                }
            }
        }

        log.info("Submitted {} precomputation tasks", taskCount.get());
        return routeCount.get();
    }

    /**
     * Precomputes routes for a specific source-destination pair on a date.
     * Tries all hop counts (1, 2, 3...).
     * 
     * @return number of routes computed for this pair
     */
    private int precomputeRouteForPair(String source, String destination, LocalDate date, 
                                        Map<String, List<Flight>> graph) {
        int routeCount = 0;

        for (int hops = 1; hops <= maxHops; hops++) {
            List<ComputedFlightEntry> routes = computeRoutes(source, destination, date, hops, graph);

            if (!routes.isEmpty()) {
                // Cache in Redis (TTL-based)
                String dateStr = date.toString();
                cacheService.cacheRoutes(dateStr, hops, source, destination, routes, cacheTtlHours);
                routeCount += routes.size();
                
                log.debug("Cached {} routes: {}->{} on {} ({} hops)", 
                        routes.size(), source, destination, date, hops);
            }
        }

        return routeCount;
    }

    /**
     * Computes routes using DFS algorithm.
     * 
     * This runs in BACKGROUND ONLY, never blocks user requests.
     */
    private List<ComputedFlightEntry> computeRoutes(String source, String destination, LocalDate date,
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
     * 
     * @param current Current location
     * @param destination Target location
     * @param date Travel date
     * @param remainingHops Number of hops remaining
     * @param path Current path being built
     * @param visited Visited locations (for cycle detection)
     * @param results Accumulator for all valid paths
     * @param minDeparture Minimum departure time (for connection time validation)
     * @param graph Flight graph
     */
    private void findPaths(String current, String destination, LocalDate date, int remainingHops,
                           List<Flight> path, Set<String> visited, List<List<Flight>> results,
                           LocalDateTime minDeparture, Map<String, List<Flight>> graph) {

        // Base case: no more hops
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

        // Try all outbound flights from current location
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


    public void shutdown() {
        log.info("Shutting down precomputation executor...");
        precomputationExecutor.shutdown();
        try {
            if (!precomputationExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                precomputationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            precomputationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
