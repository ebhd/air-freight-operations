package be.kdg.airfreight.cargo.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// both throw FlightLookupUnavailableException if flight is unreachable
public interface FlightRepository {
    // empty if flight doesnt know it
    Optional<FlightInformation> findByFlightKey(FlightKey flightKey);

    List<FlightInformation> findByScheduledDepartureDate(LocalDate scheduledDepartureDate);
}
