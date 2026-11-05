package be.kdg.airfreight.cargo.domain.flight;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import java.util.Locale;

public record AirportCode(String value) {
    public AirportCode {
        if (value == null) {
            throw new InvalidCargoValueException("Airport code is required");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{3}")) {
            throw new InvalidCargoValueException("Airport code must be three letters: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
