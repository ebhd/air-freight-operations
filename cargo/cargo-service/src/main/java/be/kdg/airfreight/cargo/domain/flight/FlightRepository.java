package be.kdg.airfreight.cargo.domain.flight;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// what cargo knows about flights, read from cargos own copy
public interface FlightRepository {
    // the copy first otherwise one lookup at flight that is then kept in the copy
    // empty if flight doesnt know it, FlightLookupUnavailableException if flight is unreachable
    Optional<FlightInformation> findByFlightKey(FlightKey flightKey);

    // only the copy so the day overview keeps working while flight is down
    List<FlightInformation> findByScheduledDepartureDate(LocalDate scheduledDepartureDate);
}
