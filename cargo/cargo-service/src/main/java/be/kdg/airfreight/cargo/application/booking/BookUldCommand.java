package be.kdg.airfreight.cargo.application.booking;

import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.time.Instant;

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
        if (messageId == null) {
            throw new InvalidCargoValueException("Message ID is required");
        }
        if (flightNumber == null) {
            throw new InvalidCargoValueException("Flight number is required");
        }
        if (departureAirportCode == null) {
            throw new InvalidCargoValueException("Departure airport code is required");
        }
        if (uldCode == null) {
            throw new InvalidCargoValueException("ULD code is required");
        }
        if (weight == null) {
            throw new InvalidCargoValueException("Weight is required");
        }
        if (destination == null) {
            throw new InvalidCargoValueException("Destination is required");
        }
        if (firstReceivedAt == null) {
            throw new InvalidCargoValueException("First receipt instant is required");
        }
        if (messageId.isBlank() || messageId.length() > 255) {
            throw new InvalidCargoValueException("Message ID must be 1 to 255 characters");
        }
    }
}
