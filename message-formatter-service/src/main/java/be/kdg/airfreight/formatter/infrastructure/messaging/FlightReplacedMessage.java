package be.kdg.airfreight.formatter.infrastructure.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// our copy of flight's flight.replaced version 1
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
