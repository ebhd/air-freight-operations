package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// flights flight.estimates-changed json version 1, every field so an unused one never breaks
// parsing
record FlightEstimatesChangedMessage(
        UUID messageId,
        Integer version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant previousEstimatedDeparture,
        Instant previousEstimatedArrival,
        Instant estimatedDeparture,
        Instant estimatedArrival) {}
