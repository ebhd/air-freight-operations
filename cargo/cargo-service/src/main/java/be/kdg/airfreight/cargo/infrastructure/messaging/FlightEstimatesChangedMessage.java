package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.time.Instant;
import java.time.LocalDate;

// the fields cargo reads from the flight.estimates-changed json version 1
// jackson 3 ignores the other fields, the message id comes from the amqp properties
record FlightEstimatesChangedMessage(
        Integer version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        Instant estimatedDeparture,
        Instant estimatedArrival) {}
