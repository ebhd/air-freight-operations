package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import java.math.BigDecimal;

// both sides in one unit and the most the heavier side may weigh
public record LateralBalance(
        BigDecimal left, BigDecimal right, BigDecimal maxHeavierSide, WeightUnit unit) {
    public BigDecimal heavier() {
        return left.max(right);
    }

    // exact decimals so 1100 against 1000 passes and 1101 fails without rounding first
    public boolean passed() {
        return heavier().compareTo(maxHeavierSide) <= 0;
    }
}
