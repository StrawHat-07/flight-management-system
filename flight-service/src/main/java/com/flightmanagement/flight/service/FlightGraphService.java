package com.flightmanagement.flight.service;

import com.flightmanagement.flight.dto.FlightEntry;
import com.flightmanagement.flight.event.FlightChangedEvent;
import com.flightmanagement.flight.event.GraphRebuildEvent;
import com.flightmanagement.flight.mapper.FlightMapper;
import com.flightmanagement.flight.model.Flight;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages in-memory flight graph for route finding.
 * 
 * Single Responsibility: Graph management ONLY
 * - Builds and maintains adjacency list (source → flights)
 * - Thread-safe read/write operations
 * - Publishes events when graph changes
 */
@Service
@Slf4j
public class FlightGraphService {

    private final FlightService flightService;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, List<Flight>> flightGraph;
    private final ReadWriteLock graphLock;

    public FlightGraphService(FlightService flightService, ApplicationEventPublisher eventPublisher) {
        this.flightService = flightService;
        this.eventPublisher = eventPublisher;
        this.flightGraph = new HashMap<>();
        this.graphLock = new ReentrantReadWriteLock();
    }

    @PostConstruct
    void initialize() {
        log.info("Initializing FlightGraphService...");
        rebuildGraph();
        log.info("FlightGraphService initialized: {} source locations", flightGraph.size());
    }

    /**
     * Listens to flight changes and updates graph incrementally.
     * Non-blocking: doesn't delay the original flight operation.
     * 
     * Performance: O(1) incremental update vs O(n) full rebuild
     */
    @Async
    @EventListener
    public void onFlightChanged(FlightChangedEvent event) {
        Flight flight = event.getFlight();
        FlightChangedEvent.ChangeType changeType = event.getChangeType();
        
        log.info("Flight changed: {} ({}), updating graph incrementally...", 
                flight.getFlightId(), changeType);
        
        switch (changeType) {
            case CREATED:
            case UPDATED:
                addOrUpdateFlight(flight);
                break;
            case CANCELLED:
                removeFlight(flight);
                break;
        }
        
        // Publish event for route recomputation (only affected routes, not all)
        int locationCount = getLocationCount();
        int flightCount = getTotalFlightCount();
        eventPublisher.publishEvent(new GraphRebuildEvent(this, locationCount, flightCount));
    }

    public Map<String, List<Flight>> getGraphSnapshot() {
        graphLock.readLock().lock();
        try {
            Map<String, List<Flight>> copy = new HashMap<>();
            for (Map.Entry<String, List<Flight>> entry : flightGraph.entrySet()) {
                copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        } finally {
            graphLock.readLock().unlock();
        }
    }

    public Set<String> getActiveLocations() {
        graphLock.readLock().lock();
        try {
            Set<String> locations = new HashSet<>(flightGraph.keySet());
            
            // Add all destinations as well
            for (List<Flight> flights : flightGraph.values()) {
                for (Flight flight : flights) {
                    locations.add(flight.getDestination());
                }
            }
            
            return Collections.unmodifiableSet(locations);
        } finally {
            graphLock.readLock().unlock();
        }
    }

    public int getLocationCount() {
        graphLock.readLock().lock();
        try {
            return flightGraph.size();
        } finally {
            graphLock.readLock().unlock();
        }
    }


    public int getTotalFlightCount() {
        graphLock.readLock().lock();
        try {
            return flightGraph.values().stream()
                    .mapToInt(List::size)
                    .sum();
        } finally {
            graphLock.readLock().unlock();
        }
    }

    public void rebuildGraph() {
        graphLock.writeLock().lock();
        try {
            log.info("Rebuilding flight graph from database...");
            flightGraph.clear();

            // Get all active flights from FlightService (Repository Pattern)
            List<FlightEntry> activeFlights = flightService.getAllActiveFlights();
            
            int flightCount = 0;
            for (FlightEntry entry : activeFlights) {
                Flight flight = FlightMapper.toEntity(entry.getFlightId(), entry);
                flightGraph.computeIfAbsent(flight.getSource(), k -> new ArrayList<>()).add(flight);
                flightCount++;
            }

            int locationCount = flightGraph.size();
            log.info("Graph rebuilt: {} locations, {} flights", locationCount, flightCount);

            // Publish event to trigger route precomputation
            eventPublisher.publishEvent(new GraphRebuildEvent(this, locationCount, flightCount));

        } finally {
            graphLock.writeLock().unlock();
        }
    }


    private void addOrUpdateFlight(Flight flight) {
        graphLock.writeLock().lock();
        try {
            String source = flight.getSource();

            List<Flight> flights = flightGraph.get(source);
            if (flights != null) {
                flights.removeIf(f -> f.getFlightId().equals(flight.getFlightId()));
            }
            
            // Add new/updated flight
            flightGraph.computeIfAbsent(source, k -> new ArrayList<>()).add(flight);
            
            log.debug("Updated graph: added/updated flight {} ({}->{})", 
                    flight.getFlightId(), flight.getSource(), flight.getDestination());
            
        } finally {
            graphLock.writeLock().unlock();
        }
    }

    private void removeFlight(Flight flight) {
        graphLock.writeLock().lock();
        try {
            String source = flight.getSource();
            List<Flight> flights = flightGraph.get(source);
            
            if (flights != null) {
                flights.removeIf(f -> f.getFlightId().equals(flight.getFlightId()));

                if (flights.isEmpty()) {
                    flightGraph.remove(source);
                }
                
                log.debug("Updated graph: removed flight {} ({}->{})", 
                        flight.getFlightId(), flight.getSource(), flight.getDestination());
            }
            
        } finally {
            graphLock.writeLock().unlock();
        }
    }
}
