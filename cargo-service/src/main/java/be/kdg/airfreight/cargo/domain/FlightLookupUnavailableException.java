package be.kdg.airfreight.cargo.domain;

import java.time.LocalDate;

// flight unreachable so the lookup can be retried
public class FlightLookupUnavailableException extends RuntimeException {
    public FlightLookupUnavailableException(FlightKey flightKey, Throwable cause) {
        super("Flight information unavailable for " + flightKey, cause);
    }

    public FlightLookupUnavailableException(LocalDate scheduledDepartureDate, Throwable cause) {
        super("Flight information unavailable for flights on " + scheduledDepartureDate, cause);
    }
}
