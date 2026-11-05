package be.kdg.airfreight.flight.domain.events;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;

// the flight passed its retention and is gone, so cargo can remove what it holds about it
public record FlightRemoved(FlightKey flightKey, Instant occurredOn) {}
