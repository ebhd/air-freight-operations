package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckReport;

// a hard check failed so the api answers 409 with the whole report
public class LoadNotFinalizedException extends RuntimeException {
    private final FlightKey flightKey;
    private final LoadCheckReport report;

    public LoadNotFinalizedException(FlightKey flightKey, LoadCheckReport report) {
        super("Load of flight " + flightKey + " is not finalized: a hard check failed");
        this.flightKey = flightKey;
        this.report = report;
    }

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public LoadCheckReport getReport() {
        return report;
    }
}
