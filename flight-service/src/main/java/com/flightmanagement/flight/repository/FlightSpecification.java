package com.flightmanagement.flight.repository;

import com.flightmanagement.flight.dto.FlightFilterCriteria;
import com.flightmanagement.flight.model.Flight;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public final class FlightSpecification {

    private FlightSpecification() {
    }

    public static Specification<Flight> withCriteria(FlightFilterCriteria criteria) {
        return Specification
                .where(hasSource(criteria.getSource()))
                .and(hasDestination(criteria.getDestination()))
                .and(hasDepartureDate(criteria.getDepartureDate()))
                .and(hasStatus(criteria.getStatus()))
                .and(hasPriceGreaterThanOrEqual(criteria.getMinPrice()))
                .and(hasPriceLessThanOrEqual(criteria.getMaxPrice()))
                .and(hasMinAvailableSeats(criteria.getMinAvailableSeats()));
    }

    public static Specification<Flight> hasSource(String source) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(source)) {
                return null;
            }
            return cb.equal(cb.upper(root.get("source")), source.toUpperCase().trim());
        };
    }

    public static Specification<Flight> hasDestination(String destination) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(destination)) {
                return null;
            }
            return cb.equal(cb.upper(root.get("destination")), destination.toUpperCase().trim());
        };
    }

    public static Specification<Flight> hasDepartureDate(java.time.LocalDate date) {
        return (root, query, cb) -> {
            if (date == null) {
                return null;
            }
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(23, 59, 59);
            return cb.between(root.get("departureTime"), startOfDay, endOfDay);
        };
    }

    public static Specification<Flight> hasStatus(com.flightmanagement.flight.enums.FlightStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return null;
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Flight> hasPriceGreaterThanOrEqual(java.math.BigDecimal minPrice) {
        return (root, query, cb) -> {
            if (minPrice == null) {
                return null;
            }
            return cb.greaterThanOrEqualTo(root.get("price"), minPrice);
        };
    }

    public static Specification<Flight> hasPriceLessThanOrEqual(java.math.BigDecimal maxPrice) {
        return (root, query, cb) -> {
            if (maxPrice == null) {
                return null;
            }
            return cb.lessThanOrEqualTo(root.get("price"), maxPrice);
        };
    }

    public static Specification<Flight> hasMinAvailableSeats(Integer minSeats) {
        return (root, query, cb) -> {
            if (minSeats == null) {
                return null;
            }
            return cb.greaterThanOrEqualTo(root.get("availableSeats"), minSeats);
        };
    }

    public static Specification<Flight> isActive() {
        return hasStatus(com.flightmanagement.flight.enums.FlightStatus.ACTIVE);
    }
}
