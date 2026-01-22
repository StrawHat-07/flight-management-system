package com.flightmanagement.payment.service.outcome;

import com.flightmanagement.payment.constants.PaymentConstants;
import com.flightmanagement.payment.enums.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Random outcome strategy based on configured probabilities.
 * Default mock behavior for testing various scenarios.
 */
@Component
@Slf4j
public class RandomOutcomeStrategy implements OutcomeStrategy {

    private final int successProbability;
    private final int failureProbability;
    private final Random random = new Random();

    public RandomOutcomeStrategy(
            @Value("${payment.success-probability:" + PaymentConstants.DEFAULT_SUCCESS_PROBABILITY + "}") int successProbability,
            @Value("${payment.failure-probability:" + PaymentConstants.DEFAULT_FAILURE_PROBABILITY + "}") int failureProbability) {
        this.successProbability = successProbability;
        this.failureProbability = failureProbability;
        log.info("Initialized RandomOutcomeStrategy: success={}%, failure={}%, timeout={}%",
                successProbability, failureProbability, 100 - successProbability - failureProbability);
    }

    @Override
    public PaymentStatus determine() {
        int roll = random.nextInt(100);

        if (roll < successProbability) {
            return PaymentStatus.SUCCESS;
        } else if (roll < successProbability + failureProbability) {
            return PaymentStatus.FAILURE;
        } else {
            return PaymentStatus.TIMEOUT;
        }
    }

    @Override
    public String getMessage(PaymentStatus status) {
        return switch (status) {
            case SUCCESS -> PaymentConstants.MESSAGE_SUCCESS;
            case FAILURE -> PaymentConstants.MESSAGE_FAILURE;
            case TIMEOUT -> PaymentConstants.MESSAGE_TIMEOUT;
        };
    }
}
