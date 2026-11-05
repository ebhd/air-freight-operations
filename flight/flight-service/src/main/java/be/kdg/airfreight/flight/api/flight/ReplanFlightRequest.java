package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.application.command.ReplanFlightCommand;
import be.kdg.airfreight.flight.domain.flight.DelayType;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.util.UUID;

// the refused delay change: delayId null for an add, set for an edit (reasonCode then ignored)
public record ReplanFlightRequest(
        DelayType delayType, UUID delayId, int minutes, String reasonCode, String description) {
    ReplanFlightCommand toCommand(FlightKey flightKey) {
        return new ReplanFlightCommand(
                flightKey, delayType, delayId, minutes, reasonCode, description);
    }
}
