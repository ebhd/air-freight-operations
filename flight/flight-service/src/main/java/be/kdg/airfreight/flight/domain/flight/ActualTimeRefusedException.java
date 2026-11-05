package be.kdg.airfreight.flight.domain.flight;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;
import java.time.Instant;

// a rule of the flight refused the actual time, so the api answers 409 with the refusal code
public class ActualTimeRefusedException extends DomainRuleViolationException {
    private final ActualTimeRefusal refusal;
    // only for TOO_LATE: the last instant the flight would have accepted
    private final Instant latestAllowed;

    public ActualTimeRefusedException(ActualTimeRefusal refusal, String message) {
        this(refusal, message, null);
    }

    public ActualTimeRefusedException(
            ActualTimeRefusal refusal, String message, Instant latestAllowed) {
        super(message);
        this.refusal = refusal;
        this.latestAllowed = latestAllowed;
    }

    public ActualTimeRefusal refusal() {
        return refusal;
    }

    public Instant latestAllowed() {
        return latestAllowed;
    }
}
