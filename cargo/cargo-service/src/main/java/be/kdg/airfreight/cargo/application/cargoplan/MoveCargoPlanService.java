package be.kdg.airfreight.cargo.application.cargoplan;

import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// moves the cargo of a replanned flight to its new flight once flight says that flight exists
@Service
@Slf4j
public class MoveCargoPlanService {
    private final CargoPlanRepository cargoPlanRepository;

    public MoveCargoPlanService(CargoPlanRepository cargoPlanRepository) {
        this.cargoPlanRepository = cargoPlanRepository;
    }

    // both plans in one transaction: the old one becomes the marker, the new one gets the cargo
    @Transactional
    public void move(FlightKey oldKey, FlightKey newKey) {
        // without bookings the marker row is still made, so the old key always says where to look
        CargoPlan old =
                cargoPlanRepository.findByFlightKey(oldKey).orElseGet(() -> new CargoPlan(oldKey));
        if (old.isReplanned()) {
            log.info(
                    "Cargo of flight {} already moved to {}",
                    oldKey,
                    old.getMovedTo().orElse(null));
            return;
        }
        CargoPlan target = currentPlanOf(newKey);
        int bookings = old.getBookings().size();
        old.moveTo(target);
        cargoPlanRepository.save(target);
        cargoPlanRepository.save(old);
        log.info(
                "Moved {} bookings of replanned flight {} to flight {}",
                bookings,
                oldKey,
                target.getFlightKey());
    }

    // the new flight can itself be replanned already (events out of order), then follow the chain
    private CargoPlan currentPlanOf(FlightKey flightKey) {
        CargoPlan plan =
                cargoPlanRepository
                        .findByFlightKey(flightKey)
                        .orElseGet(() -> new CargoPlan(flightKey));
        while (plan.isReplanned()) {
            FlightKey next = plan.getMovedTo().orElseThrow();
            log.info("Flight {} was replanned too, following to {}", plan.getFlightKey(), next);
            plan = cargoPlanRepository.findByFlightKey(next).orElseGet(() -> new CargoPlan(next));
        }
        return plan;
    }
}
