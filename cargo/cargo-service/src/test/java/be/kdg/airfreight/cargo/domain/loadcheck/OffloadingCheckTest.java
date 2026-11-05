package be.kdg.airfreight.cargo.domain.loadcheck;

import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.A1;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.A2;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.B1;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.CL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.CR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.DL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.DR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.ML;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.MR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.PL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.PR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.RL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.RR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.SL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.TL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.TR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.UL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.UR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// flights go to ath like the published examples so ath cargo leaves and fra cargo stays
class OffloadingCheckTest {
    private static final AirportCode ATH = new AirportCode("ATH");
    private static final AirportCode FRA = new AirportCode("FRA");

    private final OffloadingCheck check = new OffloadingCheck();

    @Test
    void publishedExample1FailsBecausePlBlocksMlAndRlBlocksMr() {
        OffloadingResult result =
                check.simulate(List.of(at(ML, ATH), at(MR, ATH), at(PL, FRA), at(RL, FRA)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockedPositions()).containsExactly(ML, MR);
        assertThat(result.unloadingOrder()).isEmpty();
    }

    @Test
    void publishedExample2PassesByUnloadingRrBeforeUr() {
        OffloadingResult result =
                check.simulate(List.of(at(RR, ATH), at(UR, ATH), at(UL, FRA)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(RR, UR);
        assertThat(result.blockedPositions()).isEmpty();
    }

    @Test
    void destinationCargoIsUnloadedFirstToClearARoute() {
        OffloadingResult result =
                check.simulate(List.of(at(ML, ATH), at(PL, ATH), at(RL, ATH)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(RL, PL, ML);
    }

    @Test
    void destinationCargoWithABlockedOwnRouteDoesNotClearTheWay() {
        OffloadingResult result =
                check.simulate(List.of(at(MR, ATH), at(RR, ATH), at(RL, FRA)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockedPositions()).containsExactly(MR, RR);
        assertThat(result.unloadingOrder()).isEmpty();
    }

    // fra on ml cannot reach the door but it stays aboard so it is not checked
    @Test
    void cargoForOtherAirportsStaysAndIsNotChecked() {
        OffloadingResult result =
                check.simulate(List.of(at(ML, FRA), at(PL, FRA), at(SL, ATH)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(SL);
        assertThat(result.blockedPositions()).isEmpty();
    }

    @Test
    void aBlockedSideIsNotBypassedOverTheOtherSide() {
        OffloadingResult right = check.simulate(List.of(at(MR, ATH), at(PR, FRA)), ATH);
        OffloadingResult left = check.simulate(List.of(at(ML, ATH), at(PL, FRA)), ATH);

        assertThat(right.passed()).isFalse();
        assertThat(right.blockedPositions()).containsExactly(MR);
        assertThat(left.passed()).isFalse();
        assertThat(left.blockedPositions()).containsExactly(ML);
    }

    @Test
    void cargoBehindTheDoorMovesForwardNotBackward() {
        OffloadingResult tailFree = check.simulate(List.of(at(TL, ATH), at(UL, FRA)), ATH);
        OffloadingResult doorSideBlocked = check.simulate(List.of(at(UL, ATH), at(SL, FRA)), ATH);

        assertThat(tailFree.passed()).isTrue();
        assertThat(tailFree.unloadingOrder()).containsExactly(TL);
        assertThat(doorSideBlocked.passed()).isFalse();
        assertThat(doorSideBlocked.blockedPositions()).containsExactly(UL);
    }

    @Test
    void onlyPositionedBookingsCount() {
        OffloadingResult result =
                check.simulate(List.of(unpositioned(ATH), unpositioned(FRA), at(RL, ATH)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(RL);
    }

    @Test
    void noDestinationCargoPasses() {
        OffloadingResult empty = check.simulate(List.of(), ATH);
        OffloadingResult onlyFra = check.simulate(List.of(at(ML, FRA), at(RL, FRA)), ATH);

        assertThat(empty.passed()).isTrue();
        assertThat(empty.unloadingOrder()).isEmpty();
        assertThat(onlyFra.passed()).isTrue();
        assertThat(onlyFra.unloadingOrder()).isEmpty();
    }

    @Test
    void theArrivalAirportDecidesWhatIsUnloaded() {
        OffloadingResult result =
                check.simulate(List.of(at(ML, ATH), at(MR, ATH), at(PL, FRA), at(RL, FRA)), FRA);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(RL, PL);
    }

    @Test
    void checkingChangesNoPosition() {
        List<UldBooking> bookings =
                List.of(at(RR, ATH), at(UR, ATH), at(UL, FRA), unpositioned(ATH));

        check.simulate(bookings, ATH);

        assertThat(bookings)
                .extracting(booking -> booking.getPosition().orElse(null))
                .containsExactly(RR, UR, UL, null);
    }

    @Test
    void publishedExample3FailsBecauseCrHoldsCargoForFra() {
        OffloadingResult result = check.simulate(List.of(at(B1, ATH), at(CR, FRA)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockedPositions()).containsExactly(B1);
        assertThat(result.unloadingOrder()).isEmpty();
    }

    // dl blocks the left route so only the right route can make this pass
    @Test
    void publishedExample4PassesOverTheRightSide() {
        OffloadingResult result = check.simulate(List.of(at(B1, ATH), at(DL, FRA)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(B1);
        assertThat(result.blockedPositions()).isEmpty();
    }

    @Test
    void publishedExample5FailsBecauseUlHoldsCargoForFra() {
        OffloadingResult result = check.simulate(List.of(at(X, ATH), at(UL, FRA)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockedPositions()).containsExactly(X);
        assertThat(result.unloadingOrder()).isEmpty();
    }

    // tr blocks the right route so only the left route can make this pass
    @Test
    void publishedExample6PassesOverTheLeftSide() {
        OffloadingResult result = check.simulate(List.of(at(X, ATH), at(TR, FRA)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(X);
        assertThat(result.blockedPositions()).isEmpty();
    }

    @Test
    void maneuveringSpaceIsClearedByUnloadingDestinationCargoFirst() {
        OffloadingResult result = check.simulate(List.of(at(B1, ATH), at(CL, ATH)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(CL, B1);
    }

    @Test
    void maneuveringSpaceWithStuckDestinationCargoFails() {
        OffloadingResult result =
                check.simulate(List.of(at(B1, ATH), at(CL, ATH), at(DL, FRA)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockedPositions()).containsExactly(B1, CL);
        assertThat(result.unloadingOrder()).isEmpty();
    }

    // dr blocks the right route so every nose position must take the left one
    @ParameterizedTest
    @EnumSource(
            value = LoadingPosition.class,
            names = {"A1", "A2", "B1"})
    void everyNosePositionUsesTheSameRule(LoadingPosition position) {
        OffloadingResult result = check.simulate(List.of(at(position, ATH), at(DR, FRA)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(position);
    }

    @Test
    void bothSideRoutesBlockedFails() {
        OffloadingResult result =
                check.simulate(List.of(at(B1, ATH), at(DL, FRA), at(DR, FRA)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockedPositions()).containsExactly(B1);
    }

    @Test
    void tailManeuveringSpaceIsClearedFirst() {
        OffloadingResult result = check.simulate(List.of(at(X, ATH), at(UR, ATH)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(UR, X);
    }

    // team reading of q16 so this test flips if the coach answers otherwise
    @Test
    void nosePositionsDoNotBlockEachOther() {
        OffloadingResult result =
                check.simulate(List.of(at(A1, ATH), at(A2, FRA), at(B1, FRA)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(A1);
    }

    // b1 leaves over the right side while ml stays stuck behind pl in the same check
    @Test
    void noseTailAndRowsAreOneCheck() {
        OffloadingResult result =
                check.simulate(List.of(at(B1, ATH), at(ML, ATH), at(PL, FRA)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.unloadingOrder()).containsExactly(B1);
        assertThat(result.blockedPositions()).containsExactly(ML);
    }

    @Test
    void cargoForOtherAirportsOnSinglePositionsIsIgnored() {
        OffloadingResult result =
                check.simulate(List.of(at(X, FRA), at(A1, FRA), at(RL, ATH)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(RL);
    }

    @Test
    void refusesMissingInput() {
        assertThatThrownBy(() -> check.simulate(null, ATH))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Bookings are required");
        assertThatThrownBy(() -> check.simulate(List.of(at(RL, ATH)), null))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Arrival airport is required");
        assertThatThrownBy(() -> new OffloadingResult(null, List.of()))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Unloading order is required");
        assertThatThrownBy(() -> new OffloadingResult(List.of(), null))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Blocked positions are required");
    }

    private static UldBooking at(LoadingPosition position, AirportCode destination) {
        return new UldBooking(
                new UldCode("AKE" + position + destination), weight(), destination, position);
    }

    private static UldBooking unpositioned(AirportCode destination) {
        return new UldBooking(new UldCode("PMC" + destination), weight(), destination, null);
    }

    private static Weight weight() {
        return new Weight(new BigDecimal("1250.5"), WeightUnit.KG);
    }
}
