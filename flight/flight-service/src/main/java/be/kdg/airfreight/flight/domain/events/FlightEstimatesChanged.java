package be.kdg.airfreight.flight.domain.events;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;

public record FlightEstimatesChanged(
        FlightKey flightKey,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant previousEstimatedDeparture,
        Instant previousEstimatedArrival,
        Instant estimatedDeparture,
        Instant estimatedArrival,
        Instant occurredOn) {}
