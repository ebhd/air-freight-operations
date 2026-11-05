package be.kdg.airfreight.cargo.infrastructure.persistence;

import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaCargoPlan;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaCargoPlanId;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaCargoPlanRepository;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaUldBooking;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaUldBookingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
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
        Optional<JpaCargoPlan> plan = plans.findById(JpaCargoPlanId.of(flightKey));
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        List<UldBooking> stored =
                storedBookings(flightKey).stream().map(JpaUldBooking::toDomain).toList();
        JpaCargoPlanId movedTo = plan.get().getMovedTo();
        return Optional.of(
                CargoPlan.restore(
                        flightKey,
                        plan.get().getState(),
                        plan.get().getFinalizedAt(),
                        movedTo == null ? null : movedTo.toFlightKey(),
                        stored));
    }

    // load and save a plan in one transaction or the version check compares the wrong version
    // save makes the stored bookings equal to the plan, so a moved booking is also deleted
    @Override
    public void save(CargoPlan cargoPlan) {
        JpaCargoPlanId id = JpaCargoPlanId.of(cargoPlan.getFlightKey());
        Optional<JpaCargoPlan> stored = plans.findById(id);
        Instant finalizedAt = cargoPlan.getFinalizedAt().orElse(null);
        JpaCargoPlanId movedTo = cargoPlan.getMovedTo().map(JpaCargoPlanId::of).orElse(null);
        if (stored.isPresent() && stored.get().getState() != cargoPlan.getState()) {
            // finalizing or moving changes the plan row itself so hibernate raises and checks
            // the version
            stored.get().changeState(cargoPlan.getState(), finalizedAt, movedTo);
        } else if (stored.isPresent()) {
            // only bookings change so make hibernate raise and check the plan version at commit
            entityManager.lock(stored.get(), LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        } else {
            // the first save creates the plan row with version 0
            plans.save(new JpaCargoPlan(id, cargoPlan.getState(), finalizedAt, movedTo));
        }
        List<JpaUldBooking> kept =
                cargoPlan.getBookings().stream()
                        .map(booking -> JpaUldBooking.fromDomain(cargoPlan.getFlightKey(), booking))
                        .toList();
        List<JpaUldBooking> removed =
                storedBookings(cargoPlan.getFlightKey()).stream()
                        .filter(row -> cargoPlan.findBooking(row.toDomain().getCode()).isEmpty())
                        .toList();
        bookings.deleteAll(removed);
        // the deletes go to the database first so a moved position is free on the new plan
        bookings.flush();
        bookings.saveAll(kept);
    }

    // the delete of the plan row checks the version, so a change that overlaps it is retried
    @Override
    public void deleteByFlightKey(FlightKey flightKey) {
        bookings.deleteAll(storedBookings(flightKey));
        plans.deleteById(JpaCargoPlanId.of(flightKey));
    }

    private List<JpaUldBooking> storedBookings(FlightKey flightKey) {
        return bookings.findByIdFlightNumberAndIdScheduledDepartureDateAndIdDepartureAirportCode(
                flightKey.flightNumber(),
                flightKey.scheduledDepartureDate(),
                flightKey.departureAirportCode().value());
    }
}
