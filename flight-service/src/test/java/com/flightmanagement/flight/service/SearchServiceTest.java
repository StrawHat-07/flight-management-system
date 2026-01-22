package com.flightmanagement.flight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightmanagement.flight.dto.ComputedFlightEntry;
import com.flightmanagement.flight.dto.PageResponse;
import com.flightmanagement.flight.dto.SearchRequest;
import com.flightmanagement.flight.dto.SearchResponse;
import com.flightmanagement.flight.exception.FlightValidationException;
import com.flightmanagement.flight.model.Flight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchService")
class SearchServiceTest {

        @Mock
        private FlightGraphService graphService;

        @Mock
        private CacheService cacheService;

        @Mock
        private RouteFinderService routeFinderService;

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        private SearchService searchService;
        private Map<String, List<Flight>> flightGraph;
        private LocalDate testDate;

        @BeforeEach
        void setUp() {
                testDate = LocalDate.now().plusDays(1);
                flightGraph = createTestGraph(testDate);

                // Mock FlightGraphService to return test graph
                when(graphService.getGraphSnapshot()).thenReturn(flightGraph);

                searchService = new SearchService(
                                graphService,
                                cacheService,
                                routeFinderService,
                                objectMapper,
                                3,
                                24);
        }

        private Map<String, List<Flight>> createTestGraph(LocalDate date) {
                Map<String, List<Flight>> graph = new HashMap<>();

                Flight direct = createFlight("FL101", "DEL", "BLR", date, 6, 0, 8, 45, 180, 5500.00);
                Flight leg1 = createFlight("FL103", "DEL", "HYD", date, 8, 0, 10, 30, 160, 5200.00);
                Flight leg2 = createFlight("FL115", "HYD", "BLR", date, 12, 0, 13, 15, 140, 2700.00);

                graph.put("DEL", List.of(direct, leg1));
                graph.put("HYD", List.of(leg2));

                return graph;
        }

        private Flight createFlight(String id, String source, String destination, LocalDate date,
                        int depHour, int depMin, int arrHour, int arrMin,
                        int seats, double price) {
                return Flight.builder()
                                .flightId(id)
                                .source(source)
                                .destination(destination)
                                .departureTime(date.atTime(depHour, depMin))
                                .arrivalTime(date.atTime(arrHour, arrMin))
                                .totalSeats(seats)
                                .availableSeats(seats)
                                .price(BigDecimal.valueOf(price))
                                .build();
        }

        @Nested
        @DisplayName("validation")
        class Validation {

                @Test
                @DisplayName("rejects null request")
                void rejectsNullRequest() {
                        assertThatThrownBy(() -> searchService.searchFlights(null))
                                        .isInstanceOf(FlightValidationException.class)
                                        .hasMessageContaining("required");
                }

                @Test
                @DisplayName("rejects same source and destination (business rule)")
                void rejectsSameSourceDestination() {
                        SearchRequest request = SearchRequest.builder()
                                        .source("DEL")
                                        .destination("DEL")
                                        .date(testDate)
                                        .build();

                        assertThatThrownBy(() -> searchService.searchFlights(request))
                                        .isInstanceOf(FlightValidationException.class)
                                        .hasMessageContaining("cannot be the same");
                }

                @Test
                @DisplayName("rejects past date (business rule)")
                void rejectsPastDate() {
                        SearchRequest request = SearchRequest.builder()
                                        .source("DEL")
                                        .destination("BLR")
                                        .date(LocalDate.now().minusDays(1))
                                        .build();

                        assertThatThrownBy(() -> searchService.searchFlights(request))
                                        .isInstanceOf(FlightValidationException.class)
                                        .hasMessageContaining("past");
                }
        }

        @Nested
        @DisplayName("search results")
        class SearchResults {

                @Test
                @DisplayName("finds direct flight")
                void findsDirectFlight() {
                        when(cacheService.getCachedRoutes(anyString(), anyInt(), anyString(), anyString()))
                                        .thenReturn(Optional.empty());
                        when(cacheService.getMinSeatsAcrossFlights(any())).thenReturn(180);

                        // Mock routeFinderService to return a direct flight
                        ComputedFlightEntry directFlight = ComputedFlightEntry.builder()
                                        .computedFlightId("FL101")
                                        .source("DEL")
                                        .destination("BLR")
                                        .flightIds(List.of("FL101"))
                                        .numberOfHops(1)
                                        .totalPrice(BigDecimal.valueOf(5500))
                                        .build();
                        when(routeFinderService.findRoutes(eq("DEL"), eq("BLR"), eq(testDate), eq(1), any()))
                                        .thenReturn(List.of(directFlight));

                        SearchRequest request = SearchRequest.builder()
                                        .source("DEL")
                                        .destination("BLR")
                                        .date(testDate)
                                        .seats(1)
                                        .maxHops(1)
                                        .build();

                        PageResponse<SearchResponse> results = searchService.searchFlights(request);

                        assertThat(results.getContent()).hasSize(1);
                        assertThat(results.getContent().get(0).getType()).isEqualTo("DIRECT");
                        assertThat(results.getContent().get(0).getFlightIdentifier()).isEqualTo("FL101");
                        assertThat(results.getTotalElements()).isEqualTo(1);
                        assertThat(results.getTotalPages()).isEqualTo(1);
                }

                @Test
                @DisplayName("filters by available seats")
                void filtersBySeats() {
                        when(cacheService.getCachedRoutes(anyString(), anyInt(), anyString(), anyString()))
                                        .thenReturn(Optional.empty());
                        when(cacheService.getMinSeatsAcrossFlights(any())).thenReturn(2);

                        // Mock routeFinderService to return a flight with limited seats
                        ComputedFlightEntry directFlight = ComputedFlightEntry.builder()
                                        .computedFlightId("FL101")
                                        .source("DEL")
                                        .destination("BLR")
                                        .flightIds(List.of("FL101"))
                                        .numberOfHops(1)
                                        .totalPrice(BigDecimal.valueOf(5500))
                                        .build();
                        when(routeFinderService.findRoutes(eq("DEL"), eq("BLR"), eq(testDate), eq(1), any()))
                                        .thenReturn(List.of(directFlight));

                        SearchRequest request = SearchRequest.builder()
                                        .source("DEL")
                                        .destination("BLR")
                                        .date(testDate)
                                        .seats(5)
                                        .maxHops(1)
                                        .build();

                        PageResponse<SearchResponse> results = searchService.searchFlights(request);

                        assertThat(results.getContent()).isEmpty();
                        assertThat(results.getTotalElements()).isEqualTo(0);
                }

                @Test
                @DisplayName("returns empty for non-existent route")
                void returnsEmptyForNoRoute() {
                        when(cacheService.getCachedRoutes(anyString(), anyInt(), anyString(), anyString()))
                                        .thenReturn(Optional.empty());
                        // Mock routeFinderService to return empty for non-existent route
                        when(routeFinderService.findRoutes(anyString(), anyString(), any(), anyInt(), any()))
                                        .thenReturn(Collections.emptyList());

                        SearchRequest request = SearchRequest.builder()
                                        .source("DEL")
                                        .destination("GOA")
                                        .date(testDate)
                                        .seats(1)
                                        .build();

                        PageResponse<SearchResponse> results = searchService.searchFlights(request);

                        assertThat(results.getContent()).isEmpty();
                        assertThat(results.getTotalElements()).isEqualTo(0);
                }
        }

        @Nested
        @DisplayName("getComputedFlight")
        class GetComputedFlight {

                @Test
                @DisplayName("returns direct flight by ID")
                void returnsDirectFlight() {
                        ComputedFlightEntry result = searchService.getComputedFlight("FL101");

                        assertThat(result).isNotNull();
                        assertThat(result.getComputedFlightId()).isEqualTo("FL101");
                        assertThat(result.getNumberOfHops()).isEqualTo(1);
                        assertThat(result.getSource()).isEqualTo("DEL");
                        assertThat(result.getDestination()).isEqualTo("BLR");
                }

                @Test
                @DisplayName("returns null for non-existent flight")
                void returnsNullForNotFound() {
                        ComputedFlightEntry result = searchService.getComputedFlight("INVALID");

                        assertThat(result).isNull();
                }

                @Test
                @DisplayName("rejects empty ID")
                void rejectsEmptyId() {
                        assertThatThrownBy(() -> searchService.getComputedFlight(""))
                                        .isInstanceOf(FlightValidationException.class);
                }
        }
}
