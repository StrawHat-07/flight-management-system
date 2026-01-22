package com.flightmanagement.booking.service;

import com.flightmanagement.booking.service.pricing.PricingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class PricingService {

    private final List<PricingStrategy> strategies;

    public PricingService(List<PricingStrategy> strategies) {
        this.strategies = strategies;
    }

    public BigDecimal calculateTotalPrice(String flightIdentifier, int seats) {
        log.debug("Calculating price: flight={}, seats={}", flightIdentifier, seats);

        return strategies.stream()
                .filter(strategy -> strategy.supports(flightIdentifier))
                .findFirst()
                .map(strategy -> strategy.calculatePrice(flightIdentifier, seats))
                .orElse(BigDecimal.ZERO);
    }
}
