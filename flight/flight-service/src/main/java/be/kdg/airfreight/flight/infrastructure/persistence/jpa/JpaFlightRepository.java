package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface JpaFlightRepository extends JpaRepository<JpaFlight, JpaFlightId> {
    List<JpaFlight> findByIdScheduledDepartureDateOrderByScheduledDepartureAsc(LocalDate date);

    // select for update so a second delay change waits until the first one commits
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select flight from JpaFlight flight where flight.id = :id")
    Optional<JpaFlight> findByIdForUpdate(JpaFlightId id);
}
