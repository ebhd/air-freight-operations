package be.kdg.airfreight.cargo.domain.loadcheck;

import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.A1;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.A2;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.B1;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.CL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.CR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.DL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.DR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LateralBalanceCheckTest {
    private static final AirportCode ARRIVAL = new AirportCode("ATH");

    private final LateralBalanceCheck check = new LateralBalanceCheck(new BigDecimal("0.10"));

    // the published boundary with both sides swapped
    @ParameterizedTest(name = "left {0} right {1} passes {2}")
    @CsvSource({
        "1000, 1100, true",
        "1100, 1000, true",
        "1000, 1101, false",
        "1101, 1000, false",
        "1000, 1000, true",
        "1000, 1100.001, false"
    })
    void heavierSideMayWeighAtMostTenPercentMoreThanTheLighterSide(
            String left, String right, boolean passed) {
        List<UldBooking> bookings =
                List.of(at(CL, left, WeightUnit.KG), at(CR, right, WeightUnit.KG));

        assertThat(check.balance(bookings).passed()).isEqualTo(passed);
        assertThat(check.evaluate(bookings, ARRIVAL).passed()).isEqualTo(passed);
    }

    @Test
    void sumsEachSideOverAllItsPositions() {
        LateralBalance balance =
                check.balance(
                        List.of(
                                at(CL, "400", WeightUnit.KG),
                                at(DL, "600", WeightUnit.KG),
                                at(CR, "500", WeightUnit.KG),
                                at(DR, "601", WeightUnit.KG)));

        assertThat(balance.left()).isEqualByComparingTo("1000");
        assertThat(balance.right()).isEqualByComparingTo("1101");
        assertThat(balance.maxHeavierSide()).isEqualByComparingTo("1100");
        assertThat(balance.passed()).isFalse();
    }

    @Test
    void convertsPoundsToKilogramsBeforeAdding() {
        // 2000 lb is 907.18474 kg
        LateralBalance balance =
                check.balance(
                        List.of(
                                at(CL, "2000", WeightUnit.LB),
                                at(CR, "500", WeightUnit.KG),
                                at(DR, "450", WeightUnit.KG)));

        assertThat(balance.unit()).isEqualTo(WeightUnit.KG);
        assertThat(balance.left()).isEqualByComparingTo("907.18474");
        assertThat(balance.right()).isEqualByComparingTo("950");
        assertThat(balance.passed()).isTrue();
    }

    @Test
    void singlePositionsCountForNeitherSide() {
        List<UldBooking> bookings =
                List.of(
                        at(A1, "5000", WeightUnit.KG),
                        at(A2, "5000", WeightUnit.KG),
                        at(B1, "5000", WeightUnit.KG),
                        at(X, "5000", WeightUnit.KG),
                        at(CL, "1000", WeightUnit.KG),
                        at(CR, "1000", WeightUnit.KG));

        LateralBalance balance = check.balance(bookings);

        assertThat(balance.left()).isEqualByComparingTo("1000");
        assertThat(balance.right()).isEqualByComparingTo("1000");
        assertThat(balance.passed()).isTrue();
    }

    @Test
    void cargoWithoutAPositionIsIgnored() {
        LateralBalance balance =
                check.balance(
                        List.of(at(CL, "1000", WeightUnit.KG), at(null, "9000", WeightUnit.KG)));

        assertThat(balance.left()).isEqualByComparingTo("1000");
        assertThat(balance.right()).isEqualByComparingTo("0");
    }

    // derived from the formula: nothing against nothing passes, something against nothing fails
    @Test
    void emptyLoadPassesAndOneEmptySideFails() {
        assertThat(check.balance(List.of()).passed()).isTrue();
        assertThat(check.balance(List.of(at(CL, "1", WeightUnit.KG))).passed()).isFalse();
    }

    @Test
    void toleranceComesFromTheConstructor() {
        LateralBalanceCheck strict = new LateralBalanceCheck(new BigDecimal("0.05"));
        List<UldBooking> bookings =
                List.of(at(CL, "1000", WeightUnit.KG), at(CR, "1060", WeightUnit.KG));

        assertThat(check.balance(bookings).passed()).isTrue();
        assertThat(strict.balance(bookings).passed()).isFalse();
        assertThat(strict.balance(bookings).maxHeavierSide()).isEqualByComparingTo("1050");
    }

    @Test
    void failureNamesTheHeavierSideBothTotalsTheUnitAndTheMaximum() {
        LoadCheckResult result =
                check.evaluate(
                        List.of(at(CL, "1000", WeightUnit.KG), at(CR, "1101", WeightUnit.KG)),
                        ARRIVAL);

        assertThat(result.name()).isEqualTo("Lateral balance");
        assertThat(result.severity()).isEqualTo(CheckSeverity.HARD);
        assertThat(result.hardFailure()).isTrue();
        assertThat(result.reason())
                .isEqualTo(
                        "Right side is too heavy: left 1000 KG, right 1101 KG, the heavier side"
                                + " may weigh at most 1100 KG");
    }

    @Test
    void passShowsTheTotalsToo() {
        LoadCheckResult result =
                check.evaluate(
                        List.of(at(CL, "1100", WeightUnit.KG), at(CR, "1000", WeightUnit.KG)),
                        ARRIVAL);

        assertThat(result.passed()).isTrue();
        assertThat(result.reason())
                .isEqualTo(
                        "In balance: left 1100 KG, right 1000 KG, the heavier side may weigh at"
                                + " most 1100 KG");
    }

    @Test
    void refusesAMissingOrNegativeTolerance() {
        assertThatThrownBy(() -> new LateralBalanceCheck(null))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Lateral balance tolerance is required");
        assertThatThrownBy(() -> new LateralBalanceCheck(new BigDecimal("-0.01")))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Lateral balance tolerance must not be negative: -0.01");
    }

    static UldBooking at(LoadingPosition position, String weight, WeightUnit unit) {
        String code = position == null ? "AKE00000KL" : "AKE" + position.name() + "KL";
        return new UldBooking(
                new UldCode(code),
                new Weight(new BigDecimal(weight), unit),
                new AirportCode("AMS"),
                position);
    }
}
