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
    private final List<Delay> departureDelays;
    private final List<Delay> arrivalDelays;
    // true once the cpm of the finalized load from cargo is received and processed
    private boolean finalizedLoadProcessed;

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
        this.departureDelays = new ArrayList<>();
        this.arrivalDelays = new ArrayList<>();
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
            List<Delay> departureDelays,
            List<Delay> arrivalDelays) {
        return restore(
                key,
                arrivalAirportCode,
                aircraftRegistration,
                scheduledDeparture,
                scheduledArrival,
                status,
                departureDelays,
                arrivalDelays,
                false);
    }

    public static Flight restore(
            FlightKey key,
            String arrivalAirportCode,
            String aircraftRegistration,
            Instant scheduledDeparture,
            Instant scheduledArrival,
            FlightStatus status,
            List<Delay> departureDelays,
            List<Delay> arrivalDelays,
            boolean finalizedLoadProcessed) {
        Flight flight =
                new Flight(
                        key,
                        arrivalAirportCode,
                        aircraftRegistration,
                        scheduledDeparture,
                        scheduledArrival,
                        Objects.requireNonNull(status, "flight status must not be null"));
        flight.departureDelays.addAll(
                Objects.requireNonNull(departureDelays, "departure delays must not be null"));
        flight.arrivalDelays.addAll(
                Objects.requireNonNull(arrivalDelays, "arrival delays must not be null"));
        flight.finalizedLoadProcessed = finalizedLoadProcessed;
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

    public List<Delay> departureDelays() {
        return Collections.unmodifiableList(departureDelays);
    }

    public List<Delay> arrivalDelays() {
        return Collections.unmodifiableList(arrivalDelays);
    }

    public boolean finalizedLoadProcessed() {
        return finalizedLoadProcessed;
    }

    // false when the cpm was already processed so a second delivery has no effect
    public boolean recordFinalizedLoad() {
        if (finalizedLoadProcessed) {
            return false;
        }
        if (status != FlightStatus.PLANNED) {
            throw new DomainRuleViolationException(
                    "a finalized load can only be recorded while the flight is planned");
        }
        finalizedLoadProcessed = true;
        return true;
    }

    // the departure gate for registering atd in issue 28
    public void requireFinalizedLoadProcessed() {
        if (!finalizedLoadProcessed) {
            throw new DomainRuleViolationException(
                    "departure needs the processed CPM of the finalized load from Cargo Loading");
        }
    }

    public void addDepartureDelay(int minutes, String reasonCode, String description) {
        requireDepartureDelayChangesAllowed();
        departureDelays.add(new Delay(minutes, reasonCode, description));
    }

    public void updateDepartureDelay(UUID delayId, int minutes, String description) {
        Delay delay = requireDelay(departureDelays, delayId);
        requireDepartureDelayChangesAllowed();
        delay.update(minutes, description);
    }

    public void removeDepartureDelay(UUID delayId) {
        Delay delay = requireDelay(departureDelays, delayId);
        requireDepartureDelayChangesAllowed();
        departureDelays.remove(delay);
    }

    public void addArrivalDelay(int minutes, String reasonCode, String description) {
        requireArrivalDelayChangesAllowed();
        arrivalDelays.add(new Delay(minutes, reasonCode, description));
    }

    public void updateArrivalDelay(UUID delayId, int minutes, String description) {
        Delay delay = requireDelay(arrivalDelays, delayId);
        requireArrivalDelayChangesAllowed();
        delay.update(minutes, description);
    }

    public void removeArrivalDelay(UUID delayId) {
        Delay delay = requireDelay(arrivalDelays, delayId);
        requireArrivalDelayChangesAllowed();
        arrivalDelays.remove(delay);
    }

    private Delay requireDelay(List<Delay> delays, UUID delayId) {
        Objects.requireNonNull(delayId, "delay id must not be null");
        for (Delay delay : delays) {
            if (delay.id().equals(delayId)) {
                return delay;
            }
        }
        throw new NotFoundException("Unknown delay " + delayId);
    }

    private void requireDepartureDelayChangesAllowed() {
        if (status != FlightStatus.PLANNED) {
            throw new DomainRuleViolationException(
                    "departure delays can only be changed while the flight is planned");
        }
    }

    private void requireArrivalDelayChangesAllowed() {
        if (status == FlightStatus.LANDED) {
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
        return departureDelays.stream().mapToInt(Delay::minutes).sum();
    }

    public int arrivalDelayMinutes() {
        return arrivalDelays.stream().mapToInt(Delay::minutes).sum();
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
