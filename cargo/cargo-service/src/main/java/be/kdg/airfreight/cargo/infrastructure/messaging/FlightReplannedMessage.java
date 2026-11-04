package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.Instant;
import java.time.LocalDate;

// the fields cargo reads from flights flight.replanned json version 1: only the old flights key
record FlightReplannedMessage(
        Integer version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode) {}
