package be.kdg.airfreight.flight.application.command;

import be.kdg.airfreight.flight.domain.flight.DelayType;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.InvalidDelayException;
import java.util.Locale;

public final class AddDelayCommand {
    private final FlightKey flightKey;
    private final DelayType type;
    private final int minutes;
    private final String reasonCode;
    private final String description;

    public AddDelayCommand(
            FlightKey flightKey,
            DelayType type,
            int minutes,
            String reasonCode,
            String description) {
        if (flightKey == null) {
            throw new InvalidDelayException("flight key must not be null");
        }
        if (type == null) {
            throw new InvalidDelayException("delay type must not be null");
        }
        this.flightKey = flightKey;
        this.type = type;
        this.minutes = minutes;
        this.reasonCode = requireText(reasonCode, "delay reason code").toUpperCase(Locale.ROOT);
        this.description = requireText(description, "delay description");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidDelayException(name + " must not be blank");
        }
        return value.trim();
    }

    public FlightKey getFlightKey() {
        return flightKey;
    }

    public DelayType getType() {
        return type;
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
