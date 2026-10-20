package be.kdg.airfreight.cargo.domain;

import java.time.Instant;
import java.util.Objects;

public record FlightInformation(
        FlightKey flightKey,
        AirportCode arrivalAirportCode,
        FlightStatus status,
        Instant scheduledDeparture,
        Instant scheduledArrival) {
    public FlightInformation {
        Objects.requireNonNull(flightKey, "Flight key is required");
        Objects.requireNonNull(arrivalAirportCode, "Arrival airport code is required");
        Objects.requireNonNull(status, "Status is required");
        Objects.requireNonNull(scheduledDeparture, "Scheduled departure is required");
        Objects.requireNonNull(scheduledArrival, "Scheduled arrival is required");
    }

    public boolean planned() {
        return status == FlightStatus.PLANNED;
    }
}
