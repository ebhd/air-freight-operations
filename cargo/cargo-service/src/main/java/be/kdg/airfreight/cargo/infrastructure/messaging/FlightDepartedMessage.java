package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.LocalDate;

// the fields cargo reads from the flight.departed json version 1, only the key
// jackson 3 ignores the other fields, the message id comes from the amqp properties
record FlightDepartedMessage(
        Integer version,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode) {}
