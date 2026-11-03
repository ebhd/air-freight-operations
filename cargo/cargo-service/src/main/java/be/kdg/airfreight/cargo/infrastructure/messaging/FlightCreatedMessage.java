package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// flights flight.created json version 1, every field so an unused one never breaks parsing
record FlightCreatedMessage(
        UUID messageId,
        Integer version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        Instant scheduledDeparture,
        Instant scheduledArrival) {}
