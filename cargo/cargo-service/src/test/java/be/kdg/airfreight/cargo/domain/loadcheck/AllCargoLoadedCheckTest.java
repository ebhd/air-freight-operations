package be.kdg.airfreight.cargo.domain.loadcheck;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllCargoLoadedCheckTest {
    private static final AirportCode ARRIVAL = new AirportCode("ATH");

    private final AllCargoLoadedCheck check = new AllCargoLoadedCheck();

    @Test
    void passesWhenEveryBookingHasAPosition() {
        LoadCheckResult result =
                check.evaluate(List.of(booking("AKE1", LoadingPosition.CL)), ARRIVAL);

        assertThat(result.passed()).isTrue();
        assertThat(result.severity()).isEqualTo(CheckSeverity.SOFT);
        assertThat(result.reason()).isEqualTo("Every booked ULD has a position");
    }

    @Test
    void namesTheOneUldThatStaysBehind() {
        LoadCheckResult result =
                check.evaluate(
                        List.of(booking("AKE1", LoadingPosition.CL), booking("PMC2", null)),
                        ARRIVAL);

        assertThat(result.passed()).isFalse();
        assertThat(result.hardFailure()).isFalse();
        assertThat(result.reason())
                .isEqualTo("1 booked ULD has no position and will stay behind: PMC2");
    }

    @Test
    void namesEveryUldThatStaysBehindSortedByCode() {
        LoadCheckResult result =
                check.evaluate(
                        List.of(
                                booking("PMC2", null),
                                booking("AKE1", null),
                                booking("AKE3", null)),
                        ARRIVAL);

        assertThat(result.passed()).isFalse();
        assertThat(result.severity()).isEqualTo(CheckSeverity.SOFT);
        assertThat(result.reason())
                .isEqualTo("3 booked ULDs have no position and will stay behind: AKE1, AKE3, PMC2");
    }

    @Test
    void noBookingsIsNothingLeftBehind() {
        assertThat(check.evaluate(List.of(), ARRIVAL).passed()).isTrue();
    }

    private static UldBooking booking(String code, LoadingPosition position) {
        return new UldBooking(
                new UldCode(code),
                new Weight(BigDecimal.TEN, WeightUnit.KG),
                new AirportCode("AMS"),
                position);
    }
}
