package be.kdg.airfreight.flight.domain;

import java.util.Optional;

public interface FlightRepository {
    Optional<Flight> findById(FlightKey key);

    Flight save(Flight flight);
}
