package be.kdg.airfreight.cargo.domain;

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
