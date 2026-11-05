package be.kdg.airfreight.flight.infrastructure.messaging.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// the old flight and the schedule the planning system should announce for the new one
public record FlightReplannedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        String newFlightNumber,
        LocalDate newScheduledDepartureDate,
        Instant newScheduledDeparture,
        Instant newScheduledArrival) {
    public static final int VERSION = 1;
}
