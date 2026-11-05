package be.kdg.airfreight.flight.domain.flight.replanning;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;

// one class for every replanning refusal, the code tells the page which one
public class ReplanningRefusedException extends DomainRuleViolationException {
    private final ReplanningRefusal refusal;

    public ReplanningRefusedException(ReplanningRefusal refusal, String message) {
        super(message);
        this.refusal = refusal;
    }

    public ReplanningRefusal refusal() {
        return refusal;
    }
}
