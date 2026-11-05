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
