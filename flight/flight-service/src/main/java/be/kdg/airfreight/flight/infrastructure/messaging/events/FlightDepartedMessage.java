package be.kdg.airfreight.flight.infrastructure.messaging.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// the json of flight.departed version 1, only what the formatter and cargo need
public record FlightDepartedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant actualDeparture) {
    public static final int VERSION = 1;
}
