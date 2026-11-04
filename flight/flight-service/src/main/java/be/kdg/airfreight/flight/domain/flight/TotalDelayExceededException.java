package be.kdg.airfreight.flight.domain.flight;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;

// the change itself is valid but all delays together would pass 12 hours
public class TotalDelayExceededException extends DomainRuleViolationException {
    private final int totalMinutes;
    private final boolean replanningPossible;
    // the proposed new flight, filled by the application once it knows the free number
    private final Replanning proposal;

    public TotalDelayExceededException(int totalMinutes, boolean replanningPossible) {
        this(
                totalMinutes,
                replanningPossible,
                null,
                replanningPossible
                        ? "You can replan this flight instead."
                        : "A departed flight cannot be replanned.");
    }

    // the replan question the page asks, with the proposed flight
    public TotalDelayExceededException(int totalMinutes, Replanning proposal) {
        this(
                totalMinutes,
                true,
                proposal,
                "You can replan this flight as " + proposal.newFlightNumber() + " instead.");
    }

    // the replan itself would be refused, for example no free flight number
    public TotalDelayExceededException(int totalMinutes, String whyNoReplanning) {
        this(totalMinutes, false, null, "Replanning is not possible: " + whyNoReplanning);
    }

    private TotalDelayExceededException(
            int totalMinutes, boolean replanningPossible, Replanning proposal, String ending) {
        super(message(totalMinutes) + " " + ending);
        this.totalMinutes = totalMinutes;
        this.replanningPossible = replanningPossible;
        this.proposal = proposal;
    }

    private static String message(int totalMinutes) {
        return "All delays together would be "
                + totalMinutes
                + " minutes ("
                + hoursAndMinutes(totalMinutes)
                + "). At most "
                + Flight.MAX_TOTAL_DELAY_MINUTES
                + " minutes ("
                + hoursAndMinutes(Flight.MAX_TOTAL_DELAY_MINUTES)
                + ") is allowed.";
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

    // null when there is no proposal
    public Replanning proposal() {
        return proposal;
    }
}
