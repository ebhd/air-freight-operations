package be.kdg.airfreight.cargo.api;

import be.kdg.airfreight.cargo.domain.FlightInformation;
import java.time.Instant;
import java.time.LocalDate;

public record FlightOverviewResponse(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String status,
        Instant scheduledDeparture,
        Instant scheduledArrival) {
    static FlightOverviewResponse from(FlightInformation flight) {
        return new FlightOverviewResponse(
                flight.flightKey().flightNumber(),
                flight.flightKey().scheduledDepartureDate(),
                flight.flightKey().departureAirportCode().value(),
                flight.arrivalAirportCode().value(),
                flight.status().name(),
                flight.scheduledDeparture(),
                flight.scheduledArrival());
    }
}
