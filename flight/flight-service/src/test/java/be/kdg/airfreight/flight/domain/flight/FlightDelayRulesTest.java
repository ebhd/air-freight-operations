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

        flight.addDelay(DelayType.DEPARTURE, 10, "71", "Fog at departure");
        flight.addDelay(DelayType.DEPARTURE, 20, "63", "Late crew");
        flight.addDelay(DelayType.ARRIVAL, 15, "71", "Holding at arrival");

        assertThat(flight.estimatedDeparture()).isEqualTo(STD.plus(Duration.ofMinutes(30)));
        assertThat(flight.estimatedArrival()).isEqualTo(STA.plus(Duration.ofMinutes(45)));
        assertThat(flight.scheduledDeparture()).isEqualTo(STD);
        assertThat(flight.scheduledArrival()).isEqualTo(STA);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 720})
    void oneAndSevenHundredTwentyMinutesAreAccepted(int minutes) {
        Flight flight = planned();

        flight.addDelay(DelayType.DEPARTURE, minutes, "71", "Fog at departure");

        assertThat(flight.departureDelayMinutes()).isEqualTo(minutes);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 721})
    void zeroAndSevenHundredTwentyOneMinutesAreRefused(int minutes) {
        Flight flight = planned();

        assertThatThrownBy(
                        () ->
                                flight.addDelay(
                                        DelayType.DEPARTURE, minutes, "71", "Fog at departure"))
                .isInstanceOf(InvalidDelayException.class)
                .hasMessage("delay minutes must be between 1 and 720");
        assertThat(flight.delays()).isEmpty();
    }

    @Test
    void invalidEditChangesNothing() {
        Delay delay = delay(DelayType.DEPARTURE, 20);
        Flight flight = withStatus(FlightStatus.PLANNED, delay);

        assertThatThrownBy(() -> flight.updateDelay(delay.id(), 721, "Longer fog"))
                .isInstanceOf(InvalidDelayException.class);

        assertThat(flight.delays().getFirst().minutes()).isEqualTo(20);
        assertThat(flight.delays().getFirst().description()).isEqualTo("Fog at departure");
    }

    @Test
    void departedFlightRefusesDepartureDelayChanges() {
        Delay departure = delay(DelayType.DEPARTURE, 20);
        Flight flight = withStatus(FlightStatus.DEPARTED, departure);

        assertThatThrownBy(() -> flight.addDelay(DelayType.DEPARTURE, 10, "71", "Fog at departure"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.updateDelay(departure.id(), 30, "Longer fog"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.removeDelay(departure.id()))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThat(flight.delays()).containsExactly(departure);
        assertThat(flight.departureDelayMinutes()).isEqualTo(20);
    }

    @Test
    void departedFlightStillAcceptsArrivalDelayChanges() {
        Delay arrival = delay(DelayType.ARRIVAL, 15);
        Flight flight = withStatus(FlightStatus.DEPARTED, arrival);

        flight.addDelay(DelayType.ARRIVAL, 10, "71", "Holding at arrival");
        flight.updateDelay(arrival.id(), 25, "Longer holding");

        assertThat(flight.arrivalDelayMinutes()).isEqualTo(35);
        flight.removeDelay(arrival.id());
        assertThat(flight.arrivalDelayMinutes()).isEqualTo(10);
    }

    @Test
    void landedFlightRefusesEveryDelayChange() {
        Delay departure = delay(DelayType.DEPARTURE, 20);
        Delay arrival = delay(DelayType.ARRIVAL, 15);
        Flight flight = withStatus(FlightStatus.LANDED, departure, arrival);

        assertThatThrownBy(() -> flight.addDelay(DelayType.ARRIVAL, 10, "71", "Holding"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.addDelay(DelayType.DEPARTURE, 10, "71", "Fog"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.updateDelay(arrival.id(), 30, "Longer holding"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.removeDelay(arrival.id()))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> flight.removeDelay(departure.id()))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThat(flight.delays()).containsExactly(departure, arrival);
    }

    @Test
    void removingTheLastDelayReturnsTheEstimatesToTheSchedule() {
        Delay delay = delay(DelayType.DEPARTURE, 20);
        Flight flight = withStatus(FlightStatus.PLANNED, delay);

        flight.removeDelay(delay.id());

        assertThat(flight.estimatedDeparture()).isEqualTo(STD);
        assertThat(flight.estimatedArrival()).isEqualTo(STA);
    }

    @Test
    void descriptionOnlyEditKeepsTheEstimates() {
        Delay delay = delay(DelayType.DEPARTURE, 20);
        Flight flight = withStatus(FlightStatus.PLANNED, delay);
        Instant departureBefore = flight.estimatedDeparture();
        Instant arrivalBefore = flight.estimatedArrival();

        flight.updateDelay(delay.id(), 20, "Fog cleared later than expected");

        assertThat(flight.estimatedDeparture()).isEqualTo(departureBefore);
        assertThat(flight.estimatedArrival()).isEqualTo(arrivalBefore);
        assertThat(flight.delays().getFirst().description())
                .isEqualTo("Fog cleared later than expected");
    }

    private static Flight planned() {
        return Flight.create(KEY, "AMS", "SXBAA", STD, STA);
    }

    private static Flight withStatus(FlightStatus status, Delay... delays) {
        return Flight.restore(KEY, "AMS", "SXBAA", STD, STA, status, List.of(delays));
    }

    private static Delay delay(DelayType type, int minutes) {
        String description = type == DelayType.DEPARTURE ? "Fog at departure" : "Holding";
        return Delay.restore(UUID.randomUUID(), type, minutes, "71", description);
    }
}
