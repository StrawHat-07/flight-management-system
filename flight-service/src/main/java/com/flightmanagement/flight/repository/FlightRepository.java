package com.flightmanagement.flight.repository;

import com.flightmanagement.flight.enums.FlightStatus;
import com.flightmanagement.flight.model.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long>, JpaSpecificationExecutor<Flight> {

    Optional<Flight> findByFlightId(String flightId);

    boolean existsByFlightId(String flightId);

    List<Flight> findBySourceAndDestinationAndStatus(String source, String destination, FlightStatus status);

    List<Flight> findBySourceAndStatus(String source, FlightStatus status);

    List<Flight> findByStatus(FlightStatus status);

    @Query("SELECT f FROM Flight f WHERE f.departureTime >= :startTime AND f.departureTime <= :endTime AND f.status = :status")
    List<Flight> findByDepartureTimeBetweenAndStatus(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("status") FlightStatus status);

    @Query("SELECT DISTINCT f.source FROM Flight f WHERE f.status = 'ACTIVE' " +
            "UNION SELECT DISTINCT f.destination FROM Flight f WHERE f.status = 'ACTIVE'")
    List<String> findAllActiveLocations();

    @Modifying
    @Query("UPDATE Flight f SET f.availableSeats = f.availableSeats - :seats WHERE f.flightId = :flightId AND f.availableSeats >= :seats")
    int decrementAvailableSeats(@Param("flightId") String flightId, @Param("seats") int seats);

    @Modifying
    @Query("UPDATE Flight f SET f.availableSeats = f.availableSeats + :seats WHERE f.flightId = :flightId")
    int incrementAvailableSeats(@Param("flightId") String flightId, @Param("seats") int seats);
}
