package be.kdg.airfreight.flight.api.flight.delay;

import be.kdg.airfreight.flight.application.delay.AddDelayCommand;
import be.kdg.airfreight.flight.domain.flight.FlightKey;

// the command and the domain check the values so the client gets their message
public record AddDelayRequest(int minutes, String reasonCode, String description) {
    AddDelayCommand toCommand(FlightKey flightKey) {
        return new AddDelayCommand(flightKey, minutes, reasonCode, description);
    }
}
