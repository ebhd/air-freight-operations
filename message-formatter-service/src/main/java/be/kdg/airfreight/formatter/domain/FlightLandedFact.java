package be.kdg.airfreight.formatter.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

// what the aa telegram needs: the arrival airport and the actual arrival
public record FlightLandedFact(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String arrivalAirportCode,
        String aircraftRegistration,
        Instant actualArrival,
        Instant occurredOn) {
    public FlightLandedFact {
        TelegramText.requireTelegramText(flightNumber, "flight number");
        TelegramText.requireTelegramText(aircraftRegistration, "aircraft registration");
        TelegramText.requireAirportCode(arrivalAirportCode);
        Objects.requireNonNull(scheduledDepartureDate, "scheduled departure date must not be null");
        Objects.requireNonNull(actualArrival, "ATA must not be null");
        Objects.requireNonNull(occurredOn, "event occurrence time must not be null");
    }
}
