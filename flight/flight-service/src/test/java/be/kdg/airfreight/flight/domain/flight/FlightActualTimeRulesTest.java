package be.kdg.airfreight.flight.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

// flt-11 to flt-14: the rules for atd and ata, checked against a fixed now
class FlightActualTimeRulesTest {
    private static final FlightKey KEY = new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final Instant STD = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant STA = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final ActualTimePolicy FIVE_MINUTES =
            new ActualTimePolicy(Duration.ofMinutes(5));

    // ----- departure -----

    @Test
    void departureMovesTheFlightToDepartedAndKeepsTheTime() {
        Flight flight = readyToDepart();
        Instant atd = STD.plus(Duration.ofMinutes(3));

        flight.recordDeparture(atd, NOW, FIVE_MINUTES);

        assertThat(flight.status()).isEqualTo(FlightStatus.DEPARTED);
        assertThat(flight.actualDeparture()).isEqualTo(atd);
        assertThat(flight.actualArrival()).isNull();
    }

    // flt-12: exactly five minutes is still valid
    @Test
    void departureExactlyAtTheToleranceIsAccepted() {
        Flight flight = readyToDepart();

        flight.recordDeparture(STD.plus(Duration.ofMinutes(5)), NOW, FIVE_MINUTES);

        assertThat(flight.status()).isEqualTo(FlightStatus.DEPARTED);
    }

    @Test
    void departureOneSecondOverTheToleranceIsTooLateAndChangesNothing() {
        Flight flight = readyToDepart();
        Instant limit = STD.plus(Duration.ofMinutes(5));

        assertThatThrownBy(() -> flight.recordDeparture(limit.plusSeconds(1), NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .satisfies(
                        thrown -> {
                            ActualTimeRefusedException refused =
                                    (ActualTimeRefusedException) thrown;
                            assertThat(refused.refusal()).isEqualTo(ActualTimeRefusal.TOO_LATE);
                            assertThat(refused.latestAllowed()).isEqualTo(limit);
                        })
                .hasMessage(
                        "The actual departure 2026-10-19T08:05:01Z is later than the estimated"
                                + " departure 2026-10-19T08:00:00Z plus the tolerance of 5"
                                + " minutes (2026-10-19T08:05:00Z). Add a departure delay first,"
                                + " then register the departure.");
        assertThat(flight.status()).isEqualTo(FlightStatus.PLANNED);
        assertThat(flight.actualDeparture()).isNull();
    }

    // flt-12: the tolerance counts from etd, so a delay moves the limit
    @Test
    void departureDelayMovesTheLimit() {
        Flight flight = readyToDepart();
        flight.addDepartureDelay(30, "71", "Fog at departure");

        flight.recordDeparture(STD.plus(Duration.ofMinutes(35)), NOW, FIVE_MINUTES);

        assertThat(flight.status()).isEqualTo(FlightStatus.DEPARTED);
    }

    @Test
    void departureDelayDoesNotStretchTheTolerance() {
        Flight flight = readyToDepart();
        flight.addDepartureDelay(30, "71", "Fog at departure");

        assertThatThrownBy(
                        () ->
                                flight.recordDeparture(
                                        STD.plus(Duration.ofMinutes(36)), NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.TOO_LATE);
    }

    // flt-11: at most now, exactly now is fine
    @Test
    void departureEqualToNowIsAccepted() {
        Flight flight = readyToDepart();
        Instant now = STD.plus(Duration.ofMinutes(2));

        flight.recordDeparture(now, now, FIVE_MINUTES);

        assertThat(flight.actualDeparture()).isEqualTo(now);
    }

    @Test
    void departureOneSecondInTheFutureIsRefused() {
        Flight flight = readyToDepart();
        Instant now = STD.plus(Duration.ofMinutes(2));

        assertThatThrownBy(() -> flight.recordDeparture(now.plusSeconds(1), now, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.IN_THE_FUTURE);
        assertThat(flight.status()).isEqualTo(FlightStatus.PLANNED);
    }

    // no lower bound: an early departure is fine
    @Test
    void departureHoursBeforeScheduleIsAccepted() {
        Flight flight = readyToDepart();

        flight.recordDeparture(STD.minus(Duration.ofHours(2)), NOW, FIVE_MINUTES);

        assertThat(flight.status()).isEqualTo(FlightStatus.DEPARTED);
    }

    // flt-13: the cpm gate comes before the time checks and the same call works after the cpm
    @Test
    void departureNeedsTheProcessedCpmAndSucceedsOnceItIsThere() {
        Flight flight = Flight.create(KEY, "AMS", "SXBAA", STD, STA);

        assertThatThrownBy(() -> flight.recordDeparture(STD, NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .hasMessageStartingWith("Departure needs the processed CPM")
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.CPM_NOT_PROCESSED);
        assertThat(flight.status()).isEqualTo(FlightStatus.PLANNED);

        flight.recordFinalizedLoad();
        flight.recordDeparture(STD, NOW, FIVE_MINUTES);

        assertThat(flight.status()).isEqualTo(FlightStatus.DEPARTED);
    }

    // replanned is final, the new flight gets the actual times
    @Test
    void replannedFlightRefusesBothActualTimes() {
        Flight flight =
                Flight.restore(
                        KEY,
                        "AMS",
                        "SXBAA",
                        STD,
                        STA,
                        FlightStatus.REPLANNED,
                        List.of(),
                        List.of(),
                        true);

        assertThatThrownBy(() -> flight.recordDeparture(STD, NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .hasMessageContaining("This flight is REPLANNED")
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.NOT_PLANNED);
        assertThatThrownBy(() -> flight.recordArrival(STA, NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .hasMessageContaining("This flight is REPLANNED")
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.NOT_DEPARTED);
        assertThat(flight.actualDeparture()).isNull();
        assertThat(flight.actualArrival()).isNull();
    }

    // flt-14: registered once, never changed
    @Test
    void secondDepartureIsRefusedAndTheFirstTimeStays() {
        Flight flight = readyToDepart();
        flight.recordDeparture(STD, NOW, FIVE_MINUTES);

        assertThatThrownBy(() -> flight.recordDeparture(STD.plusSeconds(60), NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .hasMessage(
                        "The actual departure of this flight was already registered on"
                                + " 2026-10-19T08:00:00Z and cannot be changed.")
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.NOT_PLANNED);
        assertThat(flight.actualDeparture()).isEqualTo(STD);
    }

    @Test
    void missingDepartureTimeIsInvalidInput() {
        Flight flight = readyToDepart();

        assertThatThrownBy(() -> flight.recordDeparture(null, NOW, FIVE_MINUTES))
                .isInstanceOf(InvalidActualTimeException.class)
                .hasMessage("actual departure is required");
    }

    // flt-10: departure delays freeze at departure, arrival delays still move
    @Test
    void afterDepartureOnlyArrivalDelaysCanChange() {
        Flight flight = readyToDepart();
        flight.addDepartureDelay(10, "71", "Fog at departure");
        Delay departureDelay = flight.departureDelays().getFirst();
        flight.recordDeparture(STD.plus(Duration.ofMinutes(10)), NOW, FIVE_MINUTES);

        assertThatThrownBy(() -> flight.addDepartureDelay(5, "71", "Fog"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.updateDepartureDelay(departureDelay.id(), 20, "Fog"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.removeDepartureDelay(departureDelay.id()))
                .isInstanceOf(DomainRuleViolationException.class);
        flight.addArrivalDelay(15, "71", "Holding");

        assertThat(flight.departureDelayMinutes()).isEqualTo(10);
        assertThat(flight.arrivalDelayMinutes()).isEqualTo(15);
    }

    // ----- arrival -----

    @Test
    void arrivalMovesTheFlightToLandedAndKeepsBothTimes() {
        Flight flight = departed(STD);
        Instant ata = STA.minus(Duration.ofMinutes(2));

        flight.recordArrival(ata, NOW, FIVE_MINUTES);

        assertThat(flight.status()).isEqualTo(FlightStatus.LANDED);
        assertThat(flight.actualDeparture()).isEqualTo(STD);
        assertThat(flight.actualArrival()).isEqualTo(ata);
    }

    @Test
    void arrivalOnAPlannedFlightAsksForTheDepartureFirst() {
        Flight flight = readyToDepart();

        assertThatThrownBy(() -> flight.recordArrival(STA, NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .hasMessage(
                        "The actual arrival can only be registered after the actual departure."
                                + " Register the departure first.")
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.NOT_DEPARTED);
        assertThat(flight.status()).isEqualTo(FlightStatus.PLANNED);
    }

    // flt-11: strictly later than atd, equal is refused
    @Test
    void arrivalEqualToDepartureIsRefused() {
        Flight flight = departed(STD);

        assertThatThrownBy(() -> flight.recordArrival(STD, NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .hasMessage(
                        "The actual arrival 2026-10-19T08:00:00Z must be later than the actual"
                                + " departure 2026-10-19T08:00:00Z.")
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.NOT_AFTER_DEPARTURE);
        assertThat(flight.status()).isEqualTo(FlightStatus.DEPARTED);
    }

    @Test
    void arrivalOneSecondAfterDepartureIsAccepted() {
        Flight flight = departed(STD);

        flight.recordArrival(STD.plusSeconds(1), NOW, FIVE_MINUTES);

        assertThat(flight.status()).isEqualTo(FlightStatus.LANDED);
    }

    @Test
    void arrivalExactlyAtTheToleranceIsAccepted() {
        Flight flight = departed(STD);

        flight.recordArrival(STA.plus(Duration.ofMinutes(5)), NOW, FIVE_MINUTES);

        assertThat(flight.status()).isEqualTo(FlightStatus.LANDED);
    }

    @Test
    void arrivalOneSecondOverTheToleranceIsTooLateWithTheLimit() {
        Flight flight = departed(STD);
        Instant limit = STA.plus(Duration.ofMinutes(5));

        assertThatThrownBy(() -> flight.recordArrival(limit.plusSeconds(1), NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .hasMessageEndingWith("Add an arrival delay first, then register the arrival.")
                .satisfies(
                        thrown -> {
                            ActualTimeRefusedException refused =
                                    (ActualTimeRefusedException) thrown;
                            assertThat(refused.refusal()).isEqualTo(ActualTimeRefusal.TOO_LATE);
                            assertThat(refused.latestAllowed()).isEqualTo(limit);
                        });
        assertThat(flight.actualArrival()).isNull();
    }

    // an arrival delay after atd moves eta, so the limit for ata moves too
    @Test
    void arrivalDelayAfterDepartureMovesTheArrivalLimit() {
        Flight flight = departed(STD);
        flight.addArrivalDelay(20, "71", "Holding at arrival");

        flight.recordArrival(STA.plus(Duration.ofMinutes(25)), NOW, FIVE_MINUTES);

        assertThat(flight.status()).isEqualTo(FlightStatus.LANDED);
    }

    @Test
    void arrivalInTheFutureIsRefused() {
        Flight flight = departed(STD);

        assertThatThrownBy(() -> flight.recordArrival(NOW.plusSeconds(1), NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.IN_THE_FUTURE);
    }

    @Test
    void secondArrivalIsRefusedAndTheFirstTimeStays() {
        Flight flight = departed(STD);
        flight.recordArrival(STA, NOW, FIVE_MINUTES);

        assertThatThrownBy(() -> flight.recordArrival(STA.plusSeconds(60), NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .hasMessage(
                        "The actual arrival of this flight was already registered on"
                                + " 2026-10-19T10:00:00Z and cannot be changed.")
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.NOT_DEPARTED);
        assertThat(flight.actualArrival()).isEqualTo(STA);
    }

    @Test
    void landedFlightRefusesTheDepartureAgain() {
        Flight flight = departed(STD);
        flight.recordArrival(STA, NOW, FIVE_MINUTES);

        assertThatThrownBy(() -> flight.recordDeparture(STD, NOW, FIVE_MINUTES))
                .isInstanceOf(ActualTimeRefusedException.class)
                .extracting("refusal")
                .isEqualTo(ActualTimeRefusal.NOT_PLANNED);
    }

    @Test
    void missingArrivalTimeIsInvalidInput() {
        Flight flight = departed(STD);

        assertThatThrownBy(() -> flight.recordArrival(null, NOW, FIVE_MINUTES))
                .isInstanceOf(InvalidActualTimeException.class)
                .hasMessage("actual arrival is required");
    }

    // flt-10: all delays freeze at landing
    @Test
    void afterLandingNoDelayCanChange() {
        Flight flight = departed(STD);
        flight.addArrivalDelay(15, "71", "Holding");
        Delay arrivalDelay = flight.arrivalDelays().getFirst();
        flight.recordArrival(STA.plus(Duration.ofMinutes(15)), NOW, FIVE_MINUTES);

        assertThatThrownBy(() -> flight.addArrivalDelay(5, "71", "Holding"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.updateArrivalDelay(arrivalDelay.id(), 20, "Holding"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.removeArrivalDelay(arrivalDelay.id()))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.addDepartureDelay(5, "71", "Fog"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    // decision 2a: registering changes no estimate, so no ed or ea follows
    @Test
    void registrationsLeaveTheEstimatesAlone() {
        Flight flight = readyToDepart();
        flight.addDepartureDelay(10, "71", "Fog at departure");

        flight.recordDeparture(STD.plus(Duration.ofMinutes(12)), NOW, FIVE_MINUTES);
        flight.recordArrival(STA.plus(Duration.ofMinutes(14)), NOW, FIVE_MINUTES);

        assertThat(flight.estimatedDeparture()).isEqualTo(STD.plus(Duration.ofMinutes(10)));
        assertThat(flight.estimatedArrival()).isEqualTo(STA.plus(Duration.ofMinutes(10)));
    }

    // ----- the policy itself -----

    @Test
    void policyAddsTheToleranceToTheEstimate() {
        assertThat(FIVE_MINUTES.latestAllowed(STD)).isEqualTo(STD.plus(Duration.ofMinutes(5)));
        assertThat(new ActualTimePolicy(Duration.ZERO).latestAllowed(STD)).isEqualTo(STD);
    }

    @Test
    void policyRefusesANegativeOrMissingTolerance() {
        assertThatThrownBy(() -> new ActualTimePolicy(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("actual time tolerance must be zero or positive, was PT-1M");
        assertThatThrownBy(() -> new ActualTimePolicy(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("actual time tolerance is required");
    }

    // a planned flight whose cpm was processed
    private static Flight readyToDepart() {
        Flight flight = Flight.create(KEY, "AMS", "SXBAA", STD, STA);
        flight.recordFinalizedLoad();
        return flight;
    }

    // restored as the database would give it back after the departure
    private static Flight departed(Instant actualDeparture) {
        return Flight.restore(
                KEY,
                "AMS",
                "SXBAA",
                STD,
                STA,
                FlightStatus.DEPARTED,
                List.of(),
                List.of(),
                true,
                actualDeparture,
                null,
                null,
                false,
                null);
    }
}
