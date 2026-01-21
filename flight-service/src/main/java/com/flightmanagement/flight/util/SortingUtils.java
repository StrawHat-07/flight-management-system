package com.flightmanagement.flight.util;

import com.flightmanagement.flight.dto.SearchResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Sorting utilities for search results.
 * Encapsulates sorting strategy to follow Open/Closed Principle.
 */
@Slf4j
public final class SortingUtils {

    private static final String DEFAULT_SORT_FIELD = "price";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "price", "durationMinutes", "departureTime", "hops"
    );

    private SortingUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void sortSearchResults(List<SearchResponse> results, String sortBy, String sortDirection) {
        String field = validateAndNormalizeSortField(sortBy);
        boolean ascending = !"desc".equalsIgnoreCase(sortDirection);

        Comparator<SearchResponse> comparator = createSearchResponseComparator(field);
        if (!ascending) {
            comparator = comparator.reversed();
        }

        results.sort(comparator);
    }

    private static String validateAndNormalizeSortField(String sortBy) {
        String field = org.springframework.util.StringUtils.hasText(sortBy) ? sortBy : DEFAULT_SORT_FIELD;

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            log.warn("Invalid sort field '{}', defaulting to '{}'", field, DEFAULT_SORT_FIELD);
            return DEFAULT_SORT_FIELD;
        }

        return field;
    }

    private static Comparator<SearchResponse> createSearchResponseComparator(String field) {
        return switch (field) {
            case "durationMinutes" -> Comparator.comparing(
                    SearchResponse::getDurationMinutes,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "departureTime" -> Comparator.comparing(
                    SearchResponse::getDepartureTime,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "hops" -> Comparator.comparing(
                    SearchResponse::getHops,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> Comparator.comparing(
                    SearchResponse::getPrice,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
    }

    public static Set<String> getAllowedSortFields() {
        return Set.copyOf(ALLOWED_SORT_FIELDS);
    }
}
