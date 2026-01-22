package com.flightmanagement.booking.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotFound(BookingNotFoundException ex) {
        log.warn("Booking not found: {}", ex.getBookingId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("BOOKING_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ErrorResponse> handleBookingException(BookingException ex) {
        log.warn("Booking error: code={}, message={}", ex.getErrorCode(), ex.getMessage());

        HttpStatus status = switch (ex.getErrorCode()) {
            case "NO_SEATS_AVAILABLE" -> HttpStatus.CONFLICT;
            case "INVALID_FLIGHT", "INVALID_REQUEST", "INVALID_USER_ID",
                 "INVALID_FLIGHT_ID", "INVALID_SEATS", "INVALID_BOOKING_ID" -> HttpStatus.BAD_REQUEST;
            case "PRICE_UNAVAILABLE" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("SERVICE_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation errors: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Invalid request", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }


    public record ErrorResponse(
            String error,
            String message,
            Map<String, String> details,
            LocalDateTime timestamp
    ) {
        public static ErrorResponse of(String error, String message) {
            return new ErrorResponse(error, message, null, LocalDateTime.now());
        }

        public static ErrorResponse of(String error, String message, Map<String, String> details) {
            return new ErrorResponse(error, message, details, LocalDateTime.now());
        }
    }
}
