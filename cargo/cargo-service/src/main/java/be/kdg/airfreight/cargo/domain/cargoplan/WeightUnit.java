package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import java.math.BigDecimal;
import java.util.Locale;

// the weight units we accept
public enum WeightUnit {
    KG(BigDecimal.ONE),
    // 1 lb is exactly 0.45359237 kg by the international definition
    LB(new BigDecimal("0.45359237"));

    private final BigDecimal kilogramsPerUnit;

    WeightUnit(BigDecimal kilogramsPerUnit) {
        this.kilogramsPerUnit = kilogramsPerUnit;
    }

    // exact so a comparison never depends on rounding
    BigDecimal toKilograms(BigDecimal value) {
        return value.multiply(kilogramsPerUnit);
    }

    public static WeightUnit parse(String unit) {
        if (unit == null) {
            throw new InvalidCargoValueException("Weight unit is required");
        }
        try {
            return valueOf(unit.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new InvalidCargoValueException("Unsupported weight unit: " + unit);
        }
    }
}
