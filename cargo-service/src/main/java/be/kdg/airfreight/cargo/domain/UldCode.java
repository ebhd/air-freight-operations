package be.kdg.airfreight.cargo.domain;

import java.util.Locale;
import java.util.Objects;

public record UldCode(String value) {
    public UldCode {
        Objects.requireNonNull(value, "ULD code is required");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 32) {
            throw new IllegalArgumentException("ULD code must be 1 to 32 characters: " + value);
        }
    }
}
