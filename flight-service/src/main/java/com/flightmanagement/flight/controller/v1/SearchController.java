package com.flightmanagement.flight.controller.v1;

import com.flightmanagement.flight.dto.ComputedFlightEntry;
import com.flightmanagement.flight.dto.PageResponse;
import com.flightmanagement.flight.dto.SearchRequest;
import com.flightmanagement.flight.dto.SearchResponse;
import com.flightmanagement.flight.exception.FlightNotFoundException;
import com.flightmanagement.flight.service.FlightService;
import com.flightmanagement.flight.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/search")
public class SearchController {

    private final SearchService searchService;
    private final FlightService flightService;

    private final Executor backgroundExecutor = Executors.newFixedThreadPool(2);

    @GetMapping
    public ResponseEntity<PageResponse<SearchResponse>> search(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") Integer seats,
            @RequestParam(defaultValue = "3") Integer maxHops,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        log.info("GET /v1/search: {} -> {} on {}, seats={}, maxHops={}, page={}, size={}, sort={}",
                source, destination, date, seats, maxHops, page, size, sortBy);

        SearchRequest request = SearchRequest.builder()
                .source(source)
                .destination(destination)
                .date(date)
                .seats(seats)
                .maxHops(maxHops)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        PageResponse<SearchResponse> results = searchService.searchFlights(
                request, flightService.getFlightGraphForSearch());

        return ResponseEntity.ok(results);
    }

    @PostMapping
    public ResponseEntity<PageResponse<SearchResponse>> searchPost(@Valid @RequestBody SearchRequest request) {
        log.info("POST /v1/search: {} -> {} on {}, page={}, size={}, sort={}",
                request.getSource(), request.getDestination(), request.getDate(),
                request.getPage(), request.getSize(), request.getSortBy());

        PageResponse<SearchResponse> results = searchService.searchFlights(
                request, flightService.getFlightGraphForSearch());

        return ResponseEntity.ok(results);
    }

    @GetMapping("/computed/{computedFlightId}")
    public ResponseEntity<ComputedFlightEntry> getComputedFlight(@PathVariable String computedFlightId) {
        log.debug("GET /v1/search/computed/{}", computedFlightId);

        ComputedFlightEntry flight = searchService.getComputedFlight(
                computedFlightId, flightService.getFlightGraphForSearch());

        if (flight == null) {
            throw new FlightNotFoundException(computedFlightId);
        }

        return ResponseEntity.ok(flight);
    }

    @PostMapping("/recompute")
    public ResponseEntity<Map<String, String>> triggerRecomputation() {
        log.info("POST /v1/search/recompute");

        backgroundExecutor.execute(() -> {
            try {
                searchService.runPrecomputation(flightService.getFlightGraphForSearch());
            } catch (Exception e) {
                log.error("Recomputation failed", e);
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "message", "Recomputation triggered"));
    }
}
