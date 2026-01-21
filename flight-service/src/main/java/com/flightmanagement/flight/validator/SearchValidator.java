package com.flightmanagement.flight.validator;

import com.flightmanagement.flight.constants.ValidationMessages;
import com.flightmanagement.flight.dto.SearchRequest;
import com.flightmanagement.flight.exception.FlightValidationException;
import com.flightmanagement.flight.util.DateTimeUtils;
import com.flightmanagement.flight.util.StringUtils;

import java.time.LocalDate;


public final class SearchValidator {

    private SearchValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void validateSearchRequest(SearchRequest request) {
        if (request == null) {
            throw new FlightValidationException(ValidationMessages.SEARCH_REQUEST_REQUIRED);
        }

        validateSourceDestinationNotSame(request.getSource(), request.getDestination());
        validateDateNotPast(request.getDate());
    }

    private static void validateSourceDestinationNotSame(String source, String destination) {
        if (source == null || destination == null) {
            return;
        }
        String normalizedSource = StringUtils.normalizeLocation(source);
        String normalizedDest = StringUtils.normalizeLocation(destination);
        
        if (normalizedSource != null && normalizedSource.equals(normalizedDest)) {
            throw new FlightValidationException(ValidationMessages.SOURCE_DESTINATION_SAME);
        }
    }

    private static void validateDateNotPast(LocalDate date) {
        if (date == null) {
            return;
        }
        
        if (!DateTimeUtils.isTodayOrFuture(date)) {
            throw new FlightValidationException(ValidationMessages.DATE_NOT_PAST);
        }
    }

    public static void validateFlightIdentifier(String identifier) {
        if (!org.springframework.util.StringUtils.hasText(identifier)) {
            throw new FlightValidationException(ValidationMessages.FLIGHT_IDENTIFIER_REQUIRED);
        }
    }

}
