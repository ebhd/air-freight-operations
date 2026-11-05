package be.kdg.airfreight.cargo.domain.exception;

import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loading.LoadingRefusal;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindow;

// a valid request at the wrong moment so the api answers 409 with the reason
public class LoadingNotAllowedException extends RuntimeException {
    private final FlightKey flightKey;
    private final LoadingRefusal refusal;
    private final LoadingWindow window;

    public LoadingNotAllowedException(
            FlightKey flightKey, LoadingRefusal refusal, LoadingWindow window) {
        super("Loading on " + flightKey + " is not allowed: " + reason(refusal, window));
        this.flightKey = flightKey;
        this.refusal = refusal;
        this.window = window;
    }

    private static String reason(LoadingRefusal refusal, LoadingWindow window) {
        return switch (refusal) {
            case PLAN_FINALIZED -> "the cargo plan is finalized";
            case FLIGHT_NOT_PLANNED -> "the flight is no longer planned";
            case WINDOW_NOT_OPEN_YET -> "the loading window opens at " + window.opensAt();
            case WINDOW_CLOSED -> "the loading window closed at " + window.closesAt();
        };
    }

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public LoadingRefusal getRefusal() {
        return refusal;
    }

    public LoadingWindow getWindow() {
        return window;
    }
}
