package com.flightmanagement.flight.service;

import com.flightmanagement.flight.enums.FlightStatus;
import com.flightmanagement.flight.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final CacheService cacheService;
    private final FlightRepository flightRepository;

    public int getAvailableSeats(String flightId) {
        Optional<Integer> cachedSeats = cacheService.getSeats(flightId);
        if (cachedSeats.isPresent()) {
            return cachedSeats.get();
        }

        log.info("Cache miss for flight {}, falling back to DB", flightId);
        return flightRepository.findByFlightId(flightId)
                .map(flight -> {
                    cacheService.setSeats(flightId, flight.getAvailableSeats());
                    return flight.getAvailableSeats();
                })
                .orElse(0);
    }

    @Transactional
    public boolean decrementSeats(String flightId, int count) {
        int updated = flightRepository.decrementAvailableSeats(flightId, count);
        if (updated == 0) {
            log.warn("DB seat decrement failed: flightId={}, requested={}", flightId, count);
            return false;
        }

        try {
            cacheService.decrementSeats(flightId, count);
            log.debug("Write-through decrement: flightId={}, count={}", flightId, count);
        } catch (Exception e) {
            log.error("Cache write-through failed for decrement, DB updated: flightId={}, error={}", 
                    flightId, e.getMessage());
        }

        return true;
    }

    @Transactional
    public boolean decrementSeatsDbOnly(String flightId, int count) {
        int updated = flightRepository.decrementAvailableSeats(flightId, count);
        if (updated == 0) {
            log.warn("DB-only seat decrement failed: flightId={}, requested={}", flightId, count);
            return false;
        }
        log.debug("DB-only decrement: flightId={}, count={}", flightId, count);
        return true;
    }

    @Transactional
    public void incrementSeatsDbOnly(String flightId, int count) {
        flightRepository.incrementAvailableSeats(flightId, count);
        log.debug("DB-only increment: flightId={}, count={}", flightId, count);
    }

    @Transactional
    public void incrementSeats(String flightId, int count) {
        flightRepository.incrementAvailableSeats(flightId, count);

        try {
            cacheService.incrementSeats(flightId, count);
            log.debug("Write-through increment: flightId={}, count={}", flightId, count);
        } catch (Exception e) {
            log.error("Cache write-through failed for increment, DB updated: flightId={}, error={}", 
                    flightId, e.getMessage());
        }
    }

    @Transactional
    public void setSeats(String flightId, int seats) {
        flightRepository.findByFlightId(flightId).ifPresent(flight -> {
            flight.setAvailableSeats(seats);
            flightRepository.save(flight);
        });

        cacheService.setSeats(flightId, seats);
        log.debug("Set seats: flightId={}, seats={}", flightId, seats);
    }

    public void syncCacheFromDb(String flightId) {
        flightRepository.findByFlightId(flightId).ifPresent(flight -> {
            cacheService.setSeats(flightId, flight.getAvailableSeats());
            log.info("Synced flight {} from DB to cache: {} seats", flightId, flight.getAvailableSeats());
        });
    }

    public int syncAllFromDb() {
        var flights = flightRepository.findByStatus(FlightStatus.ACTIVE);
        
        int count = 0;
        for (var flight : flights) {
            try {
                cacheService.setSeats(flight.getFlightId(), flight.getAvailableSeats());
                count++;
            } catch (Exception e) {
                log.error("Failed to sync flight {}: {}", flight.getFlightId(), e.getMessage());
            }
        }
        
        log.info("Synced {} flights from DB to cache", count);
        return count;
    }

    public void deleteSeats(String flightId) {
        cacheService.deleteSeats(flightId);
    }
}
