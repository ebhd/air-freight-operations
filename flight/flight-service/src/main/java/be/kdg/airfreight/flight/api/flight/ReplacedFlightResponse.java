package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.LocalDate;

// the key of the replanned flight this flight replaces
public record ReplacedFlightResponse(
        String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode) {
    static ReplacedFlightResponse from(FlightKey key) {
        return new ReplacedFlightResponse(
                key.flightNumber(), key.scheduledDepartureDate(), key.departureAirportCode());
    }
}
