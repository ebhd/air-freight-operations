package be.kdg.airfreight.flight.api.flight.actualtime;

import be.kdg.airfreight.flight.application.actualtime.RecordDepartureCommand;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;

// a utc instant like 2026-11-10T20:03:00Z, the page converts brussels time before sending
public record RecordDepartureRequest(Instant actualDeparture) {
    RecordDepartureCommand toCommand(FlightKey flightKey) {
        return new RecordDepartureCommand(flightKey, actualDeparture);
    }
}
