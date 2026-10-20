package be.kdg.airfreight.cargo.infrastructure.flight;

import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;

// flights json translated to the cargo model
record FlightResponse(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String status,
        Instant scheduledDeparture,
        Instant scheduledArrival) {
    FlightInformation toFlightInformation() {
        FlightKey key =
                new FlightKey(
                        flightNumber,
                        scheduledDepartureDate,
                        new AirportCode(departureAirportCode));
        return new FlightInformation(
                key,
                new AirportCode(arrivalAirportCode),
                FlightStatus.valueOf(status),
                scheduledDeparture,
                scheduledArrival);
    }
}
