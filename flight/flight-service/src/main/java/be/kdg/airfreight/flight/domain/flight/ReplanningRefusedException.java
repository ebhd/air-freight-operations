package be.kdg.airfreight.flight.domain.flight;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;

// one class for every replanning refusal, the code tells the page which one
public class ReplanningRefusedException extends DomainRuleViolationException {
    public static final String NOT_PLANNED = "NOT_PLANNED";
    public static final String REPLANNING_NOT_NEEDED = "REPLANNING_NOT_NEEDED";
    public static final String NO_FREE_FLIGHT_NUMBER = "NO_FREE_FLIGHT_NUMBER";
    public static final String SCHEDULE_NOT_EXPRESSIBLE = "SCHEDULE_NOT_EXPRESSIBLE";

    private final String refusal;

    public ReplanningRefusedException(String refusal, String message) {
        super(message);
        this.refusal = refusal;
    }

    public String refusal() {
        return refusal;
    }
}
