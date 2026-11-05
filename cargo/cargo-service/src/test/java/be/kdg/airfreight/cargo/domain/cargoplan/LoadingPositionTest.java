package be.kdg.airfreight.cargo.domain.cargoplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LoadingPositionTest {
    private static final String ALL_CODES =
            "A1, A2, B1, CL, CR, DL, DR, EL, ER, FL, FR, GL, GR, HL, HR, JL, JR, KL, KR, LL, LR,"
                    + " ML, MR, PL, PR, RL, RR, SL, SR, TL, TR, UL, UR, X";

    @Test
    void listsTheThirtyFourMainDeckPositionsFromNoseToTail() {
        assertThat(LoadingPosition.values()).hasSize(34);
        assertThat(Arrays.stream(LoadingPosition.values()).map(Enum::name))
                .containsExactly(ALL_CODES.split(", "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZ", "IL"})
    void rejectsUnknownCodes(String code) {
        assertThatThrownBy(() -> LoadingPosition.fromCode(code))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessageContaining("Unknown loading position " + code);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsMissingCodes(String code) {
        assertThatThrownBy(() -> LoadingPosition.fromCode(code))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage("Loading position is required");
    }

    @Test
    void singlePositionsHaveNoSideAndCodesIgnoreCase() {
        assertThat(LoadingPosition.A1.side()).isEqualTo(LoadingPosition.Side.NONE);
        assertThat(LoadingPosition.A2.side()).isEqualTo(LoadingPosition.Side.NONE);
        assertThat(LoadingPosition.B1.side()).isEqualTo(LoadingPosition.Side.NONE);
        assertThat(LoadingPosition.X.side()).isEqualTo(LoadingPosition.Side.NONE);
        assertThat(LoadingPosition.fromCode(" rl ")).isEqualTo(LoadingPosition.RL);
        assertThat(LoadingPosition.fromCode("x")).isEqualTo(LoadingPosition.X);
    }

    // every paired row has exactly one left and one right position
    @Test
    void pairedRowsHaveOneLeftAndOneRightPosition() {
        for (char row : "CDEFGHJKLMPRSTU".toCharArray()) {
            assertThat(LoadingPosition.fromCode(row + "L").row()).isEqualTo(row);
            assertThat(LoadingPosition.fromCode(row + "L").side())
                    .isEqualTo(LoadingPosition.Side.LEFT);
            assertThat(LoadingPosition.fromCode(row + "R").row()).isEqualTo(row);
            assertThat(LoadingPosition.fromCode(row + "R").side())
                    .isEqualTo(LoadingPosition.Side.RIGHT);
        }
    }
}
