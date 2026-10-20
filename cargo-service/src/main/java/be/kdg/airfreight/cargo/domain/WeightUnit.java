package be.kdg.airfreight.cargo.domain;

import java.util.Locale;

// kg and lb only is our q15 choice
public enum WeightUnit {
    KG,
    LB;

    public static WeightUnit parse(String unit) {
        if (unit == null) {
            throw new IllegalArgumentException("Weight unit is required");
        }
        try {
            return valueOf(unit.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unsupported weight unit: " + unit);
        }
    }
}
