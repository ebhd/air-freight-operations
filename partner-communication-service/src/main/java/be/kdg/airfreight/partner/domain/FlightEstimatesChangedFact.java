package be.kdg.airfreight.partner.domain;

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
        requireTelegramText(flightNumber, "flight number");
        requireTelegramText(aircraftRegistration, "aircraft registration");
        requireAirportCode(departureAirportCode);
        requireAirportCode(arrivalAirportCode);
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

    private static void requireTelegramText(String value, String name) {
        if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException(name + " must be nonblank single-line text");
        }
    }

    private static void requireAirportCode(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("airport code must be a three-letter IATA code");
        }
    }
}
