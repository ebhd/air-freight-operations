package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.UldCode;
import be.kdg.airfreight.cargo.domain.Weight;
import java.time.Instant;
import java.util.Objects;

public record BookUldCommand(
        String messageId,
        String flightNumber,
        int scheduledDepartureDay,
        AirportCode departureAirportCode,
        UldCode uldCode,
        Weight weight,
        AirportCode destination,
        // first receipt so a retry resolves the same date
        Instant firstReceivedAt) {
    public BookUldCommand {
        Objects.requireNonNull(messageId, "Message ID is required");
        Objects.requireNonNull(flightNumber, "Flight number is required");
        Objects.requireNonNull(departureAirportCode, "Departure airport code is required");
        Objects.requireNonNull(uldCode, "ULD code is required");
        Objects.requireNonNull(weight, "Weight is required");
        Objects.requireNonNull(destination, "Destination is required");
        Objects.requireNonNull(firstReceivedAt, "First receipt instant is required");
        if (messageId.isBlank() || messageId.length() > 255) {
            throw new IllegalArgumentException("Message ID must be 1 to 255 characters");
        }
    }
}
