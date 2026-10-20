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
        // keeps numbers like 1e999999999 out of the db and the logs
        if ((long) value.precision() - value.scale() > 9 || value.scale() > 3) {
            throw new IllegalArgumentException(
                    "Weight must have at most 9 digits before and 3 after the decimal point");
        }
    }

    @Override
    public String toString() {
        return value.toPlainString() + " " + unit;
    }
}
