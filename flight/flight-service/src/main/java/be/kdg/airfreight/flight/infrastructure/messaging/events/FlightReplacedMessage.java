package be.kdg.airfreight.flight.infrastructure.messaging.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// the old flight and the new flight that now really exists, with its schedule
public record FlightReplacedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String newFlightNumber,
        LocalDate newScheduledDepartureDate,
        String newDepartureAirportCode,
        Instant newScheduledDeparture,
        Instant newScheduledArrival) {
    public static final int VERSION = 1;
}
