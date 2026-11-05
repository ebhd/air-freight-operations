package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import java.math.BigDecimal;

public record Weight(BigDecimal value, WeightUnit unit) {
    public Weight {
        if (value == null) {
            throw new InvalidCargoValueException("Weight value is required");
        }
        if (unit == null) {
            throw new InvalidCargoValueException("Weight unit is required");
        }
        if (value.signum() <= 0) {
            throw new InvalidCargoValueException("Weight must be greater than zero");
        }
        // so 1250.50 equals 1250.5
        value = value.stripTrailingZeros();
        // keeps numbers like 1e999999999 out of the db and the logs
        if ((long) value.precision() - value.scale() > 9 || value.scale() > 3) {
            throw new InvalidCargoValueException(
                    "Weight must have at most 9 digits before and 3 after the decimal point");
        }
    }

    // 1E+3 becomes 1000 in the json
    public BigDecimal plainValue() {
        return new BigDecimal(value.toPlainString());
    }

    // the checks compare weights in kilograms so kg and lb can be added up
    public BigDecimal inKilograms() {
        return unit.toKilograms(value);
    }

    @Override
    public String toString() {
        return value.toPlainString() + " " + unit;
    }
}
