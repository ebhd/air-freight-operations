package be.kdg.airfreight.cargo.infrastructure.flight;

import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightKey;
import java.time.LocalDate;

// only the fields cargo needs the rest of flights json is ignored
record FlightResponse(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String status) {
    private static final String PLANNED = "PLANNED";

    FlightInformation toFlightInformation() {
        FlightKey key =
                new FlightKey(
                        flightNumber,
                        scheduledDepartureDate,
                        new AirportCode(departureAirportCode));
        // cargo only cares whether the flight is still planned
        return new FlightInformation(key, PLANNED.equals(status));
    }
}
