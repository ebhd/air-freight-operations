package be.kdg.airfreight.cargo.domain;

// flight couldnt be reached so the lookup can be retried later
public class FlightLookupUnavailableException extends RuntimeException {
    public FlightLookupUnavailableException(FlightKey flightKey, Throwable cause) {
        super("Flight information unavailable for " + flightKey, cause);
    }
}
