package com.flightmanagement.payment.constants;

public final class PaymentConstants {

    private PaymentConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    public static final String PAYMENT_ID_PREFIX = "PAY";
    
    public static final int DEFAULT_SUCCESS_PROBABILITY = 70;
    public static final int DEFAULT_FAILURE_PROBABILITY = 20;
    
    public static final int DEFAULT_MIN_PROCESSING_DELAY_MS = 1000;
    public static final int DEFAULT_MAX_PROCESSING_DELAY_MS = 5000;
    
    public static final int SYNC_PROCESSING_MIN_DELAY_MS = 500;
    public static final int SYNC_PROCESSING_MAX_DELAY_MS = 1500;
    
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    
    public static final String MESSAGE_SUCCESS = "Payment processed successfully";
    public static final String MESSAGE_FAILURE = "Payment declined by issuing bank";
    public static final String MESSAGE_TIMEOUT = "Payment processing timed out";
}
