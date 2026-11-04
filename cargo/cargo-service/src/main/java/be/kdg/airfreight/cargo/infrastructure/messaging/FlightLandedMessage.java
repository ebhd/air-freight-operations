package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.LocalDate;

// the fields cargo reads from flights flight.landed json version 1, only the key
record FlightLandedMessage(
        Integer version,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode) {}
