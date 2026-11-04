package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.Instant;
import java.time.LocalDate;

// the fields cargo reads from flights flight.created json version 1
// jackson 3 ignores the other fields, the message id comes from the amqp properties
record FlightCreatedMessage(
        Integer version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        Instant scheduledDeparture,
        Instant scheduledArrival) {}
