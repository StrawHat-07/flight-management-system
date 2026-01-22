package com.flightmanagement.flight.service;

import com.flightmanagement.flight.service.cache.SeatCacheOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService implements SeatCacheOperations {

    private static final String SEATS_KEY_PATTERN = "flight:%s:availableSeats";
    private static final String COMPUTED_ROUTES_KEY_PATTERN = "computed:%s:%d:%s_%s";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void setSeats(String flightId, int seats) {
        String key = formatSeatsKey(flightId);
        try {
            redisTemplate.opsForValue().set(key, seats);
            log.debug("Set seats: flightId={}, seats={}", flightId, seats);
        } catch (Exception e) {
            log.error("Failed to set seats for flight {}: {}", flightId, e.getMessage());
        }
    }

    @Override
    public Optional<Integer> getSeats(String flightId) {
        String key = formatSeatsKey(flightId);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Optional.of(Integer.parseInt(value.toString()));
            }
        } catch (Exception e) {
            log.warn("Failed to get seats for flight {}: {}", flightId, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void deleteSeats(String flightId) {
        String key = formatSeatsKey(flightId);
        try {
            redisTemplate.delete(key);
            log.debug("Deleted seats key: flightId={}", flightId);
        } catch (Exception e) {
            log.warn("Failed to delete seats for flight {}: {}", flightId, e.getMessage());
        }
    }

    @Override
    public void decrementSeats(String flightId, int count) {
        String key = formatSeatsKey(flightId);
        try {
            redisTemplate.opsForValue().decrement(key, count);
            log.debug("Decremented seats: flightId={}, count={}", flightId, count);
        } catch (Exception e) {
            log.error("Failed to decrement seats for flight {}: {}", flightId, e.getMessage());
        }
    }

    @Override
    public void incrementSeats(String flightId, int count) {
        String key = formatSeatsKey(flightId);
        try {
            redisTemplate.opsForValue().increment(key, count);
            log.debug("Incremented seats: flightId={}, count={}", flightId, count);
        } catch (Exception e) {
            log.error("Failed to increment seats for flight {}: {}", flightId, e.getMessage());
        }
    }

    @Override
    public int getMinSeatsAcrossFlights(List<String> flightIds) {
        int minSeats = Integer.MAX_VALUE;
        for (String flightId : flightIds) {
            Optional<Integer> seats = getSeats(flightId);
            if (seats.isEmpty()) {
                return 0;
            }
            minSeats = Math.min(minSeats, seats.get());
        }
        return minSeats == Integer.MAX_VALUE ? 0 : minSeats;
    }

    public void cacheRoutes(String date, int hops, String src, String dest,
            Object routes, int ttlHours) {
        String key = formatRoutesKey(date, hops, src, dest);
        try {
            redisTemplate.opsForValue().set(key, routes, ttlHours, TimeUnit.HOURS);
            log.debug("Cached routes: key={}", key);
        } catch (Exception e) {
            log.warn("Failed to cache routes for {}: {}", key, e.getMessage());
        }
    }

    public Optional<Object> getCachedRoutes(String date, int hops, String src, String dest) {
        String key = formatRoutesKey(date, hops, src, dest);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Optional.of(value);
            }
        } catch (Exception e) {
            log.warn("Failed to get cached routes for {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public String formatSeatsKey(String flightId) {
        return String.format(SEATS_KEY_PATTERN, flightId);
    }

    private String formatRoutesKey(String date, int hops, String src, String dest) {
        return String.format(COMPUTED_ROUTES_KEY_PATTERN, date, hops, src, dest);
    }
}
