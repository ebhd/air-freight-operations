package be.kdg.airfreight.flight.domain.flight;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

public record FlightKey(
        String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode) {
    public FlightKey {
        flightNumber = requireFlightNumber(flightNumber);
        departureAirportCode = requireAirportCode(departureAirportCode, "departure airport code");
        Objects.requireNonNull(scheduledDepartureDate, "scheduled departure date must not be null");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidScheduleException(name + " must not be blank");
        }
        return value.trim();
    }

    static String requireFlightNumber(String value) {
        String number = requireText(value, "flight number");
        if (!number.matches("[A-Za-z0-9]{1,32}")) {
            throw new InvalidScheduleException("flight number must be 1 to 32 letters or digits");
        }
        return number;
    }

    static String requireAirportCode(String value, String name) {
        String code = requireText(value, name).toUpperCase(Locale.ROOT);
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
