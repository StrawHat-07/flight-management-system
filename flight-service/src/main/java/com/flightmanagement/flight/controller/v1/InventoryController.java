package com.flightmanagement.flight.controller.v1;

import com.flightmanagement.flight.dto.InventoryConfirmRequest;
import com.flightmanagement.flight.dto.InventoryReservationRequest;
import com.flightmanagement.flight.dto.InventoryReservationResponse;
import com.flightmanagement.flight.service.InventoryReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Inventory management controller.
 * 
 * Exposes APIs for seat reservations (CLAIM → CONFIRM → RELEASE).
 * This is the ONLY way external services can modify inventory.
 * 
 * DB-First Architecture:
 * - CLAIM: Decrements DB seats, creates reservation record
 * - CONFIRM: Deletes reservation record (seats stay decremented)
 * - RELEASE: Restores DB seats, deletes reservation record
 * - Background threads handles expired reservations
 */
@RestController
@RequestMapping("/v1/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryReservationService reservationService;

    @PostMapping("/reserve")
    public ResponseEntity<InventoryReservationResponse> reserveSeats(
            @Valid @RequestBody InventoryReservationRequest request) {
        
        log.info("Received reservation request: bookingId={}, flights={}, seats={}", 
                request.getBookingId(), request.getFlightIds(), request.getSeats());

        InventoryReservationResponse response = reservationService.reserveSeats(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            HttpStatus status = switch (response.getErrorCode()) {
                case "NO_SEATS_AVAILABLE", "ALREADY_RESERVED" -> HttpStatus.CONFLICT;
                case "INVALID_INPUT" -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            
            return ResponseEntity.status(status).body(response);
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirmReservation(
            @Valid @RequestBody InventoryConfirmRequest request) {

        log.info("Confirming reservation: bookingId={}, flights={}, seats={}",
                request.getBookingId(), request.getFlightIds(), request.getSeats());

        boolean confirmed = reservationService.confirmReservation(
                request.getBookingId(), request.getFlightIds(), request.getSeats());

        if (confirmed) {
            return ResponseEntity.ok(Map.of(
                    "status", "confirmed",
                    "message", "Reservation confirmed"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                    "status", "expired",
                    "message", "Reservation expired or not found"
            ));
        }
    }

    @DeleteMapping("/release/{bookingId}")
    public ResponseEntity<Map<String, String>> releaseReservation(
            @PathVariable String bookingId,
            @RequestParam List<String> flightIds) {
        
        log.info("Releasing reservation: bookingId={}, flights={}", bookingId, flightIds);

        boolean released = reservationService.releaseReservation(bookingId, flightIds);

        if (released) {
            return ResponseEntity.ok(Map.of(
                    "status", "released",
                    "message", "Reservation released successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
    }
}
