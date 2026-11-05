package be.kdg.airfreight.flight.application.command;

import be.kdg.airfreight.flight.domain.flight.DelayType;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.InvalidDelayException;
import java.util.Locale;
import java.util.UUID;

// the refused delay change that the planner wants to replan the flight for
// delayId null means the refused change was an add, otherwise an edit of that delay
public final class ReplanFlightCommand {
    private final FlightKey flightKey;
    private final DelayType delayType;
    private final UUID delayId;
    private final int minutes;
    private final String reasonCode;
    private final String description;

    public ReplanFlightCommand(
            FlightKey flightKey,
            DelayType delayType,
            UUID delayId,
            int minutes,
            String reasonCode,
            String description) {
        if (flightKey == null) {
            throw new InvalidDelayException("flight key must not be null");
        }
        if (delayType == null) {
            throw new InvalidDelayException("delay type must be DEPARTURE or ARRIVAL");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidDelayException("delay description must not be blank");
        }
        // the reason of an existing delay cannot change so an edit ignores it
        if (delayId == null && (reasonCode == null || reasonCode.isBlank())) {
            throw new InvalidDelayException("delay reason code must not be blank");
        }
        this.flightKey = flightKey;
        this.delayType = delayType;
        this.delayId = delayId;
        this.minutes = minutes;
        this.reasonCode = reasonCode == null ? null : reasonCode.trim().toUpperCase(Locale.ROOT);
        this.description = description.trim();
    }

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public DelayType getDelayType() {
        return delayType;
    }

    public UUID getDelayId() {
        return delayId;
    }

    public int getMinutes() {
        return minutes;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getDescription() {
        return description;
    }
}
