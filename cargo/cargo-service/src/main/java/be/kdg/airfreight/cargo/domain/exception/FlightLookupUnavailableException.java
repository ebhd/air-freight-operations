package be.kdg.airfreight.cargo.domain.exception;

import be.kdg.airfreight.cargo.domain.flight.FlightKey;

// flight unreachable so the lookup can be retried
public class FlightLookupUnavailableException extends RuntimeException {
    public FlightLookupUnavailableException(FlightKey flightKey, Throwable cause) {
        super("Flight information unavailable for " + flightKey, cause);
    }
}
