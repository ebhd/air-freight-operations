package be.kdg.airfreight.cargo.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** A positive weight with its unit; the unit is never converted on input. */
public record Weight(BigDecimal value, WeightUnit unit) {
    public Weight {
        Objects.requireNonNull(value, "Weight value is required");
        Objects.requireNonNull(unit, "Weight unit is required");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero");
        }
        // Equal decimal values should stay equal regardless of trailing zeroes.
        value = value.stripTrailingZeros();
    }

    @Override
    public String toString() {
        return value.toPlainString() + " " + unit;
    }
}
