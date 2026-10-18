package be.kdg.airfreight.cargo.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Uses the UTC date of scheduled departure; delays do not change this identity. */
public record FlightKey(
        String flightNumber, LocalDate scheduledDepartureDate, AirportCode departureAirportCode) {
    public FlightKey {
        Objects.requireNonNull(flightNumber, "Flight number is required");
        Objects.requireNonNull(scheduledDepartureDate, "Scheduled departure date is required");
        Objects.requireNonNull(departureAirportCode, "Departure airport code is required");
        flightNumber = flightNumber.trim();
        // same max length as flights column and no characters that break the lookup url
        if (!flightNumber.matches("[A-Za-z0-9]{1,32}")) {
            throw new IllegalArgumentException(
                    "Flight number must be 1 to 32 letters or digits: " + flightNumber);
        }
    }

    // HJ221/2026-10-19/BRU in logs and rejection reasons
    @Override
    public String toString() {
        return flightNumber + "/" + scheduledDepartureDate + "/" + departureAirportCode;
    }
}
