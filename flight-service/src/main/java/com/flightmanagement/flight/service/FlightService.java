package com.flightmanagement.flight.service;

import com.flightmanagement.flight.dto.FlightEntry;
import com.flightmanagement.flight.dto.FlightFilterCriteria;
import com.flightmanagement.flight.dto.PageResponse;
import com.flightmanagement.flight.enums.FlightStatus;
import com.flightmanagement.flight.event.FlightChangedEvent;
import com.flightmanagement.flight.exception.FlightNotFoundException;
import com.flightmanagement.flight.exception.FlightValidationException;
import com.flightmanagement.flight.mapper.FlightMapper;
import com.flightmanagement.flight.model.Flight;
import com.flightmanagement.flight.repository.FlightRepository;
import com.flightmanagement.flight.repository.FlightSpecification;
import com.flightmanagement.flight.util.DateTimeUtils;
import com.flightmanagement.flight.util.IdGenerator;
import com.flightmanagement.flight.validator.FlightValidator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Flight CRUD operations.
 */
@Service
@Slf4j
public class FlightService {

    private final FlightRepository flightRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    public FlightService(
            FlightRepository flightRepository, 
            InventoryService inventoryService,
            ApplicationEventPublisher eventPublisher) {
        this.flightRepository = flightRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    void initialize() {
        log.info("Initializing FlightService...");
        int synced = inventoryService.syncAllFromDb();
        log.info("FlightService initialized: {} flights synced to cache", synced);
    }


    @Transactional
    public FlightEntry createFlight(FlightEntry request) {
        FlightValidator.validateFlightEntry(request);

        String flightId = StringUtils.hasText(request.getFlightId())
                ? request.getFlightId()
                : IdGenerator.generateFlightId();

        if (flightRepository.existsByFlightId(flightId)) {
            throw new FlightValidationException("Flight already exists: " + flightId);
        }

        Flight flight = FlightMapper.toEntity(flightId, request);
        Flight saved = flightRepository.save(flight);

        inventoryService.setSeats(saved.getFlightId(), saved.getAvailableSeats());

        // Publish event for graph rebuild and route recomputation
        eventPublisher.publishEvent(
                new FlightChangedEvent(this, saved, FlightChangedEvent.ChangeType.CREATED));

        log.info("Created flight: id={}, route={}->{}", saved.getFlightId(), saved.getSource(), saved.getDestination());
        return FlightMapper.toEntry(saved);
    }

    @Transactional
    public FlightEntry updateFlight(String flightId, FlightEntry request) {
        FlightValidator.validateFlightId(flightId);
        FlightValidator.validateFlightEntry(request);

        Flight flight = findFlightOrThrow(flightId);
        FlightMapper.updateEntity(flight, request);

        Flight saved = flightRepository.save(flight);

        // Publish event for graph rebuild and route recomputation
        eventPublisher.publishEvent(
                new FlightChangedEvent(this, saved, FlightChangedEvent.ChangeType.UPDATED));

        log.info("Updated flight: id={}", flightId);
        return FlightMapper.toEntry(saved);
    }

    @Transactional
    public void cancelFlight(String flightId) {
        FlightValidator.validateFlightId(flightId);

        Flight flight = findFlightOrThrow(flightId);
        flight.setStatus(FlightStatus.CANCELLED);
        Flight saved = flightRepository.save(flight);

        inventoryService.deleteSeats(flightId);

        // Publish event for graph rebuild and route recomputation
        eventPublisher.publishEvent(
                new FlightChangedEvent(this, saved, FlightChangedEvent.ChangeType.CANCELLED));

        log.info("Cancelled flight: id={}", flightId);
    }


    public FlightEntry getFlightById(String flightId) {
        FlightValidator.validateFlightId(flightId);

        Flight flight = findFlightOrThrow(flightId);
        FlightEntry entry = FlightMapper.toEntry(flight);
        entry.setAvailableSeats(inventoryService.getAvailableSeats(flightId));
        return entry;
    }

    public PageResponse<FlightEntry> findFlights(FlightFilterCriteria criteria, Pageable pageable) {
        Specification<Flight> spec = FlightSpecification.withCriteria(criteria);
        Page<Flight> page = flightRepository.findAll(spec, pageable);
        Page<FlightEntry> entryPage = page.map(FlightMapper::toEntry);
        return PageResponse.of(entryPage);
    }

    public List<FlightEntry> getAllActiveFlights() {
        List<Flight> flights = flightRepository.findByStatus(FlightStatus.ACTIVE);
        return FlightMapper.toEntryList(flights);
    }

    public List<FlightEntry> getFlightsByRoute(String source, String destination) {
        List<Flight> flights = flightRepository.findBySourceAndDestinationAndStatus(
                com.flightmanagement.flight.util.StringUtils.normalizeLocation(source),
                com.flightmanagement.flight.util.StringUtils.normalizeLocation(destination),
                FlightStatus.ACTIVE);
        return FlightMapper.toEntryList(flights);
    }

    public List<FlightEntry> getFlightsByDate(LocalDate date) {
        LocalDateTime dayStart = DateTimeUtils.startOfDay(date);
        LocalDateTime dayEnd = DateTimeUtils.endOfDay(date);

        List<Flight> flights = flightRepository.findByDepartureTimeBetweenAndStatus(
                dayStart, dayEnd, FlightStatus.ACTIVE);
        return FlightMapper.toEntryList(flights);
    }



    public int getAvailableSeats(String flightId) {
        FlightValidator.validateFlightId(flightId);
        return inventoryService.getAvailableSeats(flightId);
    }

    @Transactional
    public boolean decrementSeats(String flightId, int count) {
        FlightValidator.validateFlightId(flightId);
        FlightValidator.validateSeatCount(count);
        return inventoryService.decrementSeats(flightId, count);
    }

    @Transactional
    public void incrementSeats(String flightId, int count) {
        FlightValidator.validateFlightId(flightId);
        FlightValidator.validateSeatCount(count);
        inventoryService.incrementSeats(flightId, count);
    }

    // ========== Private: Entity Operations ==========

    private Flight findFlightOrThrow(String flightId) {
        return flightRepository.findByFlightId(flightId)
                .orElseThrow(() -> new FlightNotFoundException(flightId));
    }
}
