package be.kdg.airfreight.flight.domain.events;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;

public record FlightLanded(
        FlightKey flightKey,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant actualArrival,
        Instant occurredOn) {}
