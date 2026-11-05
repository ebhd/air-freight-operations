package be.kdg.airfreight.cargo.domain.cargoplan;

import static be.kdg.airfreight.cargo.support.TestHelper.plannedFlight;
import static be.kdg.airfreight.cargo.support.TestHelper.restoredPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.exception.LoadingNotAllowedException;
import be.kdg.airfreight.cargo.domain.exception.PositionOccupiedException;
import be.kdg.airfreight.cargo.domain.exception.UldNotBookedException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import be.kdg.airfreight.cargo.domain.loading.LoadingRefusal;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CargoPlanPositionTest {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");
    private static final LoadingWindowPolicy POLICY =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    // window 2026-10-21T20:00Z to 2026-10-22T07:40Z
    private static final Instant INSIDE = Instant.parse("2026-10-22T06:00:00Z");
    private static final LoadingContext OPEN = context(FlightStatus.PLANNED, INSIDE);
    private static final UldCode AKE = new UldCode("AKE12345KL");
    private static final UldCode PMC = new UldCode("PMC00001KL");

    @Test
    void assignsAFreePositionToABookedUld() {
        CargoPlan cargoPlan = planWith(booking(AKE, null), booking(PMC, null));

        cargoPlan.assignPosition(AKE, LoadingPosition.CL, OPEN);

        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.CL);
        assertThat(positionOf(cargoPlan, PMC)).isNull();
    }

    @Test
    void refusesAPositionTakenByAnotherUld() {
        CargoPlan cargoPlan =
                planWith(booking(AKE, LoadingPosition.CL), booking(PMC, LoadingPosition.CR));

        assertThatThrownBy(() -> cargoPlan.assignPosition(PMC, LoadingPosition.CL, OPEN))
                .isInstanceOf(PositionOccupiedException.class)
                .hasMessage(
                        "Position CL on flight HJ221/2026-10-22/BRU is already taken by ULD"
                                + " AKE12345KL");
        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.CL);
        assertThat(positionOf(cargoPlan, PMC)).isEqualTo(LoadingPosition.CR);
    }

    @Test
    void movesAPositionedUldAndFreesItsOldPosition() {
        CargoPlan cargoPlan = planWith(booking(AKE, LoadingPosition.CL), booking(PMC, null));

        cargoPlan.assignPosition(AKE, LoadingPosition.CR, OPEN);
        cargoPlan.assignPosition(PMC, LoadingPosition.CL, OPEN);

        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.CR);
        assertThat(positionOf(cargoPlan, PMC)).isEqualTo(LoadingPosition.CL);
    }

    @Test
    void assigningTheSamePositionAgainChangesNothing() {
        CargoPlan cargoPlan = planWith(booking(AKE, LoadingPosition.CL), booking(PMC, null));

        cargoPlan.assignPosition(AKE, LoadingPosition.CL, OPEN);

        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.CL);
        assertThat(positionOf(cargoPlan, PMC)).isNull();
    }

    @Test
    void refusesAUldThatIsNotBooked() {
        CargoPlan cargoPlan = planWith(booking(AKE, LoadingPosition.CL));
        UldCode unknown = new UldCode("PMC99999KL");
        String message = "ULD PMC99999KL is not booked on flight HJ221/2026-10-22/BRU";

        assertThatThrownBy(() -> cargoPlan.assignPosition(unknown, LoadingPosition.CR, OPEN))
                .isInstanceOf(UldNotBookedException.class)
                .hasMessage(message);
        assertThatThrownBy(() -> cargoPlan.clearPosition(unknown, OPEN))
                .isInstanceOf(UldNotBookedException.class)
                .hasMessage(message);
        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.CL);
    }

    @Test
    void refusesMissingUldCodeOrPosition() {
        CargoPlan cargoPlan = planWith(booking(AKE, LoadingPosition.CL));

        assertThatThrownBy(() -> cargoPlan.assignPosition(AKE, null, OPEN))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Loading position is required");
        assertThatThrownBy(() -> cargoPlan.assignPosition(null, LoadingPosition.CR, OPEN))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("ULD code is required");
        assertThatThrownBy(() -> cargoPlan.clearPosition(null, OPEN))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("ULD code is required");
        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.CL);
    }

    @Test
    void clearsAPositionAndClearingTwiceChangesNothing() {
        CargoPlan cargoPlan =
                planWith(booking(AKE, LoadingPosition.CL), booking(PMC, LoadingPosition.CR));

        cargoPlan.clearPosition(AKE, OPEN);
        cargoPlan.clearPosition(AKE, OPEN);

        assertThat(positionOf(cargoPlan, AKE)).isNull();
        assertThat(positionOf(cargoPlan, PMC)).isEqualTo(LoadingPosition.CR);
        assertThat(cargoPlan.getBookings()).hasSize(2);
    }

    static Stream<Arguments> refusedLoading() {
        return Stream.of(
                arguments(
                        CargoPlanState.DRAFT,
                        context(FlightStatus.PLANNED, Instant.parse("2026-10-22T07:40:00Z")),
                        LoadingRefusal.WINDOW_CLOSED),
                arguments(
                        CargoPlanState.DRAFT,
                        context(FlightStatus.PLANNED, Instant.parse("2026-10-21T19:59:59Z")),
                        LoadingRefusal.WINDOW_NOT_OPEN_YET),
                arguments(
                        CargoPlanState.DRAFT,
                        context(FlightStatus.DEPARTED, INSIDE),
                        LoadingRefusal.FLIGHT_NOT_PLANNED),
                arguments(CargoPlanState.FINALIZED, OPEN, LoadingRefusal.PLAN_FINALIZED));
    }

    @ParameterizedTest
    @MethodSource("refusedLoading")
    void refusesChangesWhenLoadingIsNotAllowed(
            CargoPlanState state, LoadingContext context, LoadingRefusal refusal) {
        CargoPlan cargoPlan =
                restoredPlan(
                        KEY, state, List.of(booking(AKE, LoadingPosition.CL), booking(PMC, null)));

        assertThatThrownBy(() -> cargoPlan.assignPosition(PMC, LoadingPosition.CR, context))
                .isInstanceOfSatisfying(
                        LoadingNotAllowedException.class,
                        refused -> assertThat(refused.getRefusal()).isEqualTo(refusal));
        assertThatThrownBy(() -> cargoPlan.clearPosition(AKE, context))
                .isInstanceOfSatisfying(
                        LoadingNotAllowedException.class,
                        refused -> assertThat(refused.getRefusal()).isEqualTo(refusal));
        // the guard runs first so even an unknown uld gets the loading refusal
        assertThatThrownBy(
                        () ->
                                cargoPlan.assignPosition(
                                        new UldCode("PMC99999KL"), LoadingPosition.CR, context))
                .isInstanceOf(LoadingNotAllowedException.class);
        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.CL);
        assertThat(positionOf(cargoPlan, PMC)).isNull();
    }

    @Test
    void unpositionedBookingsStayAndRestoreKeepsPositions() {
        CargoPlan cargoPlan = planWith(booking(AKE, LoadingPosition.RL), booking(PMC, null));
        cargoPlan.bookUld(
                new UldCode("AKE00002KL"), weight(), new AirportCode("ATH"), plannedFlight(KEY));

        assertThat(cargoPlan.getBookings())
                .extracting(booking -> booking.getCode().value())
                .containsExactly("AKE12345KL", "PMC00001KL", "AKE00002KL");
        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.RL);
        assertThat(positionOf(cargoPlan, PMC)).isNull();
        assertThat(positionOf(cargoPlan, new UldCode("AKE00002KL"))).isNull();
    }

    private static CargoPlan planWith(UldBooking... bookings) {
        return restoredPlan(KEY, CargoPlanState.DRAFT, List.of(bookings));
    }

    private static UldBooking booking(UldCode code, LoadingPosition position) {
        return new UldBooking(code, weight(), new AirportCode("AMS"), position);
    }

    private static Weight weight() {
        return new Weight(new BigDecimal("1250.5"), WeightUnit.KG);
    }

    // null when the uld has no position
    private static LoadingPosition positionOf(CargoPlan cargoPlan, UldCode code) {
        return cargoPlan.findBooking(code).orElseThrow().getPosition().orElse(null);
    }

    private static LoadingContext context(FlightStatus status, Instant now) {
        FlightInformation flight =
                new FlightInformation(
                        KEY,
                        new AirportCode("AMS"),
                        status,
                        STD,
                        STD.plusSeconds(7200),
                        STD,
                        STD.plusSeconds(7200));
        return new LoadingContext(flight, POLICY.windowFor(STD, STD), now);
    }
}
