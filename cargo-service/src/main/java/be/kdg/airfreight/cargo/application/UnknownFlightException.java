package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.FlightKey;

// flight operations does not know the flight or has already cleaned it up
public class UnknownFlightException extends RuntimeException {
    public UnknownFlightException(FlightKey flightKey) {
        super("Unknown flight " + flightKey);
    }
}
