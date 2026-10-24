package be.kdg.airfreight.cargo.infrastructure.persistence;

import be.kdg.airfreight.cargo.domain.CargoPlan;
import be.kdg.airfreight.cargo.domain.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.CargoPlanState;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.UldBooking;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaCargoPlan;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaCargoPlanId;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaCargoPlanRepository;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaUldBooking;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaUldBookingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

// a plan is its plan row with the version plus its bookings
@Repository
class DbCargoPlanRepository implements CargoPlanRepository {
    private final JpaCargoPlanRepository plans;
    private final JpaUldBookingRepository bookings;
    private final EntityManager entityManager;

    DbCargoPlanRepository(
            JpaCargoPlanRepository plans,
            JpaUldBookingRepository bookings,
            EntityManager entityManager) {
        this.plans = plans;
        this.bookings = bookings;
        this.entityManager = entityManager;
    }

    // joins the transaction of the caller and keeps the plan row with the version save checks
    @Override
    @Transactional(readOnly = true)
    public Optional<CargoPlan> findByFlightKey(FlightKey flightKey) {
        if (plans.findById(JpaCargoPlanId.of(flightKey)).isEmpty()) {
            return Optional.empty();
        }
        List<UldBooking> stored =
                bookings
                        .findByIdFlightNumberAndIdScheduledDepartureDateAndIdDepartureAirportCode(
                                flightKey.flightNumber(),
                                flightKey.scheduledDepartureDate(),
                                flightKey.departureAirportCode().value())
                        .stream()
                        .map(JpaUldBooking::toDomain)
                        .toList();
        // state not stored until issue 26 since nothing can finalize a plan yet
        return Optional.of(CargoPlan.restore(flightKey, CargoPlanState.DRAFT, stored));
    }

    // load and save a plan in one transaction or the version check compares the wrong version
    @Override
    public void save(CargoPlan cargoPlan) {
        JpaCargoPlanId id = JpaCargoPlanId.of(cargoPlan.getFlightKey());
        Optional<JpaCargoPlan> stored = plans.findById(id);
        if (stored.isPresent()) {
            // only bookings change so make hibernate raise and check the plan version at commit
            entityManager.lock(stored.get(), LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        } else {
            // the first booking creates the plan row with version 0
            plans.save(new JpaCargoPlan(id));
        }
        bookings.saveAll(
                cargoPlan.getBookings().stream()
                        .map(booking -> JpaUldBooking.fromDomain(cargoPlan.getFlightKey(), booking))
                        .toList());
    }
}
