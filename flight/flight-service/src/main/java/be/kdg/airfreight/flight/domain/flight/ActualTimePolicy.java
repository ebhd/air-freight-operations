package be.kdg.airfreight.flight.domain.flight;

import java.time.Duration;
import java.time.Instant;

// how much later than the estimate an actual time may be, five minutes by default
public record ActualTimePolicy(Duration tolerance) {
    public ActualTimePolicy {
        if (tolerance == null) {
            throw new IllegalArgumentException("actual time tolerance is required");
        }
        if (tolerance.isNegative()) {
            throw new IllegalArgumentException(
                    "actual time tolerance must be zero or positive, was " + tolerance);
        }
    }

    // the last instant that is still accepted, the limit itself included
    public Instant latestAllowed(Instant estimate) {
        return estimate.plus(tolerance);
    }
}
