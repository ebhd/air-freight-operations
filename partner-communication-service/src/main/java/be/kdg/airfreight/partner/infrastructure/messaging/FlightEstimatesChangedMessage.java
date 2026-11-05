package be.kdg.airfreight.partner.infrastructure.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FlightEstimatesChangedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant previousEstimatedDeparture,
        Instant previousEstimatedArrival,
        Instant estimatedDeparture,
        Instant estimatedArrival) {
    public static final int VERSION = 1;
}
