package com.flightmanagement.flight.service;

import com.flightmanagement.flight.dto.FlightEntry;
import com.flightmanagement.flight.dto.FlightFilterCriteria;
import com.flightmanagement.flight.dto.FlightGraphEntry;
import com.flightmanagement.flight.dto.PageResponse;
import com.flightmanagement.flight.enums.FlightStatus;
import com.flightmanagement.flight.exception.FlightNotFoundException;
import com.flightmanagement.flight.exception.FlightValidationException;
import com.flightmanagement.flight.model.Flight;
import com.flightmanagement.flight.repository.FlightRepository;
import com.flightmanagement.flight.repository.FlightSpecification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@Slf4j
public class FlightService {

    private final FlightRepository flightRepository;
    private final InventoryService inventoryService;

    private final Map<String, List<Flight>> flightGraph;
    private final ReadWriteLock graphLock;

    public FlightService(FlightRepository flightRepository, InventoryService inventoryService) {
        this.flightRepository = flightRepository;
        this.inventoryService = inventoryService;
        this.flightGraph = new HashMap<>();
        this.graphLock = new ReentrantReadWriteLock();
    }

    @PostConstruct
    void initialize() {
        log.info("Initializing FlightService...");
        rebuildGraphFromDatabase();
        int synced = inventoryService.syncAllFromDb();
        log.info("FlightService initialized: {} source locations, {} flights synced to cache", 
                flightGraph.size(), synced);
    }

    // ========== CRUD Operations ==========

    @Transactional
    public FlightEntry createFlight(FlightEntry request) {
        validateCreateRequest(request);

        String flightId = hasText(request.getFlightId())
                ? request.getFlightId()
                : generateFlightId();

        if (flightRepository.existsByFlightId(flightId)) {
            throw new FlightValidationException("Flight already exists: " + flightId);
        }

        Flight flight = buildFlightEntity(flightId, request);
        Flight saved = flightRepository.save(flight);

        addFlightToGraph(saved);
        inventoryService.setSeats(saved.getFlightId(), saved.getAvailableSeats());

        log.info("Created flight: id={}, route={}->{}", saved.getFlightId(), saved.getSource(), saved.getDestination());
        return toEntry(saved);
    }

    @Transactional
    public FlightEntry updateFlight(String flightId, FlightEntry request) {
        validateFlightId(flightId);
        validateUpdateRequest(request);

        Flight flight = findFlightOrThrow(flightId);
        applyUpdates(flight, request);

        Flight saved = flightRepository.save(flight);
        rebuildGraph();

        log.info("Updated flight: id={}", flightId);
        return toEntry(saved);
    }

    @Transactional
    public void cancelFlight(String flightId) {
        validateFlightId(flightId);

        Flight flight = findFlightOrThrow(flightId);
        flight.setStatus(FlightStatus.CANCELLED);
        flightRepository.save(flight);

        inventoryService.deleteSeats(flightId);
        rebuildGraph();

        log.info("Cancelled flight: id={}", flightId);
    }

    // ========== Query Operations ==========

    public FlightEntry getFlightById(String flightId) {
        validateFlightId(flightId);

        Flight flight = findFlightOrThrow(flightId);
        FlightEntry entry = toEntry(flight);
        entry.setAvailableSeats(inventoryService.getAvailableSeats(flightId));
        return entry;
    }

    public PageResponse<FlightEntry> findFlights(FlightFilterCriteria criteria, Pageable pageable) {
        Specification<Flight> spec = FlightSpecification.withCriteria(criteria);
        Page<Flight> page = flightRepository.findAll(spec, pageable);
        Page<FlightEntry> entryPage = page.map(this::toEntry);
        return PageResponse.of(entryPage);
    }

    public List<FlightEntry> getAllActiveFlights() {
        List<Flight> flights = flightRepository.findByStatus(FlightStatus.ACTIVE);
        return toEntryList(flights);
    }

    public List<FlightEntry> getFlightsByRoute(String source, String destination) {
        if (!hasText(source) || !hasText(destination)) {
            throw new FlightValidationException("Source and destination are required");
        }

        List<Flight> flights = flightRepository.findBySourceAndDestinationAndStatus(
                normalize(source), normalize(destination), FlightStatus.ACTIVE);
        return toEntryList(flights);
    }

    public List<FlightEntry> getFlightsByDate(LocalDate date) {
        if (date == null) {
            throw new FlightValidationException("Date is required");
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(23, 59, 59);

        List<Flight> flights = flightRepository.findByDepartureTimeBetweenAndStatus(
                dayStart, dayEnd, FlightStatus.ACTIVE);
        return toEntryList(flights);
    }

    // ========== Graph Operations ==========

    public FlightGraphEntry getFlightGraphSnapshot() {
        graphLock.readLock().lock();
        try {
            Map<String, List<FlightEntry>> graphCopy = new HashMap<>();
            for (Map.Entry<String, List<Flight>> entry : flightGraph.entrySet()) {
                graphCopy.put(entry.getKey(), toEntryList(entry.getValue()));
            }
            List<String> locations = flightRepository.findAllActiveLocations();
            return FlightGraphEntry.builder()
                    .graph(graphCopy)
                    .locations(new ArrayList<>(locations))
                    .build();
        } finally {
            graphLock.readLock().unlock();
        }
    }

    public Map<String, List<Flight>> getFlightGraphForSearch() {
        graphLock.readLock().lock();
        try {
            Map<String, List<Flight>> copy = new HashMap<>();
            for (Map.Entry<String, List<Flight>> entry : flightGraph.entrySet()) {
                copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return copy;
        } finally {
            graphLock.readLock().unlock();
        }
    }

    public void rebuildGraph() {
        graphLock.writeLock().lock();
        try {
            rebuildGraphFromDatabase();
        } finally {
            graphLock.writeLock().unlock();
        }
    }

    // ========== Seat Management ==========

    public int getAvailableSeats(String flightId) {
        validateFlightId(flightId);
        return inventoryService.getAvailableSeats(flightId);
    }

    @Transactional
    public boolean decrementSeats(String flightId, int count) {
        validateFlightId(flightId);
        validateSeatCount(count);
        return inventoryService.decrementSeats(flightId, count);
    }

    @Transactional
    public void incrementSeats(String flightId, int count) {
        validateFlightId(flightId);
        validateSeatCount(count);
        inventoryService.incrementSeats(flightId, count);
    }

    // ========== Private: Validation ==========

    private void validateFlightId(String flightId) {
        if (!hasText(flightId)) {
            throw new FlightValidationException("Flight ID is required");
        }
    }

    private void validateSeatCount(int count) {
        if (count <= 0) {
            throw new FlightValidationException("Seat count must be positive");
        }
    }

    private void validateCreateRequest(FlightEntry entry) {
        if (entry == null) {
            throw new FlightValidationException("Flight data is required");
        }
        validateCommonFields(entry);
    }

    private void validateUpdateRequest(FlightEntry entry) {
        if (entry == null) {
            throw new FlightValidationException("Flight data is required");
        }
        validateCommonFields(entry);
    }

    private void validateCommonFields(FlightEntry entry) {
        if (!hasText(entry.getSource())) {
            throw new FlightValidationException("Source is required");
        }
        if (!hasText(entry.getDestination())) {
            throw new FlightValidationException("Destination is required");
        }
        if (normalize(entry.getSource()).equals(normalize(entry.getDestination()))) {
            throw new FlightValidationException("Source and destination cannot be the same");
        }
        if (entry.getDepartureTime() == null) {
            throw new FlightValidationException("Departure time is required");
        }
        if (entry.getArrivalTime() == null) {
            throw new FlightValidationException("Arrival time is required");
        }
        if (!entry.getArrivalTime().isAfter(entry.getDepartureTime())) {
            throw new FlightValidationException("Arrival must be after departure");
        }
        if (entry.getTotalSeats() == null || entry.getTotalSeats() < 1) {
            throw new FlightValidationException("Total seats must be at least 1");
        }
        if (entry.getPrice() == null || entry.getPrice().signum() < 0) {
            throw new FlightValidationException("Price must be non-negative");
        }
    }

    // ========== Private: Entity Operations ==========

    private Flight findFlightOrThrow(String flightId) {
        return flightRepository.findByFlightId(flightId)
                .orElseThrow(() -> new FlightNotFoundException(flightId));
    }

    private Flight buildFlightEntity(String flightId, FlightEntry entry) {
        return Flight.builder()
                .flightId(flightId)
                .source(normalize(entry.getSource()))
                .destination(normalize(entry.getDestination()))
                .departureTime(entry.getDepartureTime())
                .arrivalTime(entry.getArrivalTime())
                .totalSeats(entry.getTotalSeats())
                .availableSeats(entry.getTotalSeats())
                .price(entry.getPrice())
                .status(FlightStatus.ACTIVE)
                .build();
    }

    private void applyUpdates(Flight flight, FlightEntry entry) {
        flight.setSource(normalize(entry.getSource()));
        flight.setDestination(normalize(entry.getDestination()));
        flight.setDepartureTime(entry.getDepartureTime());
        flight.setArrivalTime(entry.getArrivalTime());
        flight.setTotalSeats(entry.getTotalSeats());
        flight.setPrice(entry.getPrice());
    }

    // ========== Private: Graph Management ==========

    private void rebuildGraphFromDatabase() {
        log.info("Rebuilding flight graph...");
        flightGraph.clear();

        List<Flight> activeFlights = flightRepository.findByStatus(FlightStatus.ACTIVE);
        for (Flight flight : activeFlights) {
            flightGraph.computeIfAbsent(flight.getSource(), k -> new ArrayList<>()).add(flight);
        }

        log.info("Graph rebuilt: {} locations, {} flights", flightGraph.size(), activeFlights.size());
    }

    private void addFlightToGraph(Flight flight) {
        graphLock.writeLock().lock();
        try {
            flightGraph.computeIfAbsent(flight.getSource(), k -> new ArrayList<>()).add(flight);
        } finally {
            graphLock.writeLock().unlock();
        }
    }

    // ========== Private: Utility ==========

    private String normalize(String location) {
        return location.trim().toUpperCase();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String generateFlightId() {
        return "FL" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private FlightEntry toEntry(Flight flight) {
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
                .status(flight.getStatus().name())
                .createdAt(flight.getCreatedAt())
                .updatedAt(flight.getUpdatedAt())
                .build();
    }

    private List<FlightEntry> toEntryList(List<Flight> flights) {
        List<FlightEntry> result = new ArrayList<>(flights.size());
        for (Flight flight : flights) {
            result.add(toEntry(flight));
        }
        return result;
    }
}
