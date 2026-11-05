package be.kdg.airfreight.cargo.domain.cargoplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loadcheck.LateralBalanceCheck;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckResult;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadChecks;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import be.kdg.airfreight.cargo.domain.loading.LoadingNotAllowedException;
import be.kdg.airfreight.cargo.domain.loading.LoadingRefusal;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CargoPlanFinalizeTest {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");
    private static final LoadingWindowPolicy POLICY =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    private static final Instant INSIDE = Instant.parse("2026-10-22T06:00:00Z");
    private static final LoadChecks CHECKS =
            new LoadChecks(new LateralBalanceCheck(new BigDecimal("0.10")));

    @Test
    void finalizesWithOnlyASoftWarningAndKeepsOnlyPositionedUlds() {
        CargoPlan cargoPlan =
                plan(
                        booking("PMC3", null, "500", WeightUnit.KG),
                        booking("AKE2", LoadingPosition.CR, "2204.62", WeightUnit.LB),
                        booking("AKE1", LoadingPosition.CL, "1000", WeightUnit.KG));

        LoadFinalized event = cargoPlan.finalizeLoad(context(INSIDE), CHECKS);

        assertThat(cargoPlan.isFinalized()).isTrue();
        assertThat(cargoPlan.getFinalizedAt()).contains(INSIDE);
        assertThat(event.flightKey()).isEqualTo(KEY);
        assertThat(event.finalizedAt()).isEqualTo(INSIDE);
        // floor plan order and the weight as booked without conversion
        assertThat(event.ulds())
                .containsExactly(
                        new LoadFinalized.FinalizedUld(
                                new UldCode("AKE1"),
                                LoadingPosition.CL,
                                new Weight(new BigDecimal("1000"), WeightUnit.KG),
                                new AirportCode("AMS")),
                        new LoadFinalized.FinalizedUld(
                                new UldCode("AKE2"),
                                LoadingPosition.CR,
                                new Weight(new BigDecimal("2204.62"), WeightUnit.LB),
                                new AirportCode("AMS")));
        assertThat(event.report().results())
                .extracting(LoadCheckResult::passed)
                .containsExactly(true, true, false);
    }

    @Test
    void hardFailureRefusesWithTheReportAndChangesNothing() {
        CargoPlan cargoPlan =
                plan(
                        booking("AKE1", LoadingPosition.CL, "1000", WeightUnit.KG),
                        booking("AKE2", LoadingPosition.CR, "1101", WeightUnit.KG));

        assertThatThrownBy(() -> cargoPlan.finalizeLoad(context(INSIDE), CHECKS))
                .isInstanceOfSatisfying(
                        LoadNotFinalizedException.class,
                        refused -> {
                            assertThat(refused.getFlightKey()).isEqualTo(KEY);
                            assertThat(refused.getReport().hasHardFailure()).isTrue();
                            assertThat(refused.getReport().results()).hasSize(3);
                        })
                .hasMessage(
                        "Load of flight HJ221/2026-10-22/BRU is not finalized: a hard check failed");
        assertThat(cargoPlan.isFinalized()).isFalse();
        assertThat(cargoPlan.getFinalizedAt()).isEmpty();
    }

    // after a refusal the planner can still fix the load inside the window
    @Test
    void refusedPlanCanStillBeChangedAndThenFinalized() {
        CargoPlan cargoPlan =
                plan(
                        booking("AKE1", LoadingPosition.CL, "1000", WeightUnit.KG),
                        booking("AKE2", LoadingPosition.CR, "1101", WeightUnit.KG),
                        booking("AKE3", null, "101", WeightUnit.KG));
        assertThatThrownBy(() -> cargoPlan.finalizeLoad(context(INSIDE), CHECKS))
                .isInstanceOf(LoadNotFinalizedException.class);

        cargoPlan.assignPosition(new UldCode("AKE3"), LoadingPosition.DL, context(INSIDE));

        assertThat(cargoPlan.finalizeLoad(context(INSIDE), CHECKS).ulds()).hasSize(3);
    }

    // d3: nothing positioned is only a soft warning and gives an empty cpm
    @Test
    void emptyLoadCanBeFinalized() {
        CargoPlan cargoPlan = plan(booking("AKE1", null, "1000", WeightUnit.KG));

        LoadFinalized event = cargoPlan.finalizeLoad(context(INSIDE), CHECKS);

        assertThat(event.ulds()).isEmpty();
        assertThat(cargoPlan.isFinalized()).isTrue();
    }

    @Test
    void finalizedPlanCannotBeFinalizedAgainOrChanged() {
        CargoPlan cargoPlan = plan(booking("AKE1", LoadingPosition.A1, "1000", WeightUnit.KG));
        cargoPlan.finalizeLoad(context(INSIDE), CHECKS);

        assertRefused(
                () -> cargoPlan.finalizeLoad(context(INSIDE), CHECKS),
                LoadingRefusal.PLAN_FINALIZED);
        assertRefused(
                () ->
                        cargoPlan.assignPosition(
                                new UldCode("AKE1"), LoadingPosition.A2, context(INSIDE)),
                LoadingRefusal.PLAN_FINALIZED);
        assertRefused(
                () -> cargoPlan.clearPosition(new UldCode("AKE1"), context(INSIDE)),
                LoadingRefusal.PLAN_FINALIZED);
        assertThat(cargoPlan.findBooking(new UldCode("AKE1")).orElseThrow().getPosition())
                .contains(LoadingPosition.A1);
    }

    // a single uld on one side fails the balance, derived from the formula
    @Test
    void oneUldOnOneSideFailsTheBalance() {
        CargoPlan cargoPlan = plan(booking("AKE1", LoadingPosition.CL, "1000", WeightUnit.KG));

        assertThatThrownBy(() -> cargoPlan.finalizeLoad(context(INSIDE), CHECKS))
                .isInstanceOf(LoadNotFinalizedException.class);
    }

    @Test
    void closedWindowRefusesBeforeAnyCheckRuns() {
        CargoPlan cargoPlan = plan(booking("AKE1", LoadingPosition.CL, "1000", WeightUnit.KG));

        assertRefused(
                () ->
                        cargoPlan.finalizeLoad(
                                context(Instant.parse("2026-10-22T07:40:00Z")), CHECKS),
                LoadingRefusal.WINDOW_CLOSED);
        assertThat(cargoPlan.isFinalized()).isFalse();
    }

    private static void assertRefused(Runnable action, LoadingRefusal refusal) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        LoadingNotAllowedException.class,
                        refused -> assertThat(refused.getRefusal()).isEqualTo(refusal));
    }

    private static CargoPlan plan(UldBooking... bookings) {
        return CargoPlan.restore(KEY, CargoPlanState.DRAFT, List.of(bookings));
    }

    private static UldBooking booking(
            String code, LoadingPosition position, String weight, WeightUnit unit) {
        return new UldBooking(
                new UldCode(code),
                new Weight(new BigDecimal(weight), unit),
                new AirportCode("AMS"),
                position);
    }

    private static LoadingContext context(Instant now) {
        FlightInformation flight =
                new FlightInformation(
                        KEY,
                        new AirportCode("AMS"),
                        FlightStatus.PLANNED,
                        STD,
                        STD.plus(Duration.ofHours(2)),
                        STD);
        return new LoadingContext(flight, POLICY.windowFor(STD, STD), now);
    }
}
