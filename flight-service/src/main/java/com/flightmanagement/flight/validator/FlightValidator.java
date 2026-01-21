package com.flightmanagement.flight.validator;

import com.flightmanagement.flight.constants.ValidationMessages;
import com.flightmanagement.flight.dto.FlightEntry;
import com.flightmanagement.flight.exception.FlightValidationException;
import com.flightmanagement.flight.util.StringUtils;

import java.time.LocalDateTime;

public final class FlightValidator {

    private FlightValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void validateFlightEntry(FlightEntry entry) {

        if (entry == null) {
            throw new FlightValidationException(ValidationMessages.FLIGHT_DATA_REQUIRED);
        }

        validateSourceDestinationNotSame(entry.getSource(), entry.getDestination());

        validateArrivalAfterDeparture(entry.getDepartureTime(), entry.getArrivalTime());
    }

    public static void validateSourceDestinationNotSame(String source, String destination) {
        if (source == null || destination == null) {
            return; // Jakarta @NotBlank handles null checks
        }

        String normalizedSource = StringUtils.normalizeLocation(source);
        String normalizedDest = StringUtils.normalizeLocation(destination);

        if (normalizedSource != null && normalizedSource.equals(normalizedDest)) {
            throw new FlightValidationException(ValidationMessages.SOURCE_DESTINATION_SAME);
        }
    }

    public static void validateArrivalAfterDeparture(LocalDateTime departureTime, LocalDateTime arrivalTime) {
        if (departureTime == null || arrivalTime == null) {
            return; // Jakarta @NotNull handles null checks
        }

        if (!arrivalTime.isAfter(departureTime)) {
            throw new FlightValidationException(ValidationMessages.ARRIVAL_AFTER_DEPARTURE);
        }
    }


    public static void validateFlightId(String flightId) {
        if (!org.springframework.util.StringUtils.hasText(flightId)) {
            throw new FlightValidationException(ValidationMessages.FLIGHT_ID_REQUIRED);
        }
    }

    public static void validateSeatCount(int count) {
        if (count <= 0) {
            throw new FlightValidationException(ValidationMessages.SEAT_COUNT_POSITIVE);
        }
    }


}
