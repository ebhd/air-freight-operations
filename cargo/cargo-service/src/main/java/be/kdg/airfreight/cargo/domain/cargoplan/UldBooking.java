package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.util.Objects;
import java.util.Optional;

public final class UldBooking {
    private final UldCode code;
    private final Weight weight;
    private final AirportCode destination;
    // null while the uld is not part of the load
    private LoadingPosition position;

    public UldBooking(UldCode code, Weight weight, AirportCode destination) {
        this.code = Objects.requireNonNull(code, "ULD code is required");
        this.weight = Objects.requireNonNull(weight, "Weight is required");
        this.destination = Objects.requireNonNull(destination, "Destination is required");
    }

    // restores a stored booking whose position may be null
    public UldBooking(
            UldCode code, Weight weight, AirportCode destination, LoadingPosition position) {
        this(code, weight, destination);
        this.position = position;
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

    public Optional<LoadingPosition> getPosition() {
        return Optional.ofNullable(position);
    }

    // package private so only CargoPlan can change a position and guard its rules
    void placeAt(LoadingPosition position) {
        this.position = position;
    }

    void clearPosition() {
        this.position = null;
    }
}
