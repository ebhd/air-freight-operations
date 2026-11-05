package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcessedMessageRepository extends JpaRepository<JpaProcessedMessage, UUID> {
    void deleteByFlightNumberAndScheduledDepartureDateAndDepartureAirportCode(
            String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode);
}
