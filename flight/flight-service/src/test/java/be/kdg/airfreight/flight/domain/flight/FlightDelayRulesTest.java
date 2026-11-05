package be.kdg.airfreight.flight.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlightDelayRulesTest {
    private static final FlightKey KEY = new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final Instant STD = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant STA = Instant.parse("2026-10-19T10:00:00Z");

    // the flt-09 example from the assignment
    @Test
    void estimatesAddDepartureDelaysToBothTimesAndArrivalDelaysOnlyToArrival() {
        Flight flight = planned();

        flight.addDepartureDelay(10, "71", "Fog at departure");
        flight.addDepartureDelay(20, "63", "Late crew");
        flight.addArrivalDelay(15, "71", "Holding at arrival");

        assertThat(flight.estimatedDeparture()).isEqualTo(STD.plus(Duration.ofMinutes(30)));
        assertThat(flight.estimatedArrival()).isEqualTo(STA.plus(Duration.ofMinutes(45)));
        assertThat(flight.scheduledDeparture()).isEqualTo(STD);
        assertThat(flight.scheduledArrival()).isEqualTo(STA);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 720})
    void oneAndSevenHundredTwentyMinutesAreAccepted(int minutes) {
        Flight flight = planned();

        flight.addDepartureDelay(minutes, "71", "Fog at departure");

        assertThat(flight.departureDelayMinutes()).isEqualTo(minutes);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 721})
    void zeroAndSevenHundredTwentyOneMinutesAreRefused(int minutes) {
        Flight flight = planned();

        assertThatThrownBy(() -> flight.addDepartureDelay(minutes, "71", "Fog at departure"))
                .isInstanceOf(InvalidDelayException.class)
                .hasMessage("delay minutes must be between 1 and 720");
        assertThat(flight.departureDelays()).isEmpty();
        assertThat(flight.arrivalDelays()).isEmpty();
    }

    @Test
    void invalidEditChangesNothing() {
        Delay delay = delay(20, "Fog at departure");
        Flight flight = withStatus(FlightStatus.PLANNED, List.of(delay), List.of());

        assertThatThrownBy(() -> flight.updateDepartureDelay(delay.id(), 721, "Longer fog"))
                .isInstanceOf(InvalidDelayException.class);

        assertThat(flight.departureDelays().getFirst().minutes()).isEqualTo(20);
        assertThat(flight.departureDelays().getFirst().description()).isEqualTo("Fog at departure");
    }

    @Test
    void departedFlightRefusesDepartureDelayChanges() {
        Delay departure = delay(20, "Fog at departure");
        Flight flight = withStatus(FlightStatus.DEPARTED, List.of(departure), List.of());

        assertThatThrownBy(() -> flight.addDepartureDelay(10, "71", "Fog at departure"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.updateDepartureDelay(departure.id(), 30, "Longer fog"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.removeDepartureDelay(departure.id()))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThat(flight.departureDelays()).containsExactly(departure);
        assertThat(flight.arrivalDelays()).isEmpty();
        assertThat(flight.departureDelayMinutes()).isEqualTo(20);
    }

    @Test
    void departedFlightStillAcceptsArrivalDelayChanges() {
        Delay arrival = delay(15, "Holding");
        Flight flight = withStatus(FlightStatus.DEPARTED, List.of(), List.of(arrival));

        flight.addArrivalDelay(10, "71", "Holding at arrival");
        flight.updateArrivalDelay(arrival.id(), 25, "Longer holding");

        assertThat(flight.arrivalDelayMinutes()).isEqualTo(35);
        flight.removeArrivalDelay(arrival.id());
        assertThat(flight.arrivalDelayMinutes()).isEqualTo(10);
    }

    @Test
    void landedFlightRefusesEveryDelayChange() {
        Delay departure = delay(20, "Fog at departure");
        Delay arrival = delay(15, "Holding");
        Flight flight = withStatus(FlightStatus.LANDED, List.of(departure), List.of(arrival));

        assertThatThrownBy(() -> flight.addArrivalDelay(10, "71", "Holding"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.addDepartureDelay(10, "71", "Fog"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.updateArrivalDelay(arrival.id(), 30, "Longer holding"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.removeArrivalDelay(arrival.id()))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.removeDepartureDelay(departure.id()))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThat(flight.departureDelays()).containsExactly(departure);
        assertThat(flight.arrivalDelays()).containsExactly(arrival);
    }

    @Test
    void removingTheLastDelayReturnsTheEstimatesToTheSchedule() {
        Delay delay = delay(20, "Fog at departure");
        Flight flight = withStatus(FlightStatus.PLANNED, List.of(delay), List.of());

        flight.removeDepartureDelay(delay.id());

        assertThat(flight.estimatedDeparture()).isEqualTo(STD);
        assertThat(flight.estimatedArrival()).isEqualTo(STA);
    }

    @Test
    void descriptionOnlyEditKeepsTheEstimates() {
        Delay delay = delay(20, "Fog at departure");
        Flight flight = withStatus(FlightStatus.PLANNED, List.of(delay), List.of());
        Instant departureBefore = flight.estimatedDeparture();
        Instant arrivalBefore = flight.estimatedArrival();

        flight.updateDepartureDelay(delay.id(), 20, "Fog cleared later than expected");

        assertThat(flight.estimatedDeparture()).isEqualTo(departureBefore);
        assertThat(flight.estimatedArrival()).isEqualTo(arrivalBefore);
        assertThat(flight.departureDelays().getFirst().description())
                .isEqualTo("Fog cleared later than expected");
    }

    // the coach rule: departure and arrival delays together are at most 720 minutes
    @Test
    void delaysAddingUpToExactlyTwelveHoursAreAccepted() {
        Flight flight = planned();

        flight.addDepartureDelay(400, "71", "Fog at departure");
        flight.addArrivalDelay(320, "71", "Holding at arrival");

        assertThat(flight.totalDelayMinutes()).isEqualTo(720);
    }

    @Test
    void oneMinuteOverTwelveHoursTogetherIsRefusedForBothDelayKinds() {
        Flight flight = planned();
        flight.addDepartureDelay(400, "71", "Fog at departure");
        flight.addArrivalDelay(320, "71", "Holding at arrival");

        assertThatThrownBy(() -> flight.addDepartureDelay(1, "71", "One more minute"))
                .isInstanceOf(TotalDelayExceededException.class)
                .hasMessage(
                        "All delays together would be 721 minutes (12 h 1 min). At most 720"
                                + " minutes (12 h) is allowed. You can replan this flight"
                                + " instead.");
        assertThatThrownBy(() -> flight.addArrivalDelay(1, "71", "One more minute"))
                .isInstanceOf(TotalDelayExceededException.class)
                .satisfies(
                        exception -> {
                            var refused = (TotalDelayExceededException) exception;
                            assertThat(refused.totalMinutes()).isEqualTo(721);
                            assertThat(refused.replanningPossible()).isTrue();
                        });
        assertThat(flight.totalDelayMinutes()).isEqualTo(720);
        assertThat(flight.departureDelays()).hasSize(1);
        assertThat(flight.arrivalDelays()).hasSize(1);
    }

    @Test
    void editThatPushesTheTotalOverTwelveHoursIsRefusedAndTheDelayKeepsItsMinutes() {
        Delay departure = delay(400, "Fog at departure");
        Delay arrival = delay(320, "Holding");
        Flight flight = withStatus(FlightStatus.PLANNED, List.of(departure), List.of(arrival));

        assertThatThrownBy(() -> flight.updateDepartureDelay(departure.id(), 401, "Longer fog"))
                .isInstanceOf(TotalDelayExceededException.class);
        assertThatThrownBy(() -> flight.updateArrivalDelay(arrival.id(), 321, "Longer holding"))
                .isInstanceOf(TotalDelayExceededException.class);

        assertThat(departure.minutes()).isEqualTo(400);
        assertThat(departure.description()).isEqualTo("Fog at departure");
        assertThat(arrival.minutes()).isEqualTo(320);
        assertThat(flight.totalDelayMinutes()).isEqualTo(720);
    }

    @Test
    void editThatKeepsTheTotalAtTwelveHoursIsAccepted() {
        Delay departure = delay(400, "Fog at departure");
        Delay arrival = delay(320, "Holding");
        Flight flight = withStatus(FlightStatus.PLANNED, List.of(departure), List.of(arrival));

        flight.updateDepartureDelay(departure.id(), 400, "Still fog");
        flight.updateArrivalDelay(arrival.id(), 320, "Still holding");

        assertThat(flight.totalDelayMinutes()).isEqualTo(720);
        assertThat(departure.description()).isEqualTo("Still fog");
    }

    // 721 for one delay is invalid input, not the total rule
    @Test
    void editWithMoreThanTwelveHoursIsStillInvalidInput() {
        Delay departure = delay(20, "Fog at departure");
        Flight flight = withStatus(FlightStatus.PLANNED, List.of(departure), List.of());

        assertThatThrownBy(() -> flight.updateDepartureDelay(departure.id(), 721, "Longer fog"))
                .isInstanceOf(InvalidDelayException.class)
                .isNotInstanceOf(TotalDelayExceededException.class);
        assertThat(departure.minutes()).isEqualTo(20);
    }

    @Test
    void departedFlightOverTheTotalCannotBeReplanned() {
        Delay departure = delay(700, "Technical");
        Flight flight = withStatus(FlightStatus.DEPARTED, List.of(departure), List.of());

        assertThatThrownBy(() -> flight.addArrivalDelay(21, "71", "Holding"))
                .isInstanceOf(TotalDelayExceededException.class)
                .hasMessageEndingWith("A departed flight cannot be replanned.")
                .satisfies(
                        exception ->
                                assertThat(
                                                ((TotalDelayExceededException) exception)
                                                        .replanningPossible())
                                        .isFalse());
        assertThat(flight.arrivalDelays()).isEmpty();
    }

    // the review scenario: 720 + 720 was accepted before the total rule
    @Test
    void secondTwelveHourDelayIsRefused() {
        Flight flight = planned();
        flight.addDepartureDelay(720, "71", "Fog at departure");

        assertThatThrownBy(() -> flight.addDepartureDelay(720, "71", "More fog"))
                .isInstanceOf(TotalDelayExceededException.class);
        assertThat(flight.departureDelayMinutes()).isEqualTo(720);
    }

    private static Flight planned() {
        return Flight.create(KEY, "AMS", "SXBAA", STD, STA);
    }

    private static Flight withStatus(
            FlightStatus status, List<Delay> departureDelays, List<Delay> arrivalDelays) {
        return Flight.restore(
                KEY, "AMS", "SXBAA", STD, STA, status, departureDelays, arrivalDelays);
    }

    private static Delay delay(int minutes, String description) {
        return Delay.restore(UUID.randomUUID(), minutes, "71", description);
    }
}
