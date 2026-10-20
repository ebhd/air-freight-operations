package be.kdg.airfreight.cargo.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Weight(BigDecimal value, WeightUnit unit) {
    public Weight {
        Objects.requireNonNull(value, "Weight value is required");
        Objects.requireNonNull(unit, "Weight unit is required");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero");
        }
        // so 1250.50 equals 1250.5
        value = value.stripTrailingZeros();
    }

    @Override
    public String toString() {
        return value.toPlainString() + " " + unit;
    }
}
