package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.LocalDate;

// the fields cargo reads from the flight events that only need the key, json version 1:
// flight.departed, flight.landed, flight.replanned and flight.removed
record FlightKeyMessage(
        Integer version,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode) {}
