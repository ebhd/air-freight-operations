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
    // all delays of a flight together are at most 12 hours (coach), same number as one delay
    public static final int MAX_TOTAL_DELAY_MINUTES = Delay.MAX_MINUTES;

    private final FlightKey key;
    private final String arrivalAirportCode;
    private final String aircraftRegistration;
    private final Instant scheduledDeparture;
    private final Instant scheduledArrival;
    // changes when the actual departure or arrival is registered
    private FlightStatus status;
    private final List<Delay> departureDelays;
    private final List<Delay> arrivalDelays;
    // true once the cpm of the finalized load from cargo is received and processed
    private boolean finalizedLoadProcessed;
    // null until registered, then never changed
    private Instant actualDeparture;
    private Instant actualArrival;

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
        return restore(
                key,
                arrivalAirportCode,
                aircraftRegistration,
                scheduledDeparture,
                scheduledArrival,
                status,
                departureDelays,
                arrivalDelays,
                finalizedLoadProcessed,
                null,
                null);
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
            boolean finalizedLoadProcessed,
            Instant actualDeparture,
            Instant actualArrival) {
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
        flight.actualDeparture = actualDeparture;
        flight.actualArrival = actualArrival;
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

    public Instant actualDeparture() {
        return actualDeparture;
    }

    public Instant actualArrival() {
        return actualArrival;
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
            throw new ActualTimeRefusedException(
                    ActualTimeRefusal.CPM_NOT_PROCESSED,
                    "Departure needs the processed CPM of the finalized load from Cargo Loading."
                            + " If the load was finalized a moment ago, wait a few seconds and try"
                            + " again.");
        }
    }

    // now and the tolerance come from the service so this stays testable with a fixed clock
    // the checks go from the flight's state to the entered value: status, cpm, then the time
    public void recordDeparture(Instant actualDeparture, Instant now, ActualTimePolicy policy) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(policy, "actual time policy must not be null");
        if (status != FlightStatus.PLANNED) {
            throw new ActualTimeRefusedException(
                    ActualTimeRefusal.NOT_PLANNED,
                    this.actualDeparture != null
                            ? alreadyRegistered("departure", this.actualDeparture)
                            : "The actual departure can only be registered for a planned flight."
                                    + " This flight is "
                                    + status
                                    + ".");
        }
        requireFinalizedLoadProcessed();
        requireActualTime(actualDeparture, "actual departure");
        requireNotInTheFuture(actualDeparture, now, "actual departure");
        Instant latest = policy.latestAllowed(estimatedDeparture());
        // isAfter so exactly the limit is still accepted
        if (actualDeparture.isAfter(latest)) {
            throw new ActualTimeRefusedException(
                    ActualTimeRefusal.TOO_LATE,
                    tooLate(
                            "departure",
                            actualDeparture,
                            estimatedDeparture(),
                            policy,
                            latest,
                            "Add a departure delay first, then register the departure."),
                    latest);
        }
        this.actualDeparture = actualDeparture;
        this.status = FlightStatus.DEPARTED;
    }

    public void recordArrival(Instant actualArrival, Instant now, ActualTimePolicy policy) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(policy, "actual time policy must not be null");
        if (status != FlightStatus.DEPARTED) {
            throw new ActualTimeRefusedException(
                    ActualTimeRefusal.NOT_DEPARTED, notDepartedReason());
        }
        requireActualTime(actualArrival, "actual arrival");
        requireNotInTheFuture(actualArrival, now, "actual arrival");
        // equal is refused: the flight must have been in the air
        if (!actualArrival.isAfter(actualDeparture)) {
            throw new ActualTimeRefusedException(
                    ActualTimeRefusal.NOT_AFTER_DEPARTURE,
                    "The actual arrival "
                            + actualArrival
                            + " must be later than the actual departure "
                            + actualDeparture
                            + ".");
        }
        Instant latest = policy.latestAllowed(estimatedArrival());
        if (actualArrival.isAfter(latest)) {
            throw new ActualTimeRefusedException(
                    ActualTimeRefusal.TOO_LATE,
                    tooLate(
                            "arrival",
                            actualArrival,
                            estimatedArrival(),
                            policy,
                            latest,
                            "Add an arrival delay first, then register the arrival."),
                    latest);
        }
        this.actualArrival = actualArrival;
        this.status = FlightStatus.LANDED;
    }

    private String notDepartedReason() {
        if (status == FlightStatus.PLANNED) {
            return "The actual arrival can only be registered after the actual departure."
                    + " Register the departure first.";
        }
        if (actualArrival != null) {
            return alreadyRegistered("arrival", actualArrival);
        }
        return "The actual arrival can only be registered for a departed flight. This flight is "
                + status
                + ".";
    }

    private static String alreadyRegistered(String name, Instant registered) {
        return "The actual "
                + name
                + " of this flight was already registered on "
                + registered
                + " and cannot be changed.";
    }

    private static void requireActualTime(Instant value, String name) {
        if (value == null) {
            throw new InvalidActualTimeException(name + " is required");
        }
    }

    private static void requireNotInTheFuture(Instant value, Instant now, String name) {
        if (value.isAfter(now)) {
            throw new ActualTimeRefusedException(
                    ActualTimeRefusal.IN_THE_FUTURE,
                    "The "
                            + name
                            + " "
                            + value
                            + " is in the future (now "
                            + now
                            + "). An actual time can be at most now.");
        }
    }

    // the tolerance is printed in whole minutes, the unit the course and the page use
    private static String tooLate(
            String name,
            Instant actual,
            Instant estimate,
            ActualTimePolicy policy,
            Instant latest,
            String advice) {
        return "The actual "
                + name
                + " "
                + actual
                + " is later than the estimated "
                + name
                + " "
                + estimate
                + " plus the tolerance of "
                + policy.tolerance().toMinutes()
                + " minutes ("
                + latest
                + "). "
                + advice;
    }

    public void addDepartureDelay(int minutes, String reasonCode, String description) {
        requireDepartureDelayChangesAllowed();
        // the delay validates its own minutes first so 721 stays a bad request
        Delay delay = new Delay(minutes, reasonCode, description);
        requireTotalWithinLimit(totalDelayMinutes() + delay.minutes());
        departureDelays.add(delay);
    }

    public void updateDepartureDelay(UUID delayId, int minutes, String description) {
        Delay delay = requireDelay(departureDelays, delayId);
        requireDepartureDelayChangesAllowed();
        // check before update because update changes the delay in place
        int validatedMinutes = Delay.requireMinutes(minutes);
        requireTotalWithinLimit(totalDelayMinutes() - delay.minutes() + validatedMinutes);
        delay.update(validatedMinutes, description);
    }

    public void removeDepartureDelay(UUID delayId) {
        Delay delay = requireDelay(departureDelays, delayId);
        requireDepartureDelayChangesAllowed();
        departureDelays.remove(delay);
    }

    public void addArrivalDelay(int minutes, String reasonCode, String description) {
        requireArrivalDelayChangesAllowed();
        Delay delay = new Delay(minutes, reasonCode, description);
        requireTotalWithinLimit(totalDelayMinutes() + delay.minutes());
        arrivalDelays.add(delay);
    }

    public void updateArrivalDelay(UUID delayId, int minutes, String description) {
        Delay delay = requireDelay(arrivalDelays, delayId);
        requireArrivalDelayChangesAllowed();
        int validatedMinutes = Delay.requireMinutes(minutes);
        requireTotalWithinLimit(totalDelayMinutes() - delay.minutes() + validatedMinutes);
        delay.update(validatedMinutes, description);
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

    // removing a delay always lowers the total so only add and edit check it
    // only a planned flight can be replanned instead
    private void requireTotalWithinLimit(int totalAfterChange) {
        if (totalAfterChange > MAX_TOTAL_DELAY_MINUTES) {
            throw new TotalDelayExceededException(totalAfterChange, status == FlightStatus.PLANNED);
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

    public int totalDelayMinutes() {
        return departureDelayMinutes() + arrivalDelayMinutes();
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
