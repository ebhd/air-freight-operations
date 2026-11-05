package be.kdg.airfreight.cargo.infrastructure.flight;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;

// flight's json translated to the cargo model
record FlightResponse(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String status,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        Instant estimatedDeparture,
        Instant estimatedArrival) {
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
                scheduledArrival,
                estimatedDeparture,
                estimatedArrival);
    }
}
