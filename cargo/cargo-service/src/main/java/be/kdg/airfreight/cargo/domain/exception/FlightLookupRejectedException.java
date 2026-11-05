package be.kdg.airfreight.cargo.domain.exception;

import be.kdg.airfreight.cargo.domain.flight.FlightKey;

// flight refused the lookup so retrying won't help
public class FlightLookupRejectedException extends RuntimeException {
    public FlightLookupRejectedException(FlightKey flightKey, int status, Throwable cause) {
        super(
                "Flight Operations refused the lookup for " + flightKey + " with status " + status,
                cause);
    }
}
