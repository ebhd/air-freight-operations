package be.kdg.airfreight.flight.application.command;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.InvalidActualTimeException;
import java.time.Instant;

// the time may be null here, the flight refuses it with its own message
public final class RecordDepartureCommand {
    private final FlightKey flightKey;
    private final Instant actualDeparture;

    public RecordDepartureCommand(FlightKey flightKey, Instant actualDeparture) {
        if (flightKey == null) {
            throw new InvalidActualTimeException("flight key must not be null");
        }
        this.flightKey = flightKey;
        this.actualDeparture = actualDeparture;
    }

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public Instant getActualDeparture() {
        return actualDeparture;
    }
}
