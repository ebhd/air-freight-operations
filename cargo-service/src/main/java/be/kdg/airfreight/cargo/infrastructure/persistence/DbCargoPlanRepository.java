package be.kdg.airfreight.cargo.infrastructure.persistence;

import be.kdg.airfreight.cargo.domain.CargoPlan;
import be.kdg.airfreight.cargo.domain.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.UldBooking;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaUldBooking;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaUldBookingRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Stores a cargo plan as its bookings. A plan has no state of its own yet, so a flight without
 * bookings has no stored plan.
 */
@Repository
class DbCargoPlanRepository implements CargoPlanRepository {
    private final JpaUldBookingRepository bookings;

    DbCargoPlanRepository(JpaUldBookingRepository bookings) {
        this.bookings = bookings;
    }

    @Override
    public Optional<CargoPlan> findByFlightKey(FlightKey flightKey) {
        List<UldBooking> stored =
                bookings
                        .findByIdFlightNumberAndIdScheduledDepartureDateAndIdDepartureAirportCode(
                                flightKey.flightNumber(),
                                flightKey.scheduledDepartureDate(),
                                flightKey.departureAirportCode().value())
                        .stream()
                        .map(JpaUldBooking::toDomain)
                        .toList();
        return stored.isEmpty()
                ? Optional.empty()
                : Optional.of(CargoPlan.restore(flightKey, stored));
    }

    @Override
    public void save(CargoPlan cargoPlan) {
        bookings.saveAll(
                cargoPlan.getBookings().stream()
                        .map(booking -> JpaUldBooking.fromDomain(cargoPlan.getFlightKey(), booking))
                        .toList());
    }
}
