package be.kdg.airfreight.cargo.domain.flight;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.stream.Stream;

public final class DayOfMonthDateRule {
    private DayOfMonthDateRule() {}

    public static LocalDate resolve(int dayOfMonth, Instant reference) {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new InvalidCargoValueException("Day of month must be between 1 and 31");
        }
        // only the utc date counts not the time of day
        LocalDate referenceDate = LocalDate.ofInstant(reference, ZoneOffset.UTC);
        YearMonth referenceMonth = YearMonth.from(referenceDate);

        // fewest days away wins and on a tie the later date wins
        Comparator<LocalDate> nearestThenFuture =
                Comparator.<LocalDate>comparingLong(
                                candidate ->
                                        Math.abs(ChronoUnit.DAYS.between(referenceDate, candidate)))
                        .thenComparing(Comparator.reverseOrder());

        // previous current and next month is our take on q02 not a course rule
        return Stream.of(
                        referenceMonth.minusMonths(1), referenceMonth, referenceMonth.plusMonths(1))
                // skip months that dont have this day like the 31st in february
                .filter(month -> month.isValidDay(dayOfMonth))
                .map(month -> month.atDay(dayOfMonth))
                .min(nearestThenFuture)
                // never empty since three months in a row always include a 31 day month
                .orElseThrow();
    }
}
