package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.FlightKey;
import java.util.Objects;

public record BookUldResult(FlightKey flightKey, Outcome outcome) {
    public enum Outcome {
        BOOKED,
        // uld code already on this flight so nothing changed
        DUPLICATE_ULD,
        // same message id seen before
        ALREADY_PROCESSED
    }

    public BookUldResult {
        Objects.requireNonNull(flightKey, "Flight key is required");
        Objects.requireNonNull(outcome, "Outcome is required");
    }
}
