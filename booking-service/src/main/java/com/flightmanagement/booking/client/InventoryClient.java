package com.flightmanagement.booking.client;

import com.flightmanagement.booking.dto.InventoryConfirmRequest;
import com.flightmanagement.booking.dto.InventoryReservationRequest;
import com.flightmanagement.booking.dto.InventoryReservationResponse;
import com.flightmanagement.booking.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryClient {

    private final RestTemplate restTemplate;

    @Value("${flight-service.url}")
    private String flightServiceUrl;

    public InventoryReservationResponse reserveSeats(String bookingId, List<String> flightIds, 
                                                      int seats, int ttlMinutes) {
        String url = flightServiceUrl + "/v1/inventory/reserve";
        log.info("Reserving seats: bookingId={}, flights={}, seats={}", bookingId, flightIds, seats);

        try {
            InventoryReservationRequest request = InventoryReservationRequest.builder()
                    .bookingId(bookingId)
                    .flightIds(flightIds)
                    .seats(seats)
                    .ttlMinutes(ttlMinutes)
                    .build();

            ResponseEntity<InventoryReservationResponse> response =
                    restTemplate.postForEntity(url, request, InventoryReservationResponse.class);

            InventoryReservationResponse body = response.getBody();

            if (body != null && body.isSuccess()) {
                log.info("Reservation successful: bookingId={}", bookingId);
            } else if (body != null) {
                log.warn("Reservation failed: bookingId={}, reason={}", bookingId, body.getMessage());
            }

            return body;

        } catch (Exception e) {
            log.error("Reserve failed: bookingId={}, error={}", bookingId, e.getMessage());
            throw new ServiceUnavailableException("Flight service unavailable: " + e.getMessage());
        }
    }

    public boolean confirmReservation(String bookingId, List<String> flightIds, int seats) {
        String url = flightServiceUrl + "/v1/inventory/confirm";
        log.info("Confirming reservation: bookingId={}", bookingId);

        try {
            InventoryConfirmRequest request = InventoryConfirmRequest.builder()
                    .bookingId(bookingId)
                    .flightIds(flightIds)
                    .seats(seats)
                    .build();

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            boolean confirmed = response.getStatusCode().is2xxSuccessful();

            if (confirmed) {
                log.info("Reservation confirmed: bookingId={}", bookingId);
            } else {
                log.warn("Confirmation failed: bookingId={}", bookingId);
            }

            return confirmed;

        } catch (Exception e) {
            log.error("Confirm failed: bookingId={}, error={}", bookingId, e.getMessage());
            return false;
        }
    }

    public boolean releaseReservation(String bookingId, List<String> flightIds) {
        String url = flightServiceUrl + "/v1/inventory/release/" + bookingId
                + "?flightIds=" + String.join(",", flightIds);
        log.info("Releasing reservation: bookingId={}", bookingId);

        try {
            restTemplate.delete(url);
            log.info("Reservation released: bookingId={}", bookingId);
            return true;

        } catch (Exception e) {
            log.warn("Release failed: bookingId={}, error={}", bookingId, e.getMessage());
            return false;
        }
    }
}
