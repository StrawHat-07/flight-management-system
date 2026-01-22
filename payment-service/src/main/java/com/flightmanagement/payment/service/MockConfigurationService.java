package com.flightmanagement.payment.service;

import com.flightmanagement.payment.dto.MockConfiguration;
import com.flightmanagement.payment.enums.PaymentStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for managing mock payment configuration at runtime.
 * Allows tests to dynamically control payment behavior.
 */
@Service
@Slf4j
public class MockConfigurationService {

    private final AtomicReference<MockConfiguration> config = new AtomicReference<>();
    private final Random random = new Random();

    @Getter
    private final int defaultSuccessProbability;
    @Getter
    private final int defaultFailureProbability;
    @Getter
    private final int defaultMinDelayMs;
    @Getter
    private final int defaultMaxDelayMs;

    public MockConfigurationService(
            @Value("${payment.success-probability:70}") int successProbability,
            @Value("${payment.failure-probability:20}") int failureProbability,
            @Value("${payment.min-processing-delay-ms:1000}") int minDelayMs,
            @Value("${payment.max-processing-delay-ms:5000}") int maxDelayMs) {
        this.defaultSuccessProbability = successProbability;
        this.defaultFailureProbability = failureProbability;
        this.defaultMinDelayMs = minDelayMs;
        this.defaultMaxDelayMs = maxDelayMs;
    }

    /**
     * Updates mock configuration. Pass null values to keep defaults.
     */
    public MockConfiguration updateConfiguration(MockConfiguration newConfig) {
        MockConfiguration current = config.get();
        MockConfiguration merged = MockConfiguration.builder()
                .forcedOutcome(newConfig.getForcedOutcome())
                .successProbability(newConfig.getSuccessProbability() != null 
                        ? newConfig.getSuccessProbability() : defaultSuccessProbability)
                .failureProbability(newConfig.getFailureProbability() != null 
                        ? newConfig.getFailureProbability() : defaultFailureProbability)
                .minDelayMs(newConfig.getMinDelayMs() != null 
                        ? newConfig.getMinDelayMs() : defaultMinDelayMs)
                .maxDelayMs(newConfig.getMaxDelayMs() != null 
                        ? newConfig.getMaxDelayMs() : defaultMaxDelayMs)
                .skipDelay(newConfig.getSkipDelay() != null 
                        ? newConfig.getSkipDelay() : false)
                .build();

        config.set(merged);
        log.info("Mock configuration updated: {}", merged);
        return merged;
    }

    /**
     * Gets current configuration (with defaults applied).
     */
    public MockConfiguration getConfiguration() {
        MockConfiguration current = config.get();
        if (current == null) {
            return MockConfiguration.builder()
                    .forcedOutcome(null)
                    .successProbability(defaultSuccessProbability)
                    .failureProbability(defaultFailureProbability)
                    .minDelayMs(defaultMinDelayMs)
                    .maxDelayMs(defaultMaxDelayMs)
                    .skipDelay(false)
                    .build();
        }
        return current;
    }

    /**
     * Resets configuration to defaults.
     */
    public MockConfiguration reset() {
        config.set(null);
        log.info("Mock configuration reset to defaults");
        return getConfiguration();
    }

    /**
     * Determines payment outcome based on current configuration.
     */
    public PaymentStatus determineOutcome() {
        MockConfiguration cfg = getConfiguration();

        // Check for forced outcome
        if (cfg.getForcedOutcome() != null) {
            try {
                return PaymentStatus.valueOf(cfg.getForcedOutcome().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid forced outcome: {}", cfg.getForcedOutcome());
            }
        }

        // Probability-based outcome
        int roll = random.nextInt(100);
        int successProb = cfg.getSuccessProbability() != null ? cfg.getSuccessProbability() : defaultSuccessProbability;
        int failureProb = cfg.getFailureProbability() != null ? cfg.getFailureProbability() : defaultFailureProbability;

        if (roll < successProb) {
            return PaymentStatus.SUCCESS;
        } else if (roll < successProb + failureProb) {
            return PaymentStatus.FAILURE;
        } else {
            return PaymentStatus.TIMEOUT;
        }
    }

    /**
     * Gets the processing delay based on current configuration.
     */
    public int getProcessingDelay() {
        MockConfiguration cfg = getConfiguration();

        if (Boolean.TRUE.equals(cfg.getSkipDelay())) {
            return 0;
        }

        int min = cfg.getMinDelayMs() != null ? cfg.getMinDelayMs() : defaultMinDelayMs;
        int max = cfg.getMaxDelayMs() != null ? cfg.getMaxDelayMs() : defaultMaxDelayMs;

        if (max <= min) {
            return min;
        }

        return random.nextInt(max - min) + min;
    }
}
