package com.flightmanagement.flight.util;

/**
 * Domain-specific string utilities.
 * For general string operations, prefer org.springframework.util.StringUtils
 */
public final class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String normalizeLocation(String location) {
        if (!org.springframework.util.StringUtils.hasText(location)) {
            return null;
        }
        return location.trim().toUpperCase();
    }
}
