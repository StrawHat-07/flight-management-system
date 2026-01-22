package com.flightmanagement.booking.client;

import com.flightmanagement.booking.dto.ComputedFlightEntry;
import com.flightmanagement.booking.dto.FlightEntry;
import com.flightmanagement.booking.exception.ServiceUnavailableException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;


@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FlightServiceClient {

    final RestTemplate restTemplate;

    @Value("${flight-service.url}")
    String flightServiceUrl;

    public Optional<FlightEntry> getFlightById(String flightId) {
        String url = flightServiceUrl + "/v1/flights/" + flightId;
        log.info("Calling flight service: GET {}", url);

        try {
            ResponseEntity<FlightEntry> response = restTemplate.getForEntity(url, FlightEntry.class);
            log.info("Flight service response: status={}", response.getStatusCode());
            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Flight not found: {}", flightId);
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.error("Flight service unavailable: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Flight service unavailable");
        } catch (Exception e) {
            log.error("Error calling flight service: type={}, message={}", 
                    e.getClass().getName(), e.getMessage(), e);
            throw new ServiceUnavailableException("Error communicating with flight service");
        }
    }


    public Optional<ComputedFlightEntry> getComputedFlightById(String computedFlightId) {
        String url = flightServiceUrl + "/v1/search/computed/" + computedFlightId;
        log.debug("Calling flight service: GET {}", url);

        try {
            ResponseEntity<ComputedFlightEntry> response = restTemplate.getForEntity(url, ComputedFlightEntry.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Computed flight not found: {}", computedFlightId);
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.error("Flight service unavailable: {}", e.getMessage());
            throw new ServiceUnavailableException("Flight service unavailable");
        } catch (Exception e) {
            log.error("Error calling flight service: {}", e.getMessage());
            throw new ServiceUnavailableException("Error communicating with flight service");
        }
    }


    public boolean decrementSeats(String flightId, int seats) {
        String url = flightServiceUrl + "/v1/flights/" + flightId + "/decrement-seats?seats=" + seats;
        log.debug("Calling flight service: POST {}", url);

        try {
            restTemplate.postForEntity(url, null, Void.class);
            return true;
        } catch (Exception e) {
            log.error("Failed to decrement seats for flight {}: {}", flightId, e.getMessage());
            return false;
        }
    }


    public List<String> getFlightIds(String flightIdentifier) {
        if (!flightIdentifier.startsWith("CF_")) {
            // Direct flight
            return List.of(flightIdentifier);
        }

        // Computed flight - get flight IDs from computed flight entry
        return getComputedFlightById(flightIdentifier)
                .map(ComputedFlightEntry::getFlightIds)
                .orElse(Collections.emptyList());
    }
}
