package be.kdg.airfreight.cargo.domain.flight;

import java.util.Map;
import java.util.Set;

// cargos own copy of the flight states
public enum FlightStatus {
    PLANNED,
    DEPARTED,
    LANDED,
    // the flight ended because its delays passed 12 hours, a new flight replaces it
    REPLANNED;

    // the statuses a flight can come from: a flight never goes back, so the copy only moves
    // forward, add a new status here with the statuses it may follow
    private static final Map<FlightStatus, Set<FlightStatus>> PREDECESSORS =
            Map.of(
                    PLANNED, Set.of(),
                    DEPARTED, Set.of(PLANNED),
                    LANDED, Set.of(PLANNED, DEPARTED),
                    REPLANNED, Set.of(PLANNED));

    public Set<FlightStatus> predecessors() {
        return PREDECESSORS.get(this);
    }
}
