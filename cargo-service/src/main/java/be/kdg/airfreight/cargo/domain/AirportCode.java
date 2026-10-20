package be.kdg.airfreight.cargo.domain;

import java.util.Locale;
import java.util.Objects;

public record AirportCode(String value) {
    public AirportCode {
        Objects.requireNonNull(value, "Airport code is required");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Airport code must be three letters: " + value);
        }
    }

    // keeps logs short
    @Override
    public String toString() {
        return value;
    }
}
