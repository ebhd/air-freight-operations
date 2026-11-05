package be.kdg.airfreight.cargo.domain.exception;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;

// another uld already has the position so the api answers 409
public class PositionOccupiedException extends RuntimeException {
    public PositionOccupiedException(
            FlightKey flightKey, LoadingPosition position, UldCode occupant) {
        super(
                "Position "
                        + position
                        + " on flight "
                        + flightKey
                        + " is already taken by ULD "
                        + occupant.value());
    }
}
