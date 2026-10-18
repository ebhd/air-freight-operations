package be.kdg.airfreight.cargo.domain;

import java.util.Objects;

/** Cargo's view of a flight: only the information needed to accept a booking. */
public record FlightInformation(FlightKey flightKey, boolean planned) {
    public FlightInformation {
        Objects.requireNonNull(flightKey, "Flight key is required");
    }
}
