package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.DelayType;
import java.util.UUID;

public record DelayResponse(
        UUID id, DelayType type, int minutes, String reasonCode, String description) {
    static DelayResponse from(Delay delay) {
        return new DelayResponse(
                delay.id(), delay.type(), delay.minutes(), delay.reasonCode(), delay.description());
    }
}
