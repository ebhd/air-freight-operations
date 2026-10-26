package be.kdg.airfreight.flight.domain.flight;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FlightRepository {
    Optional<Flight> findById(FlightKey key);

    // locks the flight row until the transaction ends so the next change waits
    Optional<Flight> findByIdForUpdate(FlightKey key);

    // utc departure date sorted by std
    List<Flight> findByScheduledDepartureDate(LocalDate date);

    Flight save(Flight flight);
}
