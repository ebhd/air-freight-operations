package be.kdg.airfreight.cargo.domain;

import java.util.Optional;

/** Storage boundary; implementations must protect per-flight booking uniqueness. */
public interface CargoPlanRepository {
    Optional<CargoPlan> findByFlightKey(FlightKey flightKey);

    void save(CargoPlan cargoPlan);
}
