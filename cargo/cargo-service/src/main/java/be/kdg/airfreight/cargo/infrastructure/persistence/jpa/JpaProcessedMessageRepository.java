package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcessedMessageRepository extends JpaRepository<JpaProcessedMessage, String> {
    void deleteByFlightNumberAndScheduledDepartureDateAndDepartureAirportCode(
            String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode);
}
