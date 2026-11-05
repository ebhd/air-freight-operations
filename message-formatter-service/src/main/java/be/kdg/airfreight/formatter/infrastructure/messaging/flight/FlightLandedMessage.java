package be.kdg.airfreight.formatter.infrastructure.messaging.flight;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// flights flight.landed json version 1
public record FlightLandedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant actualArrival) {
    public static final int VERSION = 1;
}
