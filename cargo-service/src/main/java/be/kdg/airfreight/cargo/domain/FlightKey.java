package be.kdg.airfreight.cargo.domain;

import java.time.LocalDate;

// utc departure date so a delay never changes the key
public record FlightKey(
        String flightNumber, LocalDate scheduledDepartureDate, AirportCode departureAirportCode) {
    public FlightKey {
        if (flightNumber == null) {
            throw new InvalidCargoValueException("Flight number is required");
        }
        if (scheduledDepartureDate == null) {
            throw new InvalidCargoValueException("Scheduled departure date is required");
        }
        if (departureAirportCode == null) {
            throw new InvalidCargoValueException("Departure airport code is required");
        }
        flightNumber = flightNumber.trim();
        // same max length as flights column and no characters that break the lookup url
        if (!flightNumber.matches("[A-Za-z0-9]{1,32}")) {
            throw new InvalidCargoValueException(
                    "Flight number must be 1 to 32 letters or digits: " + flightNumber);
        }
    }

    // HJ221/2026-10-19/BRU in logs and rejection reasons
    @Override
    public String toString() {
        return flightNumber + "/" + scheduledDepartureDate + "/" + departureAirportCode;
    }
}
