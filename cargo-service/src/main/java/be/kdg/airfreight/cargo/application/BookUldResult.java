package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.FlightKey;
import java.util.Objects;

/** The resolved flight and what happened to the booking. */
public record BookUldResult(FlightKey flightKey, Outcome outcome) {
    public enum Outcome {
        /** A new booking was saved. */
        BOOKED,
        /** The ULD code was already booked on this flight; nothing changed. */
        DUPLICATE_ULD,
        /** The same message was processed before; its first result stands. */
        ALREADY_PROCESSED
    }

    public BookUldResult {
        Objects.requireNonNull(flightKey, "Flight key is required");
        Objects.requireNonNull(outcome, "Outcome is required");
    }
}
