package be.kdg.airfreight.cargo.domain;

import java.util.Optional;

public interface FlightRepository {
    // empty if flight doesnt know it and FlightLookupUnavailableException if unreachable
    Optional<FlightInformation> findByFlightKey(FlightKey flightKey);
}
