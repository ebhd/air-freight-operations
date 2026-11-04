package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import java.util.Locale;

public record UldCode(String value) {
    public UldCode {
        if (value == null) {
            throw new InvalidCargoValueException("ULD code is required");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 32) {
            throw new InvalidCargoValueException("ULD code must be 1 to 32 characters: " + value);
        }
    }
}
