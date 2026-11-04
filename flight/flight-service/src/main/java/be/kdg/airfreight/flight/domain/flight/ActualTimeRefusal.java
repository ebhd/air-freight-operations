package be.kdg.airfreight.flight.domain.flight;

// why an actual departure or arrival was refused, sent to the page as the refusal code
public enum ActualTimeRefusal {
    NOT_PLANNED,
    NOT_DEPARTED,
    CPM_NOT_PROCESSED,
    IN_THE_FUTURE,
    NOT_AFTER_DEPARTURE,
    TOO_LATE
}
