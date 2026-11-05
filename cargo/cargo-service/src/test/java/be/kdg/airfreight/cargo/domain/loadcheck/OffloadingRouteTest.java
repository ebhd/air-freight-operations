package be.kdg.airfreight.cargo.domain.loadcheck;

import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.CL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.CR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.DL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.DR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.EL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.ER;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.FL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.FR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.GL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.GR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.HL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.HR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.JL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.JR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.KL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.KR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.LL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.LR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.ML;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.MR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.PL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.PR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.RL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.RR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.SL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.SR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.TL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.TR;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.UL;
import static be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition.UR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OffloadingRouteTest {

    @Test
    void leftPositionsFollowTheLeftSideToRl() {
        assertThat(OffloadingCheck.routeToDoor(ML)).containsExactly(PL, RL);
        assertThat(OffloadingCheck.routeToDoor(CL))
                .containsExactly(DL, EL, FL, GL, HL, JL, KL, LL, ML, PL, RL);
    }

    @Test
    void rightPositionsFollowTheRightSideToRrAndCrossToRl() {
        assertThat(OffloadingCheck.routeToDoor(MR)).containsExactly(PR, RR, RL);
        assertThat(OffloadingCheck.routeToDoor(CR))
                .containsExactly(DR, ER, FR, GR, HR, JR, KR, LR, MR, PR, RR, RL);
    }

    @Test
    void rowsBehindRMoveForwardOnTheirOwnSide() {
        assertThat(OffloadingCheck.routeToDoor(UL)).containsExactly(TL, SL, RL);
        assertThat(OffloadingCheck.routeToDoor(UR)).containsExactly(TR, SR, RR, RL);
        assertThat(OffloadingCheck.routeToDoor(SL)).containsExactly(RL);
    }

    @Test
    void doorRowPositions() {
        assertThat(OffloadingCheck.routeToDoor(RL)).isEmpty();
        assertThat(OffloadingCheck.routeToDoor(RR)).containsExactly(RL);
    }

    // all 30 paired positions
    @ParameterizedTest
    @EnumSource(
            value = LoadingPosition.class,
            names = {"A1", "A2", "B1", "X"},
            mode = EnumSource.Mode.EXCLUDE)
    void routesStayOnTheOwnSideUntilTheDoor(LoadingPosition position) {
        List<LoadingPosition> route = OffloadingCheck.routeToDoor(position);

        assertThat(route).doesNotContain(position);
        if (position == RL) {
            assertThat(route).isEmpty();
            return;
        }
        assertThat(route).last().isEqualTo(RL);
        // only the final rl may be on the other side
        assertThat(route.subList(0, route.size() - 1))
                .allSatisfy(passed -> assertThat(passed.side()).isEqualTo(position.side()));
    }

    @ParameterizedTest
    @EnumSource(
            value = LoadingPosition.class,
            names = {"A1", "A2", "B1", "X"})
    void singlePositionsHaveNoOwnRoute(LoadingPosition position) {
        assertThatThrownBy(() -> OffloadingCheck.routeToDoor(position))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Single position " + position + " has no own route to the door");
    }
}
