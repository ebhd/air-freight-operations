package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.domain.flight.Delay;
import java.util.UUID;

public record DelayResponse(UUID id, int minutes, String reasonCode, String description) {
    static DelayResponse from(Delay delay) {
        return new DelayResponse(
                delay.id(), delay.minutes(), delay.reasonCode(), delay.description());
    }
}
