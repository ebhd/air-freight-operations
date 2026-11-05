package be.kdg.airfreight.flight.domain.events;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.replanning.Replanning;
import java.time.Instant;

// the old flight ended and the planning system is asked for the new schedule
public record FlightReplanned(
        FlightKey flightKey,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        Replanning replanning,
        Instant occurredOn) {}
