package be.kdg.airfreight.flight.domain.flight;

import java.time.LocalDate;
import java.util.Objects;

public record FlightKey(
        String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode) {
    public FlightKey {
        flightNumber = requireText(flightNumber, "flight number");
        departureAirportCode = requireAirportCode(departureAirportCode, "departure airport code");
        Objects.requireNonNull(scheduledDepartureDate, "scheduled departure date must not be null");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidScheduleException(name + " must not be blank");
        }
        return value.trim();
    }

    static String requireAirportCode(String value, String name) {
        String code = requireText(value, name).toUpperCase();
        if (!code.matches("[A-Z]{3}")) {
            throw new InvalidScheduleException(name + " must be a three-letter IATA code");
        }
        return code;
    }

    // error texts and logs show this, the same form as in cargo
    @Override
    public String toString() {
        return flightNumber + "/" + scheduledDepartureDate + "/" + departureAirportCode;
    }
}
