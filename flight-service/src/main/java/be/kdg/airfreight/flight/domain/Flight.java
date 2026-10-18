package be.kdg.airfreight.flight.domain;

import java.time.Instant;
import java.util.Objects;

public final class Flight {
    private final FlightKey key;
    private final String arrivalAirportCode;
    private final String aircraftRegistration;
    private final Instant scheduledDeparture;
    private final Instant scheduledArrival;
    private final FlightStatus status;

    private Flight(
            FlightKey key,
            String arrivalAirportCode,
            String aircraftRegistration,
            Instant scheduledDeparture,
            Instant scheduledArrival,
            FlightStatus status) {
        this.key = Objects.requireNonNull(key, "flight key must not be null");
        this.arrivalAirportCode =
                FlightKey.requireAirportCode(arrivalAirportCode, "arrival airport code");
        this.aircraftRegistration = requireText(aircraftRegistration, "aircraft registration");
        this.scheduledDeparture =
                Objects.requireNonNull(scheduledDeparture, "scheduled departure must not be null");
        this.scheduledArrival =
                Objects.requireNonNull(scheduledArrival, "scheduled arrival must not be null");
        if (!scheduledArrival.isAfter(scheduledDeparture)) {
            throw new InvalidScheduleException(
                    "scheduled arrival must be after scheduled departure");
        }
        if (!scheduledDeparture
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .equals(key.scheduledDepartureDate())) {
            throw new InvalidScheduleException(
                    "flight key date must equal the UTC scheduled departure date");
        }
        this.status = Objects.requireNonNull(status, "flight status must not be null");
    }

    public static Flight create(
            FlightKey key,
            String arrivalAirportCode,
            String aircraftRegistration,
            Instant scheduledDeparture,
            Instant scheduledArrival) {
        return new Flight(
                key,
                arrivalAirportCode,
                aircraftRegistration,
                scheduledDeparture,
                scheduledArrival,
                FlightStatus.PLANNED);
    }

    public static Flight restore(
            FlightKey key,
            String arrivalAirportCode,
            String aircraftRegistration,
            Instant scheduledDeparture,
            Instant scheduledArrival,
            FlightStatus status) {
        return new Flight(
                key,
                arrivalAirportCode,
                aircraftRegistration,
                scheduledDeparture,
                scheduledArrival,
                Objects.requireNonNull(status, "flight status must not be null"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidScheduleException(name + " must not be blank");
        }
        return value.trim();
    }

    public FlightKey key() {
        return key;
    }

    public String arrivalAirportCode() {
        return arrivalAirportCode;
    }

    public String aircraftRegistration() {
        return aircraftRegistration;
    }

    public Instant scheduledDeparture() {
        return scheduledDeparture;
    }

    public Instant scheduledArrival() {
        return scheduledArrival;
    }

    public FlightStatus status() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Flight flight && key.equals(flight.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
