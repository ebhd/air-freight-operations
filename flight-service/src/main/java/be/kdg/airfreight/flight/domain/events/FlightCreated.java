package be.kdg.airfreight.flight.domain.events;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;

public record FlightCreated(
        FlightKey flightKey,
        String arrivalAirportCode,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        Instant occurredOn) {}
