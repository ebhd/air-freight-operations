package be.kdg.airfreight.flight.domain.flight;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

// what a replanned flight remembers: the new schedule and the delay change that caused it
public record Replanning(
        String newFlightNumber,
        Instant newScheduledDeparture,
        Instant newScheduledArrival,
        DelayType delayType,
        int minutes,
        String reasonCode,
        String description,
        Instant replannedAt) {
    public Replanning {
        Objects.requireNonNull(newFlightNumber, "new flight number must not be null");
        Objects.requireNonNull(newScheduledDeparture, "new scheduled departure must not be null");
        Objects.requireNonNull(newScheduledArrival, "new scheduled arrival must not be null");
        Objects.requireNonNull(delayType, "delay type must not be null");
        Objects.requireNonNull(reasonCode, "reason code must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(replannedAt, "replanned at must not be null");
    }

    // the key date is the utc date of the std, like every flight
    public LocalDate newScheduledDepartureDate() {
        return newScheduledDeparture.atZone(ZoneOffset.UTC).toLocalDate();
    }

    public FlightKey newKey(String departureAirportCode) {
        return new FlightKey(newFlightNumber, newScheduledDepartureDate(), departureAirportCode);
    }
}
