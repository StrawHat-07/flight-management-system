package com.flightmanagement.booking.service.pricing;

import java.math.BigDecimal;

public interface PricingStrategy {
    
    boolean supports(String flightIdentifier);
    
    BigDecimal calculatePrice(String flightIdentifier, int seats);
}
