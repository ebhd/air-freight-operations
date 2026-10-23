package be.kdg.airfreight.cargo.domain;

import static be.kdg.airfreight.cargo.domain.LoadingPosition.B1;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.ML;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.MR;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.PL;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.PR;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.RL;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.RR;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.SL;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.TL;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.UL;
import static be.kdg.airfreight.cargo.domain.LoadingPosition.UR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

// flights go to ath like the published examples so ath cargo leaves and fra cargo stays
class OffloadingCheckTest {
    private static final AirportCode ATH = new AirportCode("ATH");
    private static final AirportCode FRA = new AirportCode("FRA");

    private final OffloadingCheck check = new OffloadingCheck();

    @Test
    void publishedExample1FailsBecausePlBlocksMlAndRlBlocksMr() {
        OffloadingResult result =
                check.evaluate(List.of(at(ML, ATH), at(MR, ATH), at(PL, FRA), at(RL, FRA)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockedPositions()).containsExactly(ML, MR);
        assertThat(result.unloadingOrder()).isEmpty();
    }

    @Test
    void publishedExample2PassesByUnloadingRrBeforeUr() {
        OffloadingResult result =
                check.evaluate(List.of(at(RR, ATH), at(UR, ATH), at(UL, FRA)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(RR, UR);
        assertThat(result.blockedPositions()).isEmpty();
    }

    @Test
    void destinationCargoIsUnloadedFirstToClearARoute() {
        OffloadingResult result =
                check.evaluate(List.of(at(ML, ATH), at(PL, ATH), at(RL, ATH)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(RL, PL, ML);
    }

    @Test
    void destinationCargoWithABlockedOwnRouteDoesNotClearTheWay() {
        OffloadingResult result =
                check.evaluate(List.of(at(MR, ATH), at(RR, ATH), at(RL, FRA)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockedPositions()).containsExactly(MR, RR);
        assertThat(result.unloadingOrder()).isEmpty();
    }

    // fra on ml cannot reach the door but it stays aboard so it is not checked
    @Test
    void cargoForOtherAirportsStaysAndIsNotChecked() {
        OffloadingResult result =
                check.evaluate(List.of(at(ML, FRA), at(PL, FRA), at(SL, ATH)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(SL);
        assertThat(result.blockedPositions()).isEmpty();
    }

    @Test
    void aBlockedSideIsNotBypassedOverTheOtherSide() {
        OffloadingResult right = check.evaluate(List.of(at(MR, ATH), at(PR, FRA)), ATH);
        OffloadingResult left = check.evaluate(List.of(at(ML, ATH), at(PL, FRA)), ATH);

        assertThat(right.passed()).isFalse();
        assertThat(right.blockedPositions()).containsExactly(MR);
        assertThat(left.passed()).isFalse();
        assertThat(left.blockedPositions()).containsExactly(ML);
    }

    @Test
    void cargoBehindTheDoorMovesForwardNotBackward() {
        OffloadingResult tailFree = check.evaluate(List.of(at(TL, ATH), at(UL, FRA)), ATH);
        OffloadingResult doorSideBlocked = check.evaluate(List.of(at(UL, ATH), at(SL, FRA)), ATH);

        assertThat(tailFree.passed()).isTrue();
        assertThat(tailFree.unloadingOrder()).containsExactly(TL);
        assertThat(doorSideBlocked.passed()).isFalse();
        assertThat(doorSideBlocked.blockedPositions()).containsExactly(UL);
    }

    @Test
    void onlyPositionedBookingsCount() {
        OffloadingResult result =
                check.evaluate(List.of(unpositioned(ATH), unpositioned(FRA), at(RL, ATH)), ATH);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(RL);
    }

    @Test
    void noDestinationCargoPasses() {
        OffloadingResult empty = check.evaluate(List.of(), ATH);
        OffloadingResult onlyFra = check.evaluate(List.of(at(ML, FRA), at(RL, FRA)), ATH);

        assertThat(empty.passed()).isTrue();
        assertThat(empty.unloadingOrder()).isEmpty();
        assertThat(onlyFra.passed()).isTrue();
        assertThat(onlyFra.unloadingOrder()).isEmpty();
    }

    @Test
    void theArrivalAirportDecidesWhatIsUnloaded() {
        OffloadingResult result =
                check.evaluate(List.of(at(ML, ATH), at(MR, ATH), at(PL, FRA), at(RL, FRA)), FRA);

        assertThat(result.passed()).isTrue();
        assertThat(result.unloadingOrder()).containsExactly(RL, PL);
    }

    @Test
    void checkingChangesNoPosition() {
        List<UldBooking> bookings =
                List.of(at(RR, ATH), at(UR, ATH), at(UL, FRA), unpositioned(ATH));

        check.evaluate(bookings, ATH);

        assertThat(bookings)
                .extracting(booking -> booking.getPosition().orElse(null))
                .containsExactly(RR, UR, UL, null);
    }

    // issue 24 replaces this test with the maneuvering rule
    @Test
    void destinationCargoOnASinglePositionIsBlockedUntilIssue24() {
        OffloadingResult result = check.evaluate(List.of(at(B1, ATH)), ATH);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockedPositions()).containsExactly(B1);
    }

    @Test
    void refusesMissingInput() {
        assertThatThrownBy(() -> check.evaluate(null, ATH))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Bookings are required");
        assertThatThrownBy(() -> check.evaluate(List.of(at(RL, ATH)), null))
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
