package be.kdg.airfreight.flight.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;
import be.kdg.airfreight.flight.domain.flight.replanning.Replanning;
import be.kdg.airfreight.flight.domain.flight.replanning.ReplanningRefusal;
import be.kdg.airfreight.flight.domain.flight.replanning.ReplanningRefusedException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// the example of the plan: HJ500 BRU-AMS, std 20:00Z, sta 22:00Z, delays 400 + 200
class FlightReplanningTest {
    private static final FlightKey KEY = new FlightKey("HJ500", LocalDate.of(2026, 11, 10), "BRU");
    private static final Instant STD = Instant.parse("2026-11-10T20:00:00Z");
    private static final Instant STA = Instant.parse("2026-11-10T22:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-10T14:03:12Z");

    private final Delay departure = Delay.restore(UUID.randomUUID(), 400, "71", "Fog");
    private final Delay arrival = Delay.restore(UUID.randomUUID(), 200, "71", "Holding");

    @Test
    void departureRequestShiftsBothTimesAndGivesTheNextDaysKey() {
        Flight flight = planned();

        Replanning replanning =
                flight.replan(
                        DelayType.DEPARTURE, null, 140, "93", "Hydraulic leak", "HJ500A", NOW);

        assertThat(flight.status()).isEqualTo(FlightStatus.REPLANNED);
        assertThat(flight.replanning()).contains(replanning);
        assertThat(replanning.newScheduledDeparture())
                .isEqualTo(Instant.parse("2026-11-11T05:00:00Z"));
        assertThat(replanning.newScheduledArrival())
                .isEqualTo(Instant.parse("2026-11-11T10:20:00Z"));
        assertThat(replanning.newKey("BRU"))
                .isEqualTo(new FlightKey("HJ500A", LocalDate.of(2026, 11, 11), "BRU"));
        assertThat(replanning.delayType()).isEqualTo(DelayType.DEPARTURE);
        assertThat(replanning.minutes()).isEqualTo(140);
        assertThat(replanning.reasonCode()).isEqualTo("93");
        assertThat(replanning.description()).isEqualTo("Hydraulic leak");
        assertThat(replanning.replannedAt()).isEqualTo(NOW);
    }

    @Test
    void arrivalRequestMovesOnlyTheArrival() {
        Flight flight = planned();

        Replanning replanning =
                flight.replan(DelayType.ARRIVAL, null, 140, "93", "Holding", "HJ500A", NOW);

        assertThat(replanning.newScheduledDeparture())
                .isEqualTo(Instant.parse("2026-11-11T02:40:00Z"));
        assertThat(replanning.newScheduledArrival())
                .isEqualTo(Instant.parse("2026-11-11T10:20:00Z"));
    }

    // an edit counts the difference and keeps the reason of the edited delay
    @Test
    void editRequestUsesTheNewMinutesAndTheOldReason() {
        Flight flight = planned();

        Replanning replanning =
                flight.replan(
                        DelayType.DEPARTURE,
                        departure.id(),
                        540,
                        "99",
                        "Longer fog",
                        "HJ500A",
                        NOW);

        assertThat(replanning.minutes()).isEqualTo(540);
        assertThat(replanning.reasonCode()).isEqualTo("71");
        assertThat(replanning.newScheduledDeparture())
                .isEqualTo(Instant.parse("2026-11-11T05:00:00Z"));
        assertThat(replanning.newScheduledArrival())
                .isEqualTo(Instant.parse("2026-11-11T10:20:00Z"));
    }

    @Test
    void delaysAndEstimatesStayUnchangedAfterReplanning() {
        Flight flight = planned();
        Instant departureBefore = flight.estimatedDeparture();
        Instant arrivalBefore = flight.estimatedArrival();

        flight.replan(DelayType.DEPARTURE, null, 140, "93", "Hydraulic leak", "HJ500A", NOW);

        assertThat(flight.departureDelays()).containsExactly(departure);
        assertThat(flight.arrivalDelays()).containsExactly(arrival);
        assertThat(flight.estimatedDeparture()).isEqualTo(departureBefore);
        assertThat(flight.estimatedArrival()).isEqualTo(arrivalBefore);
    }

    @Test
    void proposalChangesNothing() {
        Flight flight = planned();

        flight.proposeReplanning(
                DelayType.DEPARTURE, null, 140, "93", "Hydraulic leak", "HJ500A", NOW);

        assertThat(flight.status()).isEqualTo(FlightStatus.PLANNED);
        assertThat(flight.replanning()).isEmpty();
    }

    @Test
    void totalWithinTwelveHoursDoesNotNeedReplanning() {
        Flight flight = planned();

        assertThatThrownBy(
                        () ->
                                flight.replan(
                                        DelayType.DEPARTURE,
                                        null,
                                        120,
                                        "93",
                                        "Leak",
                                        "HJ500A",
                                        NOW))
                .isInstanceOfSatisfying(
                        ReplanningRefusedException.class,
                        refused ->
                                assertThat(refused.refusal())
                                        .isEqualTo(ReplanningRefusal.REPLANNING_NOT_NEEDED));
        assertThat(flight.status()).isEqualTo(FlightStatus.PLANNED);
    }

    @Test
    void onlyAPlannedFlightCanBeReplanned() {
        Flight departed =
                Flight.restore(
                        KEY, "AMS", "SXBAA", STD, STA, FlightStatus.DEPARTED, List.of(), List.of());

        // the status is checked first so even an invalid request gets this answer
        assertThatThrownBy(
                        () ->
                                departed.replan(
                                        DelayType.ARRIVAL, null, 720, "93", "Hold", "HJ500A", NOW))
                .isInstanceOfSatisfying(
                        ReplanningRefusedException.class,
                        refused ->
                                assertThat(refused.refusal())
                                        .isEqualTo(ReplanningRefusal.NOT_PLANNED));
    }

    @Test
    void aReplannedFlightCannotBeReplannedAgainOrGetDelays() {
        Flight flight = planned();
        flight.replan(DelayType.DEPARTURE, null, 140, "93", "Hydraulic leak", "HJ500A", NOW);

        assertThatThrownBy(
                        () ->
                                flight.replan(
                                        DelayType.DEPARTURE,
                                        null,
                                        140,
                                        "93",
                                        "Leak",
                                        "HJ500B",
                                        NOW))
                .isInstanceOf(ReplanningRefusedException.class);
        assertThatThrownBy(() -> flight.addArrivalDelay(10, "71", "Holding"))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessage("this flight was replanned, change the delays of the new flight");
        assertThatThrownBy(() -> flight.addDepartureDelay(10, "71", "Fog"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.removeArrivalDelay(arrival.id()))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.recordFinalizedLoad())
                .isInstanceOf(DomainRuleViolationException.class);
    }

    // sta 22:00 + 1300 min of delays would end 24 h 40 min after the new std
    @Test
    void newFlightLongerThanTwentyFourHoursCannotBeExpressedInAnSsm() {
        Flight flight =
                Flight.restore(
                        KEY,
                        "AMS",
                        "SXBAA",
                        STD,
                        STA,
                        FlightStatus.PLANNED,
                        List.of(),
                        List.of(Delay.restore(UUID.randomUUID(), 720, "71", "Holding")));

        assertThatThrownBy(
                        () ->
                                flight.replan(
                                        DelayType.ARRIVAL, null, 640, "93", "Hold", "HJ500A", NOW))
                .isInstanceOfSatisfying(
                        ReplanningRefusedException.class,
                        refused ->
                                assertThat(refused.refusal())
                                        .isEqualTo(ReplanningRefusal.SCHEDULE_NOT_EXPRESSIBLE));
    }

    @Test
    void exactlyTwentyFourHoursIsStillExpressible() {
        Flight flight =
                Flight.restore(
                        KEY,
                        "AMS",
                        "SXBAA",
                        STD,
                        STA,
                        FlightStatus.PLANNED,
                        List.of(),
                        List.of(Delay.restore(UUID.randomUUID(), 720, "71", "Holding")));

        Replanning replanning =
                flight.replan(DelayType.ARRIVAL, null, 600, "93", "Hold", "HJ500A", NOW);

        assertThat(replanning.newScheduledArrival())
                .isEqualTo(replanning.newScheduledDeparture().plusSeconds(24 * 3600));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 721})
    void invalidMinutesAreInvalidInput(int minutes) {
        Flight flight = planned();

        assertThatThrownBy(
                        () ->
                                flight.replan(
                                        DelayType.DEPARTURE,
                                        null,
                                        minutes,
                                        "93",
                                        "Leak",
                                        "HJ500A",
                                        NOW))
                .isInstanceOf(InvalidDelayException.class);
        assertThat(flight.status()).isEqualTo(FlightStatus.PLANNED);
    }

    @Test
    void blankDescriptionAndMissingReasonAreInvalidInput() {
        Flight flight = planned();

        assertThatThrownBy(
                        () ->
                                flight.replan(
                                        DelayType.DEPARTURE, null, 140, "93", " ", "HJ500A", NOW))
                .isInstanceOf(InvalidDelayException.class);
        assertThatThrownBy(
                        () ->
                                flight.replan(
                                        DelayType.DEPARTURE,
                                        null,
                                        140,
                                        null,
                                        "Leak",
                                        "HJ500A",
                                        NOW))
                .isInstanceOf(InvalidDelayException.class);
    }

    @Test
    void unknownDelayIdIsNotFound() {
        Flight flight = planned();

        assertThatThrownBy(
                        () ->
                                flight.replan(
                                        DelayType.DEPARTURE,
                                        UUID.randomUUID(),
                                        540,
                                        null,
                                        "Leak",
                                        "HJ500A",
                                        NOW))
                .isInstanceOf(be.kdg.airfreight.flight.domain.NotFoundException.class);
    }

    @Test
    void replacementIsRecordedOnce() {
        Flight flight = planned();
        assertThatThrownBy(flight::recordReplacementCreated)
                .isInstanceOf(DomainRuleViolationException.class);
        flight.replan(DelayType.DEPARTURE, null, 140, "93", "Hydraulic leak", "HJ500A", NOW);

        assertThat(flight.recordReplacementCreated()).isTrue();
        assertThat(flight.recordReplacementCreated()).isFalse();
        assertThat(flight.replacementCreated()).isTrue();
    }

    @Test
    void theBackLinkIsSetOnce() {
        Flight replacement =
                Flight.create(
                        new FlightKey("HJ500A", LocalDate.of(2026, 11, 11), "BRU"),
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-11-11T05:00:00Z"),
                        Instant.parse("2026-11-11T10:20:00Z"));
        assertThat(replacement.replaces()).isEmpty();

        replacement.recordReplaces(KEY);

        assertThat(replacement.replaces()).contains(KEY);
        assertThatThrownBy(() -> replacement.recordReplaces(KEY))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    private Flight planned() {
        return Flight.restore(
                KEY,
                "AMS",
                "SXBAA",
                STD,
                STA,
                FlightStatus.PLANNED,
                List.of(departure),
                List.of(arrival));
    }
}
