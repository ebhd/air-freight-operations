package be.kdg.airfreight.cargo.domain;

import java.util.Objects;

/** A booking identified by its ULD code within the owning cargo plan; not yet loaded. */
public final class UldBooking {
    private final UldCode code;
    private final Weight weight;
    private final AirportCode destination;

    public UldBooking(UldCode code, Weight weight, AirportCode destination) {
        this.code = Objects.requireNonNull(code, "ULD code is required");
        this.weight = Objects.requireNonNull(weight, "Weight is required");
        this.destination = Objects.requireNonNull(destination, "Destination is required");
    }

    public UldCode getCode() {
        return code;
    }

    public Weight getWeight() {
        return weight;
    }

    public AirportCode getDestination() {
        return destination;
    }
}
