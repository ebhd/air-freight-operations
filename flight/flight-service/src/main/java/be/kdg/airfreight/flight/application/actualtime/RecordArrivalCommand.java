package be.kdg.airfreight.flight.application.actualtime;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.actualtime.InvalidActualTimeException;
import java.time.Instant;

public final class RecordArrivalCommand {
    private final FlightKey flightKey;
    private final Instant actualArrival;

    public RecordArrivalCommand(FlightKey flightKey, Instant actualArrival) {
        if (flightKey == null) {
            throw new InvalidActualTimeException("flight key must not be null");
        }
        this.flightKey = flightKey;
        this.actualArrival = actualArrival;
    }

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public Instant getActualArrival() {
        return actualArrival;
    }
}
