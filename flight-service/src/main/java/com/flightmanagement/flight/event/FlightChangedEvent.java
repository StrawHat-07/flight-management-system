package com.flightmanagement.flight.event;

import com.flightmanagement.flight.model.Flight;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a flight is created, updated, or cancelled.
 * Triggers graph rebuild and route recomputation.
 */
@Getter
public class FlightChangedEvent extends ApplicationEvent {

    private final Flight flight;
    private final ChangeType changeType;

    public FlightChangedEvent(Object source, Flight flight, ChangeType changeType) {
        super(source);
        this.flight = flight;
        this.changeType = changeType;
    }

    public enum ChangeType {
        CREATED,
        UPDATED,
        CANCELLED
    }
}
