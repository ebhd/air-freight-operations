package be.kdg.airfreight.cargo.domain;

// why a load may not be changed checked or finalized right now
public enum LoadingRefusal {
    PLAN_FINALIZED,
    FLIGHT_NOT_PLANNED,
    WINDOW_NOT_OPEN_YET,
    WINDOW_CLOSED
}
