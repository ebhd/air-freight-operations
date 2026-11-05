package be.kdg.airfreight.cargo.domain.cargoplan;

import static be.kdg.airfreight.cargo.support.TestHelper.plannedFlight;
import static be.kdg.airfreight.cargo.support.TestHelper.restoredPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.domain.exception.LoadingNotAllowedException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loadcheck.LateralBalanceCheck;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckReport;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckResult;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadChecks;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import be.kdg.airfreight.cargo.domain.loading.LoadingRefusal;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CargoPlanCheckLoadTest {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");
    private static final LoadingWindowPolicy POLICY =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    private static final LoadChecks CHECKS =
            new LoadChecks(new LateralBalanceCheck(new BigDecimal("0.10")));

    @Test
    void checksTheBookingsAgainstTheArrivalAirportOfTheFlight() {
        CargoPlan cargoPlan = new CargoPlan(KEY);
        cargoPlan.bookUld(
                new UldCode("AKE1"), kg("1000"), new AirportCode("AMS"), plannedFlight(KEY));

        LoadCheckReport report =
                cargoPlan.checkLoad(context(Instant.parse("2026-10-22T06:00:00Z")), CHECKS);

        assertThat(report.results()).extracting(LoadCheckResult::name).hasSize(3);
        assertThat(report.results().get(1).reason())
                .isEqualTo("All cargo for AMS can leave without moving other cargo");
        assertThat(report.results().get(2).passed()).isFalse();
    }

    @Test
    void closedWindowRefusesTheCheck() {
        CargoPlan cargoPlan = new CargoPlan(KEY);

        assertThatThrownBy(
                        () ->
                                cargoPlan.checkLoad(
                                        context(Instant.parse("2026-10-22T07:40:00Z")), CHECKS))
                .isInstanceOfSatisfying(
                        LoadingNotAllowedException.class,
                        refused ->
                                assertThat(refused.getRefusal())
                                        .isEqualTo(LoadingRefusal.WINDOW_CLOSED));
    }

    // a check is a loading action so a finalized plan refuses it
    @Test
    void finalizedPlanRefusesTheCheck() {
        CargoPlan finalized = restoredPlan(KEY, CargoPlanState.FINALIZED, List.of());

        assertThatThrownBy(
                        () ->
                                finalized.checkLoad(
                                        context(Instant.parse("2026-10-22T06:00:00Z")), CHECKS))
                .isInstanceOfSatisfying(
                        LoadingNotAllowedException.class,
                        refused ->
                                assertThat(refused.getRefusal())
                                        .isEqualTo(LoadingRefusal.PLAN_FINALIZED));
    }

    private static Weight kg(String value) {
        return new Weight(new BigDecimal(value), WeightUnit.KG);
    }

    private static LoadingContext context(Instant now) {
        FlightInformation flight =
                new FlightInformation(
                        KEY,
                        new AirportCode("AMS"),
                        FlightStatus.PLANNED,
                        STD,
                        STD.plus(Duration.ofHours(2)),
                        STD,
                        STD.plus(Duration.ofHours(2)));
        return new LoadingContext(flight, POLICY.windowFor(STD, STD), now);
    }
}
