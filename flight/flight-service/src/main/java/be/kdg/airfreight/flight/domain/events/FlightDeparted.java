package be.kdg.airfreight.flight.domain.events;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;

// occurredOn is when the planner registered it, actualDeparture is the time the planner entered
public record FlightDeparted(
        FlightKey flightKey,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant actualDeparture,
        Instant occurredOn) {}
