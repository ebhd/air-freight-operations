package be.kdg.airfreight.flight.application.command;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.util.UUID;

public final class UpdateDelayCommand {
    private final FlightKey flightKey;
    private final UUID delayId;
    private final int minutes;
    private final String description;

    public UpdateDelayCommand(FlightKey flightKey, UUID delayId, int minutes, String description) {
        this.flightKey = flightKey;
        this.delayId = delayId;
        this.minutes = minutes;
        this.description = description;
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
