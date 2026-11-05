package be.kdg.airfreight.flight.domain.flight;

public enum FlightStatus {
    PLANNED,
    DEPARTED,
    LANDED,
    // final: the delays passed 12 hours and a new flight replaces this one
    REPLANNED
}
