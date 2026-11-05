package be.kdg.airfreight.formatter.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

// a replanned flight and the new flight that replaces it, as flight reports it
public record FlightReplacedFact(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String newFlightNumber,
        LocalDate newScheduledDepartureDate,
        String newDepartureAirportCode,
        Instant newScheduledDeparture,
        Instant newScheduledArrival,
        Instant occurredOn) {
    public FlightReplacedFact {
        requireFlightNumber(flightNumber);
        requireFlightNumber(newFlightNumber);
        requireAirportCode(departureAirportCode);
        requireAirportCode(newDepartureAirportCode);
        Objects.requireNonNull(scheduledDepartureDate, "scheduled departure date must not be null");
        Objects.requireNonNull(
                newScheduledDepartureDate, "new scheduled departure date must not be null");
        Objects.requireNonNull(newScheduledDeparture, "new scheduled departure must not be null");
        Objects.requireNonNull(newScheduledArrival, "new scheduled arrival must not be null");
        Objects.requireNonNull(occurredOn, "event occurrence time must not be null");
        if (!newScheduledArrival.isAfter(newScheduledDeparture)) {
            throw new IllegalArgumentException("new scheduled arrival must be after departure");
        }
    }

    private static void requireFlightNumber(String flightNumber) {
        if (flightNumber == null || flightNumber.isBlank()) {
            throw new IllegalArgumentException("flight number must not be blank");
        }
    }

    private static void requireAirportCode(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("airport code must be a three-letter IATA code");
        }
    }
}
