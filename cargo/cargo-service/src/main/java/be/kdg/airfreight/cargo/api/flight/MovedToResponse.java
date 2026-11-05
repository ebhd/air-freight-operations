package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.time.LocalDate;

// the key of the new flight the cargo of a replanned flight moved to
public record MovedToResponse(
        String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode) {
    static MovedToResponse from(FlightKey flightKey) {
        return new MovedToResponse(
                flightKey.flightNumber(),
                flightKey.scheduledDepartureDate(),
                flightKey.departureAirportCode().value());
    }
}
