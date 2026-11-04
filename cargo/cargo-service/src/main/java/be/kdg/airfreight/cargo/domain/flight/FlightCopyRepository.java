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

    // only the estimates so the status from another event is never overwritten
    // false when the copy has no such flight or already has a newer change
    boolean saveEstimatesIfNewer(
            FlightKey flightKey,
            Instant estimatedDeparture,
            Instant estimatedArrival,
            Instant changedAt);

    // a flight never goes back: only a row in an earlier status is changed; false for a
    // redelivery, an older event or an unknown flight; last_change_at is not touched so an older
    // estimate that arrives later can still be applied
    boolean advanceStatus(FlightKey flightKey, FlightStatus status);
}
