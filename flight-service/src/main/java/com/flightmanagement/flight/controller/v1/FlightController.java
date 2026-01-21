package com.flightmanagement.flight.controller.v1;

import com.flightmanagement.flight.dto.FlightEntry;
import com.flightmanagement.flight.dto.FlightFilterCriteria;
import com.flightmanagement.flight.dto.FlightGraphEntry;
import com.flightmanagement.flight.dto.PageResponse;
import com.flightmanagement.flight.enums.FlightStatus;
import com.flightmanagement.flight.mapper.FlightMapper;
import com.flightmanagement.flight.model.Flight;
import com.flightmanagement.flight.service.FlightGraphService;
import com.flightmanagement.flight.service.FlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/flights")
public class FlightController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "departureTime", "arrivalTime", "price", "source", "destination",
            "status", "totalSeats", "availableSeats");

    private final FlightService flightService;
    private final FlightGraphService graphService;

    @PostMapping
    public ResponseEntity<FlightEntry> create(@Valid @RequestBody FlightEntry entry) {
        log.info("POST /v1/flights: source={}, destination={}", entry.getSource(), entry.getDestination());

        FlightEntry created = flightService.createFlight(entry);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageResponse<FlightEntry>> listAll(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @RequestParam(required = false) FlightStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minAvailableSeats,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "departureTime") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        log.debug("GET /v1/flights with filters: source={}, destination={}, date={}, status={}",
                source, destination, departureDate, status);

        FlightFilterCriteria criteria = FlightFilterCriteria.builder()
                .source(source)
                .destination(destination)
                .departureDate(departureDate)
                .status(status)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minAvailableSeats(minAvailableSeats)
                .build();

        Pageable pageable = createPageable(page, size, sortBy, sortDirection);
        PageResponse<FlightEntry> response = flightService.findFlights(criteria, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<List<FlightEntry>> listAllWithoutPagination() {
        log.debug("GET /v1/flights/all");
        return ResponseEntity.ok(flightService.getAllActiveFlights());
    }

    @GetMapping("/{flightId}")
    public ResponseEntity<FlightEntry> getById(@PathVariable String flightId) {
        log.debug("GET /v1/flights/{}", flightId);
        return ResponseEntity.ok(flightService.getFlightById(flightId));
    }

    @PutMapping("/{flightId}")
    public ResponseEntity<FlightEntry> update(
            @PathVariable String flightId,
            @Valid @RequestBody FlightEntry entry) {
        log.info("PUT /v1/flights/{}", flightId);

        FlightEntry updated = flightService.updateFlight(flightId, entry);

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{flightId}")
    public ResponseEntity<Void> delete(@PathVariable String flightId) {
        log.info("DELETE /v1/flights/{}", flightId);

        flightService.cancelFlight(flightId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/route")
    public ResponseEntity<List<FlightEntry>> searchByRoute(
            @RequestParam String source,
            @RequestParam String destination) {
        log.debug("GET /v1/flights/route: source={}, destination={}", source, destination);
        return ResponseEntity.ok(flightService.getFlightsByRoute(source, destination));
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<FlightEntry>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("GET /v1/flights/date/{}", date);
        return ResponseEntity.ok(flightService.getFlightsByDate(date));
    }

    @GetMapping("/graph")
    public ResponseEntity<FlightGraphEntry> getGraph() {
        log.debug("GET /v1/flights/graph");

        Map<String, List<Flight>> graph = graphService.getGraphSnapshot();
        Set<String> locations = graphService.getActiveLocations();

        Map<String, List<FlightEntry>> graphCopy = new HashMap<>();
        for (Map.Entry<String, List<Flight>> entry : graph.entrySet()) {
            graphCopy.put(entry.getKey(), FlightMapper.toEntryList(entry.getValue()));
        }

        FlightGraphEntry graphEntry = FlightGraphEntry.builder()
                .graph(graphCopy)
                .locations(new ArrayList<>(locations))
                .build();

        return ResponseEntity.ok(graphEntry);
    }

    @GetMapping("/{flightId}/available-seats")
    public ResponseEntity<Map<String, Object>> getAvailableSeats(@PathVariable String flightId) {
        log.debug("GET /v1/flights/{}/available-seats", flightId);
        int seats = flightService.getAvailableSeats(flightId);
        return ResponseEntity.ok(Map.of("flightId", flightId, "availableSeats", seats));
    }

    @PostMapping("/{flightId}/decrement-seats")
    public ResponseEntity<Map<String, Object>> decrementSeats(
            @PathVariable String flightId,
            @RequestParam int seats) {
        log.info("POST /v1/flights/{}/decrement-seats: seats={}", flightId, seats);

        boolean success = flightService.decrementSeats(flightId, seats);
        return ResponseEntity.ok(Map.of(
                "flightId", flightId,
                "success", success,
                "decremented", seats));
    }

    @PostMapping("/{flightId}/increment-seats")
    public ResponseEntity<Map<String, Object>> incrementSeats(
            @PathVariable String flightId,
            @RequestParam int seats) {
        log.info("POST /v1/flights/{}/increment-seats: seats={}", flightId, seats);

        flightService.incrementSeats(flightId, seats);
        return ResponseEntity.ok(Map.of(
                "flightId", flightId,
                "success", true,
                "incremented", seats));
    }

    private Pageable createPageable(int page, int size, String sortBy, String sortDirection) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid sort field '{}', defaulting to 'departureTime'", sortBy);
            sortBy = "departureTime";
        }

        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return PageRequest.of(page, pageSize, Sort.by(direction, sortBy));
    }
}
