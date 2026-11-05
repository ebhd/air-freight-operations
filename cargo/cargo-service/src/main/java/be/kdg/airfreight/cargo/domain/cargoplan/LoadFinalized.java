package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckReport;
import java.time.Instant;
import java.util.List;

// domain event: the frozen load with only the positioned ulds and the report it passed with
public record LoadFinalized(
        FlightKey flightKey, Instant finalizedAt, List<FinalizedUld> ulds, LoadCheckReport report) {
    public LoadFinalized {
        ulds = List.copyOf(ulds);
    }

    // a copy of one positioned booking so later code cannot change the snapshot
    public record FinalizedUld(
            UldCode code, LoadingPosition position, Weight weight, AirportCode destination) {}
}
