package be.kdg.airfreight.flight.domain.flight;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;
import be.kdg.airfreight.flight.domain.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Flight {
    private final FlightKey key;
    private final String arrivalAirportCode;
    private final String aircraftRegistration;
    private final Instant scheduledDeparture;
    private final Instant scheduledArrival;
    private final FlightStatus status;
    private final List<Delay> delays;

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
        this.delays = new ArrayList<>();
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
            FlightStatus status,
            List<Delay> delays) {
        Flight flight =
                new Flight(
                        key,
                        arrivalAirportCode,
                        aircraftRegistration,
                        scheduledDeparture,
                        scheduledArrival,
                        Objects.requireNonNull(status, "flight status must not be null"));
        flight.delays.addAll(Objects.requireNonNull(delays, "delays must not be null"));
        return flight;
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

    public List<Delay> delays() {
        return Collections.unmodifiableList(delays);
    }

    public void addDelay(DelayType type, int minutes, String reasonCode, String description) {
        Objects.requireNonNull(type, "delay type must not be null");
        requireDelayChangesAllowed(type);
        delays.add(new Delay(type, minutes, reasonCode, description));
    }

    public void updateDelay(UUID delayId, int minutes, String description) {
        Delay delay = requireDelay(delayId);
        requireDelayChangesAllowed(delay.type());
        delay.update(minutes, description);
    }

    public void removeDelay(UUID delayId) {
        Delay delay = requireDelay(delayId);
        requireDelayChangesAllowed(delay.type());
        delays.remove(delay);
    }

    private Delay requireDelay(UUID delayId) {
        Objects.requireNonNull(delayId, "delay id must not be null");
        return delays.stream()
                .filter(delay -> delay.id().equals(delayId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Unknown delay " + delayId));
    }

    private void requireDelayChangesAllowed(DelayType type) {
        if (type == DelayType.DEPARTURE && status != FlightStatus.PLANNED) {
            throw new DomainRuleViolationException(
                    "departure delays can only be changed while the flight is planned");
        }

        if (type == DelayType.ARRIVAL && status == FlightStatus.LANDED) {
            throw new DomainRuleViolationException(
                    "arrival delays cannot be changed after landing");
        }
    }

    public Instant estimatedDeparture() {
        return scheduledDeparture.plus(Duration.ofMinutes(departureDelayMinutes()));
    }

    public Instant estimatedArrival() {
        int totalDelayMinutes = departureDelayMinutes() + arrivalDelayMinutes();

        return scheduledArrival.plus(Duration.ofMinutes(totalDelayMinutes));
    }

    public int departureDelayMinutes() {
        return delays.stream()
                .filter(delay -> delay.type() == DelayType.DEPARTURE)
                .mapToInt(Delay::minutes)
                .sum();
    }

    public int arrivalDelayMinutes() {
        return delays.stream()
                .filter(delay -> delay.type() == DelayType.ARRIVAL)
                .mapToInt(Delay::minutes)
                .sum();
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
