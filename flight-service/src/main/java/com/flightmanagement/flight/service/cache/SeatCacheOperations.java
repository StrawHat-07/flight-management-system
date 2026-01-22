package com.flightmanagement.flight.service.cache;

import java.util.List;
import java.util.Optional;

/**
 * Interface for seat inventory cache operations.
 * Allows for different cache implementations (Redis, in-memory for testing).
 */
public interface SeatCacheOperations {

    void setSeats(String flightId, int seats);

    Optional<Integer> getSeats(String flightId);

    void deleteSeats(String flightId);

    void decrementSeats(String flightId, int count);

    void incrementSeats(String flightId, int count);

    int getMinSeatsAcrossFlights(List<String> flightIds);
}
