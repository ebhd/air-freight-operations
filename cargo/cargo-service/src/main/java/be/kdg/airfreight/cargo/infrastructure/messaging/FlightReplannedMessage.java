package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.LocalDate;

// the fields cargo reads from the flight.replanned json version 1: only the old flight's key
record FlightReplannedMessage(
        Integer version,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode) {}
