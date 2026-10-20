package be.kdg.airfreight.cargo.domain;

import java.util.Objects;

public record FlightInformation(FlightKey flightKey, boolean planned) {
    public FlightInformation {
        Objects.requireNonNull(flightKey, "Flight key is required");
    }
}
