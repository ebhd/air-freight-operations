package be.kdg.airfreight.cargo.domain;

import java.util.Optional;

/** Flight lookup boundary; infrastructure translates Flight Operations data into Cargo's model. */
public interface FlightRepository {
    // empty means flight doesnt know it
    // throws FlightLookupUnavailableException when flight cant be reached
    Optional<FlightInformation> findByFlightKey(FlightKey flightKey);
}
