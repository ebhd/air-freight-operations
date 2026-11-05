package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// hard check: the heavier side weighs at most the lighter side times one plus the tolerance
public final class LateralBalanceCheck implements LoadCheck {
    static final String NAME = "Lateral balance";
    // both sides are compared in kg
    private static final WeightUnit COMMON_UNIT = WeightUnit.KG;

    // 0.10 means 10% of the lighter side
    private final BigDecimal tolerance;

    public LateralBalanceCheck(BigDecimal tolerance) {
        if (tolerance == null) {
            throw new InvalidCargoValueException("Lateral balance tolerance is required");
        }
        if (tolerance.signum() < 0) {
            throw new InvalidCargoValueException(
                    "Lateral balance tolerance must not be negative: " + tolerance);
        }
        this.tolerance = tolerance;
    }

    public BigDecimal tolerance() {
        return tolerance;
    }

    public LateralBalance balance(List<UldBooking> bookings) {
        BigDecimal left = BigDecimal.ZERO;
        BigDecimal right = BigDecimal.ZERO;
        for (UldBooking booking : bookings) {
            Optional<LoadingPosition> position = booking.getPosition();
            // hard checks look at positioned cargo only
            if (position.isEmpty()) {
                continue;
            }
            BigDecimal kilograms = booking.getWeight().inKilograms();
            // a1 a2 b1 and x have side none so they count for neither side
            if (position.get().side() == LoadingPosition.Side.LEFT) {
                left = left.add(kilograms);
            } else if (position.get().side() == LoadingPosition.Side.RIGHT) {
                right = right.add(kilograms);
            }
        }
        // multiplying needs no division, so zero against zero passes and zero against a
        // positive side fails
        BigDecimal maxHeavierSide = left.min(right).multiply(BigDecimal.ONE.add(tolerance));
        return new LateralBalance(left, right, maxHeavierSide, COMMON_UNIT);
    }

    @Override
    public LoadCheckResult evaluate(List<UldBooking> bookings, AirportCode arrivalAirport) {
        LateralBalance balance = balance(bookings);
        String totals =
                "left "
                        + weight(balance.left(), balance.unit())
                        + ", right "
                        + weight(balance.right(), balance.unit())
                        + ", the heavier side may weigh at most "
                        + weight(balance.maxHeavierSide(), balance.unit());
        if (balance.passed()) {
            return new LoadCheckResult(NAME, CheckSeverity.HARD, true, "In balance: " + totals);
        }
        String heavierSide = balance.left().compareTo(balance.right()) > 0 ? "Left" : "Right";
        return new LoadCheckResult(
                NAME, CheckSeverity.HARD, false, heavierSide + " side is too heavy: " + totals);
    }

    // the exact value without trailing zeros so the text never rounds
    private static String weight(BigDecimal value, WeightUnit unit) {
        return value.stripTrailingZeros().toPlainString() + " " + unit;
    }
}
