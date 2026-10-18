package be.kdg.airfreight.flight.api;

import be.kdg.airfreight.flight.domain.Flight;
import java.time.Instant;
import java.time.LocalDate;

// what flight exposes to other services times are utc instants
public record FlightResponse(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String status,
        Instant scheduledDeparture,
        Instant scheduledArrival) {
    static FlightResponse from(Flight flight) {
        return new FlightResponse(
                flight.key().flightNumber(),
                flight.key().scheduledDepartureDate(),
                flight.key().departureAirportCode(),
                flight.arrivalAirportCode(),
                flight.status().name(),
                flight.scheduledDeparture(),
                flight.scheduledArrival());
    }
}
