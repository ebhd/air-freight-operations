package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.LocalDate;

// the fields cargo reads from flights flight.removed json version 1: the key
record FlightRemovedMessage(
        Integer version,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode) {}
