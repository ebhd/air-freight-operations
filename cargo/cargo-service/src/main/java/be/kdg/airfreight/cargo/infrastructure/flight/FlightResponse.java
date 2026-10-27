package be.kdg.airfreight.cargo.infrastructure.flight;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
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
        Instant scheduledArrival,
        Instant estimatedDeparture,
        Instant estimatedArrival) {
    FlightInformation toFlightInformation() {
        FlightKey key =
                new FlightKey(
                        flightNumber,
                        scheduledDepartureDate,
                        new AirportCode(departureAirportCode));
        // Temporary compatibility with Flight before US-15 is integrated.
        Instant etd = estimatedDeparture != null ? estimatedDeparture : scheduledDeparture;
        Instant eta = estimatedArrival != null ? estimatedArrival : scheduledArrival;
        return new FlightInformation(
                key,
                new AirportCode(arrivalAirportCode),
                FlightStatus.valueOf(status),
                scheduledDeparture,
                scheduledArrival,
                etd,
                eta);
    }
}
