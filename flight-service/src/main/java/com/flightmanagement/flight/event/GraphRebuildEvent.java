package com.flightmanagement.flight.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when the flight graph has been rebuilt.
 * Triggers route precomputation.
 */
@Getter
public class GraphRebuildEvent extends ApplicationEvent {

    private final int locationCount;
    private final int flightCount;

    public GraphRebuildEvent(Object source, int locationCount, int flightCount) {
        super(source);
        this.locationCount = locationCount;
        this.flightCount = flightCount;
    }
}
