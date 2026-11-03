package be.kdg.airfreight.cargo.domain.flight;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// cargos own copy of the flights it needs, kept up to date by flights events
public interface FlightCopyRepository {
    // only the copy, never asks flight
    Optional<FlightInformation> findCopy(FlightKey flightKey);

    // only the copy, ordered by scheduled departure
    List<FlightInformation> findCopiesOn(LocalDate scheduledDepartureDate);

    // changedAt is when flight made the change; false when the copy already has a newer change
    boolean saveIfNewer(FlightInformation flight, Instant changedAt);
}
