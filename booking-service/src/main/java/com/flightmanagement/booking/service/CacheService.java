package com.flightmanagement.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Centralized cache operations for booking service.
 * Abstracts Redis interactions for seat blocking and availability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private static final String AVAILABLE_SEATS_KEY_PATTERN = "flight:%s:availableSeats";
    private static final String BLOCKED_SEATS_KEY_PATTERN = "flight:%s:blocked:%s";

    private final RedisTemplate<String, Object> redisTemplate;

    // ========== Script Execution ==========

    /**
     * Executes a Redis Lua script atomically.
     *
     * @param script the Lua script to execute
     * @param keys   list of Redis keys used in the script
     * @param args   arguments to pass to the script
     * @return result of the script execution, or null on failure
     */
    public <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args) {
        try {
            return redisTemplate.execute(script, keys, args);
        } catch (Exception e) {
            log.error("Failed to execute Redis script: {}", e.getMessage());
            return null;
        }
    }

    // ========== Blocked Seats Operations ==========

    /**
     * Gets the value of a blocked seats key.
     */
    public Optional<Integer> getBlockedSeats(String flightId, String bookingId) {
        String key = formatBlockedSeatsKey(flightId, bookingId);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Optional.of(Integer.parseInt(value.toString()));
            }
        } catch (Exception e) {
            log.warn("Failed to get blocked seats for flight={}, booking={}: {}", 
                    flightId, bookingId, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Deletes a blocked seats key.
     */
    public void deleteBlockedSeats(String flightId, String bookingId) {
        String key = formatBlockedSeatsKey(flightId, bookingId);
        try {
            redisTemplate.delete(key);
            log.debug("Deleted blocked seats: flight={}, booking={}", flightId, bookingId);
        } catch (Exception e) {
            log.warn("Failed to delete blocked seats for flight={}, booking={}: {}", 
                    flightId, bookingId, e.getMessage());
        }
    }

    // ========== Available Seats Operations ==========

    /**
     * Increments available seats for a flight.
     */
    public void incrementAvailableSeats(String flightId, int count) {
        String key = formatAvailableSeatsKey(flightId);
        try {
            redisTemplate.opsForValue().increment(key, count);
            log.debug("Incremented available seats: flightId={}, count={}", flightId, count);
        } catch (Exception e) {
            log.error("Failed to increment available seats for flight {}: {}", flightId, e.getMessage());
        }
    }

    /**
     * Gets available seats for a flight.
     */
    public Optional<Integer> getAvailableSeats(String flightId) {
        String key = formatAvailableSeatsKey(flightId);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Optional.of(Integer.parseInt(value.toString()));
            }
        } catch (Exception e) {
            log.warn("Failed to get available seats for flight {}: {}", flightId, e.getMessage());
        }
        return Optional.empty();
    }

    // ========== Key Formatters ==========

    /**
     * Formats the Redis key for flight available seats.
     */
    public String formatAvailableSeatsKey(String flightId) {
        return String.format(AVAILABLE_SEATS_KEY_PATTERN, flightId);
    }

    /**
     * Formats the Redis key for blocked seats.
     */
    public String formatBlockedSeatsKey(String flightId, String bookingId) {
        return String.format(BLOCKED_SEATS_KEY_PATTERN, flightId, bookingId);
    }
}
