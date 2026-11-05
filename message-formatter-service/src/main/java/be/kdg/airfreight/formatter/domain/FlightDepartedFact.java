package be.kdg.airfreight.formatter.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

// what the ad telegram needs: the departure airport and the actual departure
public record FlightDepartedFact(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String aircraftRegistration,
        Instant actualDeparture,
        Instant occurredOn) {
    public FlightDepartedFact {
        TelegramText.requireTelegramText(flightNumber, "flight number");
        TelegramText.requireTelegramText(aircraftRegistration, "aircraft registration");
        TelegramText.requireAirportCode(departureAirportCode);
        Objects.requireNonNull(scheduledDepartureDate, "scheduled departure date must not be null");
        Objects.requireNonNull(actualDeparture, "ATD must not be null");
        Objects.requireNonNull(occurredOn, "event occurrence time must not be null");
    }
}
