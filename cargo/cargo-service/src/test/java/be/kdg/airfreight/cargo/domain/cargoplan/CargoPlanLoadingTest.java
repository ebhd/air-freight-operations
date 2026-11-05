package be.kdg.airfreight.cargo.domain.cargoplan;

import static be.kdg.airfreight.cargo.support.TestHelper.plannedFlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import be.kdg.airfreight.cargo.domain.loading.LoadingNotAllowedException;
import be.kdg.airfreight.cargo.domain.loading.LoadingRefusal;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindow;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CargoPlanLoadingTest {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");
    private static final LoadingWindowPolicy POLICY =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    // 2026-10-21T20:00Z to 2026-10-22T07:40Z
    private static final LoadingWindow WINDOW = POLICY.windowFor(STD, STD);
    private static final Instant INSIDE = Instant.parse("2026-10-22T06:00:00Z");

    @Test
    void allowsPlannedFlightInsideOpenWindow() {
        CargoPlan cargoPlan = new CargoPlan(KEY);
        LoadingContext context = context(KEY, FlightStatus.PLANNED, STD, INSIDE);

        assertThat(cargoPlan.loadingRefusal(context)).isEmpty();
        assertThatCode(() -> cargoPlan.requireLoadingAllowed(context)).doesNotThrowAnyException();
    }

    static Stream<Arguments> violatedConditions() {
        return Stream.of(
                arguments(
                        CargoPlanState.DRAFT,
                        FlightStatus.DEPARTED,
                        INSIDE,
                        LoadingRefusal.FLIGHT_NOT_PLANNED,
                        "the flight is no longer planned"),
                arguments(
                        CargoPlanState.DRAFT,
                        FlightStatus.LANDED,
                        INSIDE,
                        LoadingRefusal.FLIGHT_NOT_PLANNED,
                        "the flight is no longer planned"),
                arguments(
                        CargoPlanState.DRAFT,
                        FlightStatus.PLANNED,
                        WINDOW.opensAt().minusNanos(1),
                        LoadingRefusal.WINDOW_NOT_OPEN_YET,
                        "the loading window opens at 2026-10-21T20:00:00Z"),
                arguments(
                        CargoPlanState.DRAFT,
                        FlightStatus.PLANNED,
                        WINDOW.closesAt(),
                        LoadingRefusal.WINDOW_CLOSED,
                        "the loading window closed at 2026-10-22T07:40:00Z"),
                arguments(
                        CargoPlanState.DRAFT,
                        FlightStatus.PLANNED,
                        WINDOW.closesAt().plus(Duration.ofHours(1)),
                        LoadingRefusal.WINDOW_CLOSED,
                        "the loading window closed at 2026-10-22T07:40:00Z"),
                arguments(
                        CargoPlanState.FINALIZED,
                        FlightStatus.PLANNED,
                        INSIDE,
                        LoadingRefusal.PLAN_FINALIZED,
                        "the cargo plan is finalized"));
    }

    @ParameterizedTest
    @MethodSource("violatedConditions")
    void refusesEachViolatedCondition(
            CargoPlanState state,
            FlightStatus status,
            Instant now,
            LoadingRefusal expected,
            String reason) {
        CargoPlan cargoPlan = CargoPlan.restore(KEY, state, List.of());
        LoadingContext context = context(KEY, status, STD, now);

        assertThat(cargoPlan.loadingRefusal(context)).contains(expected);
        assertThatThrownBy(() -> cargoPlan.requireLoadingAllowed(context))
                .isInstanceOfSatisfying(
                        LoadingNotAllowedException.class,
                        refused -> {
                            assertThat(refused.getFlightKey()).isEqualTo(KEY);
                            assertThat(refused.getRefusal()).isEqualTo(expected);
                            assertThat(refused.getWindow()).isEqualTo(WINDOW);
                        })
                .hasMessage("Loading on HJ221/2026-10-22/BRU is not allowed: " + reason);
    }

    // ac4 the same delay reopens a draft but never a finalized plan
    @Test
    void laterDelayNeverReopensFinalizedPlan() {
        Instant now = Instant.parse("2026-10-22T07:50:00Z");
        LoadingContext beforeDelay = context(KEY, FlightStatus.PLANNED, STD, now);
        LoadingContext afterDelay =
                context(KEY, FlightStatus.PLANNED, STD.plus(Duration.ofMinutes(60)), now);
        CargoPlan draft = new CargoPlan(KEY);
        CargoPlan finalized = CargoPlan.restore(KEY, CargoPlanState.FINALIZED, List.of());

        assertThat(draft.loadingRefusal(beforeDelay)).contains(LoadingRefusal.WINDOW_CLOSED);
        assertThat(draft.loadingRefusal(afterDelay)).isEmpty();
        assertThat(finalized.loadingRefusal(afterDelay)).contains(LoadingRefusal.PLAN_FINALIZED);
        assertThatThrownBy(() -> finalized.requireLoadingAllowed(afterDelay))
                .isInstanceOf(LoadingNotAllowedException.class);
    }

    @Test
    void finalizedWinsOverOtherReasons() {
        CargoPlan finalized = CargoPlan.restore(KEY, CargoPlanState.FINALIZED, List.of());
        LoadingContext departedAndClosed =
                context(KEY, FlightStatus.DEPARTED, STD, WINDOW.closesAt().plusSeconds(1));

        assertThat(finalized.loadingRefusal(departedAndClosed))
                .contains(LoadingRefusal.PLAN_FINALIZED);
    }

    @Test
    void rejectsContextOfAnotherFlight() {
        FlightKey otherKey =
                new FlightKey("HJ222", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
        LoadingContext otherFlight = context(otherKey, FlightStatus.PLANNED, STD, INSIDE);

        assertThatThrownBy(() -> new CargoPlan(KEY).loadingRefusal(otherFlight))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage(
                        "Loading context of HJ222/2026-10-22/BRU is not for cargo plan"
                                + " HJ221/2026-10-22/BRU");
    }

    // q07 is open so a booking keeps its own rule and the loading window does not limit it
    @Test
    void bookingIsNotLimitedByTheLoadingWindow() {
        CargoPlan draft = new CargoPlan(KEY);
        LoadingContext closed =
                context(KEY, FlightStatus.PLANNED, STD, WINDOW.closesAt().plusSeconds(1));
        assertThat(draft.loadingRefusal(closed)).isPresent();

        boolean booked =
                draft.bookUld(
                        new UldCode("AKE12345KL"),
                        weight(),
                        new AirportCode("AMS"),
                        plannedFlight(KEY));

        assertThat(booked).isTrue();
        assertThat(draft.getBookings()).hasSize(1);
    }

    // d4 our reading of car-14: a finalized load takes no new uld
    @Test
    void finalizedPlanRejectsANewBooking() {
        CargoPlan finalized = CargoPlan.restore(KEY, CargoPlanState.FINALIZED, List.of());

        assertThatThrownBy(
                        () ->
                                finalized.bookUld(
                                        new UldCode("AKE12345KL"),
                                        weight(),
                                        new AirportCode("AMS"),
                                        plannedFlight(KEY)))
                .isInstanceOf(BookingRejectedException.class)
                .hasMessage(
                        "Cargo plan of flight HJ221/2026-10-22/BRU is finalized, no ULD can be"
                                + " added");
        assertThat(finalized.getBookings()).isEmpty();
    }

    // car-03 a repeat is ignored and that does not change after finalizing
    @Test
    void finalizedPlanStillIgnoresARepeatedBooking() {
        UldBooking existing =
                new UldBooking(new UldCode("AKE12345KL"), weight(), new AirportCode("AMS"));
        CargoPlan finalized = CargoPlan.restore(KEY, CargoPlanState.FINALIZED, List.of(existing));

        assertThat(
                        finalized.bookUld(
                                new UldCode("AKE12345KL"),
                                weight(),
                                new AirportCode("AMS"),
                                plannedFlight(KEY)))
                .isFalse();
    }

    private static Weight weight() {
        return new Weight(new BigDecimal("1250.5"), WeightUnit.KG);
    }

    private static LoadingContext context(
            FlightKey flightKey, FlightStatus status, Instant etd, Instant now) {
        FlightInformation flight =
                new FlightInformation(
                        flightKey,
                        new AirportCode("AMS"),
                        status,
                        STD,
                        STD.plus(Duration.ofHours(2)),
                        etd);
        return new LoadingContext(flight, POLICY.windowFor(STD, etd), now);
    }
}
