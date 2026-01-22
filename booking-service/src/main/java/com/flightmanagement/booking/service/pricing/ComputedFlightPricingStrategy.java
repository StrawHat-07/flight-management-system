package com.flightmanagement.booking.service.pricing;

import com.flightmanagement.booking.client.FlightServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class ComputedFlightPricingStrategy implements PricingStrategy {

    private static final String COMPUTED_FLIGHT_PREFIX = "CF_";
    
    private final FlightServiceClient flightServiceClient;

    @Override
    public boolean supports(String flightIdentifier) {
        return flightIdentifier.startsWith(COMPUTED_FLIGHT_PREFIX);
    }

    @Override
    public BigDecimal calculatePrice(String flightIdentifier, int seats) {
        return flightServiceClient.getComputedFlightById(flightIdentifier)
                .map(computed -> computed.getTotalPrice().multiply(BigDecimal.valueOf(seats)))
                .orElseGet(() -> {
                    log.warn("Computed flight not found: {}", flightIdentifier);
                    return BigDecimal.ZERO;
                });
    }
}
