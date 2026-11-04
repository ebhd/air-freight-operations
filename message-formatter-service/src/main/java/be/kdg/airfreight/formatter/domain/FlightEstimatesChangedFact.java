package be.kdg.airfreight.formatter.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record FlightEstimatesChangedFact(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant previousEstimatedDeparture,
        Instant previousEstimatedArrival,
        Instant estimatedDeparture,
        Instant estimatedArrival,
        Instant occurredOn) {
    public FlightEstimatesChangedFact {
        TelegramText.requireTelegramText(flightNumber, "flight number");
        TelegramText.requireTelegramText(aircraftRegistration, "aircraft registration");
        TelegramText.requireAirportCode(departureAirportCode);
        TelegramText.requireAirportCode(arrivalAirportCode);
        Objects.requireNonNull(scheduledDepartureDate, "scheduled departure date must not be null");
        Objects.requireNonNull(previousEstimatedDeparture, "previous ETD must not be null");
        Objects.requireNonNull(previousEstimatedArrival, "previous ETA must not be null");
        Objects.requireNonNull(estimatedDeparture, "ETD must not be null");
        Objects.requireNonNull(estimatedArrival, "ETA must not be null");
        Objects.requireNonNull(occurredOn, "event occurrence time must not be null");
        if (!previousEstimatedArrival.isAfter(previousEstimatedDeparture)
                || !estimatedArrival.isAfter(estimatedDeparture)) {
            throw new IllegalArgumentException("estimated arrival must be after departure");
        }
    }

    public boolean departureChanged() {
        return !previousEstimatedDeparture.equals(estimatedDeparture);
    }

    public boolean arrivalChanged() {
        return !previousEstimatedArrival.equals(estimatedArrival);
    }
}
