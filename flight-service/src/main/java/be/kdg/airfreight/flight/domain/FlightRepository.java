package be.kdg.airfreight.flight.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FlightRepository {
    Optional<Flight> findById(FlightKey key);

    // utc departure date sorted by std
    List<Flight> findByScheduledDepartureDate(LocalDate date);

    Flight save(Flight flight);
}
