package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.domain.flight.Flight;
import java.time.Instant;
import java.time.LocalDate;

public record FlightOverviewResponse(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String status,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        Instant estimatedDeparture,
        Instant estimatedArrival) {
    static FlightOverviewResponse from(Flight flight) {
        return new FlightOverviewResponse(
                flight.key().flightNumber(),
                flight.key().scheduledDepartureDate(),
                flight.key().departureAirportCode(),
                flight.arrivalAirportCode(),
                flight.status().name(),
                flight.scheduledDeparture(),
                flight.scheduledArrival(),
                flight.estimatedDeparture(),
                flight.estimatedArrival());
    }
}
