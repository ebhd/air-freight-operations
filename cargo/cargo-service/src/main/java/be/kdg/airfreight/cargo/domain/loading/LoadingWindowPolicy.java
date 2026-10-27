package be.kdg.airfreight.cargo.domain.loading;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import java.time.Duration;
import java.time.Instant;

public record LoadingWindowPolicy(Duration opensBeforeStd, Duration closesBeforeEtd) {
    public LoadingWindowPolicy {
        if (opensBeforeStd == null) {
            throw new InvalidCargoValueException("Loading window offset before STD is required");
        }
        if (closesBeforeEtd == null) {
            throw new InvalidCargoValueException("Loading window offset before ETD is required");
        }
        String offsets =
                "opens "
                        + opensBeforeStd
                        + " before STD, closes "
                        + closesBeforeEtd
                        + " before ETD";
        if (opensBeforeStd.isNegative() || closesBeforeEtd.isNegative()) {
            throw new InvalidCargoValueException(
                    "Loading window offsets must not be negative: " + offsets);
        }
        // otherwise a flight without delay would have an empty window
        if (opensBeforeStd.compareTo(closesBeforeEtd) <= 0) {
            throw new InvalidCargoValueException(
                    "Loading window must open longer before STD than it closes before ETD: "
                            + offsets);
        }
    }

    // only std and etd go in so arrival times can never move the window
    public LoadingWindow windowFor(Instant scheduledDeparture, Instant estimatedDeparture) {
        return new LoadingWindow(
                scheduledDeparture.minus(opensBeforeStd),
                estimatedDeparture.minus(closesBeforeEtd));
    }
}
