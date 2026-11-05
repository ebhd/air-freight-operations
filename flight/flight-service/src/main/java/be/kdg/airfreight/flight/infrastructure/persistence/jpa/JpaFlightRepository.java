package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface JpaFlightRepository extends JpaRepository<JpaFlight, JpaFlightId> {
    List<JpaFlight> findByIdScheduledDepartureDateOrderByScheduledDepartureAsc(LocalDate date);

    List<JpaFlight> findByScheduledDepartureBeforeOrderByScheduledDepartureAsc(Instant end);

    // only the keys, so the delays of old flights are not loaded just to find them
    @Query(
            "select flight.id from JpaFlight flight"
                    + " where flight.id.scheduledDepartureDate <= :lastDate")
    List<JpaFlightId> findIdsUpTo(LocalDate lastDate);

    // select for update so a second delay change waits until the first one commits
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select flight from JpaFlight flight where flight.id = :id")
    Optional<JpaFlight> findByIdForUpdate(JpaFlightId id);

    // replanned flights that expect this new number, airport and std and have no replacement yet
    @Query(
            "select flight from JpaFlight flight"
                    + " where flight.status = :status"
                    + " and flight.replacementCreated = false"
                    + " and flight.replanning.newFlightNumber = :newFlightNumber"
                    + " and flight.id.departureAirportCode = :departureAirportCode"
                    + " and flight.replanning.newScheduledDeparture = :newScheduledDeparture"
                    + " order by flight.replanning.replannedAt")
    List<JpaFlight> findReplacedBy(
            FlightStatus status,
            String newFlightNumber,
            String departureAirportCode,
            Instant newScheduledDeparture);
}
