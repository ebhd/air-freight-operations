package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.application.command.UpdateDelayCommand;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record UpdateDelayRequest(
        @Min(value = 1, message = "delay minutes must be between 1 and 720")
                @Max(value = 720, message = "delay minutes must be between 1 and 720")
                int minutes,
        @NotBlank(message = "delay description must not be blank") String description) {
    UpdateDelayCommand toCommand(FlightKey flightKey, UUID delayId) {
        return new UpdateDelayCommand(flightKey, delayId, minutes, description);
    }
}
