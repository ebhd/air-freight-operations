package be.kdg.airfreight.cargo.domain;

import java.util.Optional;

public interface CargoPlanRepository {
    Optional<CargoPlan> findByFlightKey(FlightKey flightKey);

    void save(CargoPlan cargoPlan);
}
