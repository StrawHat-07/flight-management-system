package com.flightmanagement.flight.util;

import java.util.UUID;

public final class IdGenerator {

    private static final String FLIGHT_ID_PREFIX = "FL";
    private static final int UUID_SUBSTRING_LENGTH = 8;

    private IdGenerator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String generateFlightId() {
        return FLIGHT_ID_PREFIX + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, UUID_SUBSTRING_LENGTH)
                .toUpperCase();
    }

    public static String generateComputedFlightId(String source, String destination, Iterable<String> flightIds) {
        return "CF_" + source + "_" + destination + "_" + String.join("_", flightIds);
    }

    public static boolean isComputedFlightId(String identifier) {
        return identifier != null && identifier.startsWith("CF_");
    }

    public static boolean isDirectFlightId(String identifier) {
        return identifier != null && identifier.startsWith(FLIGHT_ID_PREFIX) && !identifier.startsWith("CF_");
    }
}
