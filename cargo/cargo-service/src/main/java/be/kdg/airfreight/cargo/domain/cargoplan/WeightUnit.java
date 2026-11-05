package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import java.util.Locale;

// kg and lb only is our q15 choice
public enum WeightUnit {
    KG,
    LB;

    public static WeightUnit parse(String unit) {
        if (unit == null) {
            throw new InvalidCargoValueException("Weight unit is required");
        }
        try {
            return valueOf(unit.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            // valueOf throws this for an unknown name
            throw new InvalidCargoValueException("Unsupported weight unit: " + unit);
        }
    }
}
