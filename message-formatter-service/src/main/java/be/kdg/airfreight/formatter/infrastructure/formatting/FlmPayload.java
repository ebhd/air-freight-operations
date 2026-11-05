package be.kdg.airfreight.formatter.infrastructure.formatting;

import java.time.Instant;
import java.time.LocalDate;

record FlmPayload(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        Instant scheduledDeparture,
        Instant scheduledArrival) {}
