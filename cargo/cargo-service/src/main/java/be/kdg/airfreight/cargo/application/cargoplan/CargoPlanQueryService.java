package be.kdg.airfreight.cargo.application.cargoplan;

import be.kdg.airfreight.cargo.application.flight.UnknownFlightException;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CargoPlanQueryService {
    private final FlightRepository flightRepository;
    private final CargoPlanRepository cargoPlanRepository;

    public CargoPlanQueryService(
            FlightRepository flightRepository, CargoPlanRepository cargoPlanRepository) {
        this.flightRepository = flightRepository;
        this.cargoPlanRepository = cargoPlanRepository;
    }

    // no flight state or loading window check so the plan stays readable after departure
    public CargoPlanView getCargoPlan(FlightKey flightKey) {
        Optional<FlightInformation> flight = flightRepository.findByFlightKey(flightKey);
        if (flight.isEmpty()) {
            log.info("Cargo plan requested for unknown flight {}", flightKey);
            throw new UnknownFlightException(flightKey);
        }
        // a known flight without bookings has an empty plan
        CargoPlan cargoPlan =
                cargoPlanRepository
                        .findByFlightKey(flightKey)
                        .orElseGet(() -> new CargoPlan(flightKey));
        log.debug("Cargo plan for {} has {} bookings", flightKey, cargoPlan.getBookings().size());
        return new CargoPlanView(flight.get(), cargoPlan);
    }
}
