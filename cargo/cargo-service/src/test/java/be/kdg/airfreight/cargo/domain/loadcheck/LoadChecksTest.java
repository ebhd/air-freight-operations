package be.kdg.airfreight.cargo.domain.loadcheck;

import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.CL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.CR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.ML;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.PL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

// the algorithms have their own tests, here only that the report is complete
class LoadChecksTest {
    private static final AirportCode ATH = new AirportCode("ATH");
    private final LoadChecks checks =
            new LoadChecks(new LateralBalanceCheck(new BigDecimal("0.10")));

    @Test
    void reportHasAllThreeChecksWhenEverythingPasses() {
        LoadCheckReport report =
                checks.run(
                        List.of(at("AKE1", CL, "1000", "ATH"), at("AKE2", CR, "1000", "ATH")), ATH);

        assertThat(report.results())
                .extracting(
                        LoadCheckResult::name, LoadCheckResult::severity, LoadCheckResult::passed)
                .containsExactly(
                        tuple("Lateral balance", CheckSeverity.HARD, true),
                        tuple("Offloading", CheckSeverity.HARD, true),
                        tuple("All cargo loaded", CheckSeverity.SOFT, true));
        assertThat(report.hasHardFailure()).isFalse();
        assertThat(report.results().get(1).reason())
                .isEqualTo("All cargo for ATH can leave without moving other cargo");
    }

    // ml is blocked by fra cargo at pl, everything is on the left and one uld stays behind
    @Test
    void reportShowsEveryFailureTogether() {
        List<UldBooking> bookings =
                List.of(
                        at("AKE1", ML, "1000", "ATH"),
                        at("AKE2", PL, "1000", "FRA"),
                        at("PMC3", null, "500", "ATH"));

        LoadCheckReport report = checks.run(bookings, ATH);

        assertThat(report.results())
                .extracting(LoadCheckResult::passed)
                .containsExactly(false, false, false);
        assertThat(report.hasHardFailure()).isTrue();
        assertThat(report.results().get(1).reason())
                .isEqualTo(
                        "Cargo for ATH at ML cannot leave without moving cargo for other airports");
        assertThat(report.results().get(2).reason())
                .isEqualTo("1 booked ULD has no position and will stay behind: PMC3");
    }

    @Test
    void onlyASoftWarningIsNoHardFailure() {
        LoadCheckReport report =
                checks.run(
                        List.of(
                                at("AKE1", CL, "1000", "ATH"),
                                at("AKE2", CR, "1000", "ATH"),
                                at("PMC3", null, "500", "ATH")),
                        ATH);

        assertThat(report.results().get(2).passed()).isFalse();
        assertThat(report.hasHardFailure()).isFalse();
    }

    @Test
    void checkingNeverChangesTheBookings() {
        UldBooking positioned = at("AKE1", ML, "1000", "ATH");
        UldBooking blocker = at("AKE2", PL, "1000", "FRA");

        checks.run(List.of(positioned, blocker), ATH);

        assertThat(positioned.getPosition()).contains(ML);
        assertThat(blocker.getPosition()).contains(PL);
    }

    private static UldBooking at(
            String code, LoadingPosition position, String weight, String destination) {
        return new UldBooking(
                new UldCode(code),
                new Weight(new BigDecimal(weight), WeightUnit.KG),
                new AirportCode(destination),
                position);
    }
}
