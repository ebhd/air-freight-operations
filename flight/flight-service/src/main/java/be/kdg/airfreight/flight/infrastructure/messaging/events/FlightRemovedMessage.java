package be.kdg.airfreight.flight.infrastructure.messaging.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// the key of the removed flight, the only thing cargo needs to find its own data
public record FlightRemovedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode) {
    public static final int VERSION = 1;
}
