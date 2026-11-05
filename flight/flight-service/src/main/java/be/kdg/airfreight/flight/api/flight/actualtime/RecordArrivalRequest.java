package be.kdg.airfreight.flight.api.flight.actualtime;

import be.kdg.airfreight.flight.application.actualtime.RecordArrivalCommand;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;

public record RecordArrivalRequest(Instant actualArrival) {
    RecordArrivalCommand toCommand(FlightKey flightKey) {
        return new RecordArrivalCommand(flightKey, actualArrival);
    }
}
