package be.kdg.airfreight.flight.domain.flight;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;

// the change itself is valid but all delays together would pass 12 hours
public class TotalDelayExceededException extends DomainRuleViolationException {
    private final int totalMinutes;
    private final boolean replanningPossible;

    public TotalDelayExceededException(int totalMinutes, boolean replanningPossible) {
        super(message(totalMinutes, replanningPossible));
        this.totalMinutes = totalMinutes;
        this.replanningPossible = replanningPossible;
    }

    private static String message(int totalMinutes, boolean replanningPossible) {
        String message =
                "All delays together would be "
                        + totalMinutes
                        + " minutes ("
                        + hoursAndMinutes(totalMinutes)
                        + "). At most "
                        + Flight.MAX_TOTAL_DELAY_MINUTES
                        + " minutes ("
                        + hoursAndMinutes(Flight.MAX_TOTAL_DELAY_MINUTES)
                        + ") is allowed.";
        return replanningPossible
                ? message + " You can replan this flight instead."
                : message + " A departed flight cannot be replanned.";
    }

    // 740 becomes 12 h 20 min and 720 becomes 12 h
    static String hoursAndMinutes(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    public int totalMinutes() {
        return totalMinutes;
    }

    public boolean replanningPossible() {
        return replanningPossible;
    }
}
