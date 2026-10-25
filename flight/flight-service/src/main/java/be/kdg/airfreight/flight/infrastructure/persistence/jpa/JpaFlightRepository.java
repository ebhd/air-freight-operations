package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFlightRepository extends JpaRepository<JpaFlight, JpaFlightId> {
    List<JpaFlight> findByIdScheduledDepartureDateOrderByScheduledDepartureAsc(LocalDate date);
}
