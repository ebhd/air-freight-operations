package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.util.Optional;

public interface CargoPlanRepository {
    Optional<CargoPlan> findByFlightKey(FlightKey flightKey);

    void save(CargoPlan cargoPlan);

    // the plan with its bookings, nothing happens when the flight has no plan
    void deleteByFlightKey(FlightKey flightKey);
}
