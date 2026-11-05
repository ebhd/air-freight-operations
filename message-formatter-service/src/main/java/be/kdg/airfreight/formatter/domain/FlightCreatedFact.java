package be.kdg.airfreight.formatter.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

public record FlightCreatedFact(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String arrivalAirportCode,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        Instant occurredOn) {
    public FlightCreatedFact {
        if (flightNumber == null || flightNumber.isBlank()) {
            throw new IllegalArgumentException("flight number must not be blank");
        }
        requireAirportCode(departureAirportCode);
        requireAirportCode(arrivalAirportCode);
        Objects.requireNonNull(scheduledDepartureDate, "scheduled departure date must not be null");
        Objects.requireNonNull(scheduledDeparture, "scheduled departure must not be null");
        Objects.requireNonNull(scheduledArrival, "scheduled arrival must not be null");
        Objects.requireNonNull(occurredOn, "event occurrence time must not be null");
        if (!scheduledArrival.isAfter(scheduledDeparture)) {
            throw new IllegalArgumentException("scheduled arrival must be after departure");
        }
        if (!scheduledDeparture
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .equals(scheduledDepartureDate)) {
            throw new IllegalArgumentException(
                    "scheduled departure date must match departure in UTC");
        }
    }

    private static void requireAirportCode(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("airport code must be a three-letter IATA code");
        }
    }
}
