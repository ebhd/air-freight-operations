package be.kdg.airfreight.flight.domain.flight;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FlightRepository {
    Optional<Flight> findById(FlightKey key);

    // locks the flight row until the transaction ends so the next change waits
    Optional<Flight> findByIdForUpdate(FlightKey key);

    // utc departure date sorted by std
    List<Flight> findByScheduledDepartureDate(LocalDate date);

    List<Flight> findScheduledBefore(Instant end);

    // the keys of the flights with this utc departure date or an earlier one, in any status
    List<FlightKey> findKeysUpTo(LocalDate lastDate);

    Flight save(Flight flight);

    // the flight with its delays
    void delete(FlightKey key);

    // the replanned flight that expects a new flight with this key and std and has none yet
    Optional<Flight> findReplacedBy(FlightKey newKey, Instant newScheduledDeparture);
}
