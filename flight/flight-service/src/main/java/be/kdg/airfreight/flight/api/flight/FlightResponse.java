package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.domain.flight.Flight;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// what flight exposes to other services times are utc instants
public record FlightResponse(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String status,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        Instant estimatedDeparture,
        Instant estimatedArrival,
        // null until the planner registers them
        Instant actualDeparture,
        Instant actualArrival,
        List<DelayResponse> departureDelays,
        List<DelayResponse> arrivalDelays) {
    static FlightResponse from(Flight flight) {
        return new FlightResponse(
                flight.key().flightNumber(),
                flight.key().scheduledDepartureDate(),
                flight.key().departureAirportCode(),
                flight.arrivalAirportCode(),
                flight.status().name(),
                flight.scheduledDeparture(),
                flight.scheduledArrival(),
                flight.estimatedDeparture(),
                flight.estimatedArrival(),
                flight.actualDeparture(),
                flight.actualArrival(),
                flight.departureDelays().stream().map(DelayResponse::from).toList(),
                flight.arrivalDelays().stream().map(DelayResponse::from).toList());
    }
}
