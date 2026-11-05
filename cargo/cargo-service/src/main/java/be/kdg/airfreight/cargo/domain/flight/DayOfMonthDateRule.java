package be.kdg.airfreight.cargo.domain.flight;

import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public final class DayOfMonthDateRule {
    private DayOfMonthDateRule() {}

    public static LocalDate resolve(int dayOfMonth, Instant reference) {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new InvalidCargoValueException("Day of month must be between 1 and 31");
        }
        // only the utc date counts not the time of day
        LocalDate referenceDate = LocalDate.ofInstant(reference, ZoneOffset.UTC);
        YearMonth referenceMonth = YearMonth.from(referenceDate);

        LocalDate nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        // the nearest date is always in the previous, current or next month
        for (int offset = -1; offset <= 1; offset++) {
            YearMonth month = referenceMonth.plusMonths(offset);
            // skip months that don't have this day like the 31st in february
            if (month.isValidDay(dayOfMonth)) {
                LocalDate candidate = month.atDay(dayOfMonth);
                long distance = Math.abs(ChronoUnit.DAYS.between(referenceDate, candidate));
                // months run forward so on a tie the later date wins
                if (distance <= nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
        }
        // never null since three months in a row always include a 31 day month
        return nearest;
    }
}
