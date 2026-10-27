package be.kdg.airfreight.flight.application.command;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.InvalidDelayException;
import java.util.UUID;

public final class UpdateDelayCommand {
    private final FlightKey flightKey;
    private final UUID delayId;
    private final int minutes;
    private final String description;

    public UpdateDelayCommand(FlightKey flightKey, UUID delayId, int minutes, String description) {
        if (flightKey == null) {
            throw new InvalidDelayException("flight key must not be null");
        }
        if (delayId == null) {
            throw new InvalidDelayException("delay id must not be null");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidDelayException("delay description must not be blank");
        }
        this.flightKey = flightKey;
        this.delayId = delayId;
        this.minutes = minutes;
        this.description = description.trim();
    }

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public UUID getDelayId() {
        return delayId;
    }

    public int getMinutes() {
        return minutes;
    }

    public String getDescription() {
        return description;
    }
}
