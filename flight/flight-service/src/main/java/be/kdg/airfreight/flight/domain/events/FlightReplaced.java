package be.kdg.airfreight.flight.domain.events;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;

// the new flight of a replanning exists now, so cargo can move and partners can be told
public record FlightReplaced(
        FlightKey flightKey,
        FlightKey newFlightKey,
        Instant newScheduledDeparture,
        Instant newScheduledArrival,
        Instant occurredOn) {}
