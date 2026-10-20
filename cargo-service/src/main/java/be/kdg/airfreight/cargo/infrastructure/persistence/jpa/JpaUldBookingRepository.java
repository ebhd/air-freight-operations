package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUldBookingRepository extends JpaRepository<JpaUldBooking, JpaUldBookingId> {
    List<JpaUldBooking> findByIdFlightNumberAndIdScheduledDepartureDateAndIdDepartureAirportCode(
            String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode);
}
