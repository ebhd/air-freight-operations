package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.LocalDate;

// the fields cargo reads from flights flight.replaced json version 1: both keys
record FlightReplacedMessage(
        Integer version,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String newFlightNumber,
        LocalDate newScheduledDepartureDate,
        String newDepartureAirportCode) {}
