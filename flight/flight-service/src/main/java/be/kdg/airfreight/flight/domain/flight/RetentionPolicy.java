package be.kdg.airfreight.flight.domain.flight;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

// how long a flight is kept after its scheduled departure date, seven days by default
public record RetentionPolicy(Period period) {
    public RetentionPolicy {
        if (period == null) {
            throw new IllegalArgumentException("retention period is required");
        }
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("retention period must be positive, was " + period);
        }
    }

    // a flight of this utc date or earlier has expired, the time of day does not count
    // so a flight of 29 october expires on 5 november at 00:00 utc with seven days
    public LocalDate lastExpiredDate(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC).minus(period);
    }
}
