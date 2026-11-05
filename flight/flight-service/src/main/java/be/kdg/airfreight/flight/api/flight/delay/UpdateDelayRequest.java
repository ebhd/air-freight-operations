package be.kdg.airfreight.flight.api.flight.delay;

import be.kdg.airfreight.flight.application.delay.UpdateDelayCommand;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.util.UUID;

// the command and the domain check the values so the client gets their message
public record UpdateDelayRequest(int minutes, String description) {
    UpdateDelayCommand toCommand(FlightKey flightKey, UUID delayId) {
        return new UpdateDelayCommand(flightKey, delayId, minutes, description);
    }
}
