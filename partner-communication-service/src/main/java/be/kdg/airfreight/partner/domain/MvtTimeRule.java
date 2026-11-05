package be.kdg.airfreight.partner.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Objects;

// an mvt time has no date, so the date comes from the moment the telegram was first read
public final class MvtTimeRule {
    private static final Duration HALF_DAY = Duration.ofHours(12);
    private static final Duration ONE_DAY = Duration.ofDays(1);

    private MvtTimeRule() {}

    // pass the stored first reading, a later moment can give another date
    public static Instant resolve(String hhmm, Instant reference) {
        Objects.requireNonNull(reference, "reference time must not be null");
        LocalTime time = parse(hhmm);
        LocalDate referenceDate = LocalDate.ofInstant(reference, ZoneOffset.UTC);
        Instant candidate = referenceDate.atTime(time).toInstant(ZoneOffset.UTC);

        // exactly 12 hours away counts as the later side
        if (!candidate.isAfter(reference.minus(HALF_DAY))) {
            return candidate.plus(ONE_DAY);
        }
        if (candidate.isAfter(reference.plus(HALF_DAY))) {
            return candidate.minus(ONE_DAY);
        }
        return candidate;
    }

    private static LocalTime parse(String hhmm) {
        if (hhmm == null || !hhmm.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("time must be four digits in HHmm form");
        }
        int hour = Integer.parseInt(hhmm.substring(0, 2));
        int minute = Integer.parseInt(hhmm.substring(2));
        if (hour > 23 || minute > 59) {
            throw new IllegalArgumentException("time must be between 0000 and 2359");
        }
        return LocalTime.of(hour, minute);
    }
}
