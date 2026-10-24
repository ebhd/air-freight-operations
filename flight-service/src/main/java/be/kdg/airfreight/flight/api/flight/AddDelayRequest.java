package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.application.command.AddDelayCommand;
import be.kdg.airfreight.flight.domain.flight.DelayType;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddDelayRequest(
        @NotNull(message = "delay type is required") DelayType type,
        @Min(value = 1, message = "delay minutes must be between 1 and 720")
                @Max(value = 720, message = "delay minutes must be between 1 and 720")
                int minutes,
        @NotBlank(message = "delay reason code must not be blank") String reasonCode,
        @NotBlank(message = "delay description must not be blank") String description) {
    AddDelayCommand toCommand(FlightKey flightKey) {
        return new AddDelayCommand(flightKey, type, minutes, reasonCode, description);
    }
}
