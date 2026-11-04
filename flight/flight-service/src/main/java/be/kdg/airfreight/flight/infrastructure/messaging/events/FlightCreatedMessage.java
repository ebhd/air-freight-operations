package be.kdg.airfreight.flight.infrastructure.messaging.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FlightCreatedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        Instant scheduledDeparture,
        Instant scheduledArrival) {
    public static final int VERSION = 1;
}
