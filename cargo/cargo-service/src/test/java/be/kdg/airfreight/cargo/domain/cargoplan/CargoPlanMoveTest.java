package be.kdg.airfreight.cargo.domain.cargoplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.Test;

// the cargo of a replanned flight moves to the plan of the new flight
class CargoPlanMoveTest {
    private static final AirportCode BRU = new AirportCode("BRU");
    private static final AirportCode AMS = new AirportCode("AMS");
    private static final FlightKey OLD = new FlightKey("HJ500", LocalDate.of(2026, 11, 10), BRU);
    private static final FlightKey NEW = new FlightKey("HJ500A", LocalDate.of(2026, 11, 11), BRU);
    private static final Instant FINALIZED_AT = Instant.parse("2026-11-10T13:00:00Z");

    @Test
    void bookingsMoveWithTheirPositionsAndTheOldPlanBecomesTheMarker() {
        CargoPlan old =
                CargoPlan.restore(
                        OLD,
                        CargoPlanState.DRAFT,
                        List.of(
                                booking("AKE00001HJ", LoadingPosition.CL),
                                booking("PMC00002HJ", null)));
        CargoPlan target = new CargoPlan(NEW);

        assertThat(old.moveTo(target)).isTrue();

        assertThat(target.getBookings()).hasSize(2);
        assertThat(target.findBooking(new UldCode("AKE00001HJ")).orElseThrow().getPosition())
                .contains(LoadingPosition.CL);
        assertThat(target.findBooking(new UldCode("PMC00002HJ")).orElseThrow().getPosition())
                .isEmpty();
        assertThat(target.getState()).isEqualTo(CargoPlanState.DRAFT);
        assertThat(old.getBookings()).isEmpty();
        assertThat(old.getState()).isEqualTo(CargoPlanState.REPLANNED);
        assertThat(old.isReplanned()).isTrue();
        assertThat(old.getMovedTo()).contains(NEW);
    }

    // a finalized load moves as a draft so the load planner checks and finalizes it again
    @Test
    void finalizedPlanMovesAsADraftAndKeepsItsFinalizedTime() {
        CargoPlan old =
                CargoPlan.restore(
                        OLD,
                        CargoPlanState.FINALIZED,
                        FINALIZED_AT,
                        List.of(booking("AKE00001HJ", LoadingPosition.CL)));
        CargoPlan target = new CargoPlan(NEW);

        old.moveTo(target);

        assertThat(target.getState()).isEqualTo(CargoPlanState.DRAFT);
        assertThat(target.isFinalized()).isFalse();
        assertThat(old.getFinalizedAt()).contains(FINALIZED_AT);
    }

    // a position already taken on the new flight is not stolen
    @Test
    void takenPositionIsDroppedAndTheUldMovesWithoutOne() {
        CargoPlan old =
                CargoPlan.restore(
                        OLD,
                        CargoPlanState.DRAFT,
                        List.of(booking("AKE00001HJ", LoadingPosition.CL)));
        CargoPlan target =
                CargoPlan.restore(
                        NEW,
                        CargoPlanState.DRAFT,
                        List.of(booking("PMC00009HJ", LoadingPosition.CL)));

        old.moveTo(target);

        assertThat(target.findBooking(new UldCode("AKE00001HJ")).orElseThrow().getPosition())
                .isEmpty();
        assertThat(target.findBooking(new UldCode("PMC00009HJ")).orElseThrow().getPosition())
                .contains(LoadingPosition.CL);
    }

    @Test
    void sameUldOnTheNewFlightKeepsTheNewFlightsBooking() {
        CargoPlan old =
                CargoPlan.restore(
                        OLD,
                        CargoPlanState.DRAFT,
                        List.of(booking("AKE00001HJ", LoadingPosition.CL)));
        UldBooking already =
                new UldBooking(
                        new UldCode("AKE00001HJ"),
                        new Weight(new BigDecimal("999"), WeightUnit.LB),
                        new AirportCode("ATH"),
                        LoadingPosition.CR);
        CargoPlan target = CargoPlan.restore(NEW, CargoPlanState.DRAFT, List.of(already));

        old.moveTo(target);

        assertThat(target.getBookings()).containsExactly(already);
        assertThat(old.getBookings()).isEmpty();
    }

    @Test
    void secondMoveChangesNothing() {
        CargoPlan old =
                CargoPlan.restore(OLD, CargoPlanState.DRAFT, List.of(booking("AKE00001HJ", null)));
        CargoPlan target = new CargoPlan(NEW);
        old.moveTo(target);
        CargoPlan another = new CargoPlan(NEW);

        assertThat(old.moveTo(another)).isFalse();

        assertThat(another.getBookings()).isEmpty();
        assertThat(old.getMovedTo()).contains(NEW);
    }

    @Test
    void finalizedOrReplannedTargetIsRefused() {
        CargoPlan old =
                CargoPlan.restore(OLD, CargoPlanState.DRAFT, List.of(booking("AKE00001HJ", null)));
        CargoPlan finalized =
                CargoPlan.restore(NEW, CargoPlanState.FINALIZED, FINALIZED_AT, List.of());
        CargoPlan replanned =
                CargoPlan.restore(
                        NEW,
                        CargoPlanState.REPLANNED,
                        null,
                        new FlightKey("HJ500B", LocalDate.of(2026, 11, 12), BRU),
                        List.of());

        assertThatThrownBy(() -> old.moveTo(finalized))
                .isInstanceOf(BookingRejectedException.class)
                .hasMessageContaining("is FINALIZED");
        assertThatThrownBy(() -> old.moveTo(replanned))
                .isInstanceOf(BookingRejectedException.class)
                .hasMessageContaining("is REPLANNED");
        assertThat(old.getState()).isEqualTo(CargoPlanState.DRAFT);
        assertThat(old.getBookings()).hasSize(1);
    }

    @Test
    void replannedPlanRefusesNewBookingsAndLoading() {
        CargoPlan old = CargoPlan.restore(OLD, CargoPlanState.REPLANNED, null, NEW, List.of());

        assertThatThrownBy(
                        () ->
                                old.bookUld(
                                        new UldCode("AKE00003HJ"),
                                        new Weight(new BigDecimal("100"), WeightUnit.KG),
                                        AMS))
                .isInstanceOf(BookingRejectedException.class)
                .hasMessage(
                        "Cargo of flight HJ500/2026-11-10/BRU moved to HJ500A/2026-11-11/BRU,"
                                + " book on that flight");
        // even when the flight copy still says planned
        Instant std = Instant.parse("2026-11-10T20:00:00Z");
        LoadingContext context =
                new LoadingContext(
                        new FlightInformation(
                                OLD, AMS, FlightStatus.PLANNED, std, std.plusSeconds(7200), std),
                        new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20))
                                .windowFor(std, std),
                        Instant.parse("2026-11-10T14:00:00Z"));
        assertThat(old.loadingRefusal(context)).contains(LoadingRefusal.FLIGHT_NOT_PLANNED);
    }

    private static UldBooking booking(String code, LoadingPosition position) {
        return new UldBooking(
                new UldCode(code),
                new Weight(new BigDecimal("1000"), WeightUnit.KG),
                AMS,
                position);
    }
}
