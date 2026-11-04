package be.kdg.airfreight.cargo.application.cargoplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.application.flight.UnknownFlightException;
import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanState;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.UldNotBookedException;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loading.LoadingNotAllowedException;
import be.kdg.airfreight.cargo.domain.loading.LoadingRefusal;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class UldPositionServiceTest {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");
    private static final LoadingWindowPolicy DEFAULT_POLICY =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    // inside the default window 2026-10-21T20:00Z to 2026-10-22T07:40Z
    private static final Instant INSIDE = Instant.parse("2026-10-22T06:00:00Z");
    private static final UldCode AKE = new UldCode("AKE12345KL");
    private static final UldCode PMC = new UldCode("PMC00001KL");

    private final FlightRepository flights = mock(FlightRepository.class);
    private final CargoPlanRepository cargoPlans = mock(CargoPlanRepository.class);
    // records the order of the calls to flight the plan and the transaction
    private final List<String> steps = new ArrayList<>();

    // runs the work like spring would but only records where the transaction starts and ends
    private final TransactionOperations transaction =
            new TransactionOperations() {
                @Override
                public <T> T execute(TransactionCallback<T> action) {
                    steps.add("begin");
                    T result = action.doInTransaction(new SimpleTransactionStatus());
                    steps.add("commit");
                    return result;
                }
            };

    @Test
    void savesThePlanAfterAPositionChange() {
        CargoPlan cargoPlan = givenPlannedFlightAndPlan();

        service(DEFAULT_POLICY, INSIDE).assignPosition(KEY, AKE, LoadingPosition.RL);

        verify(cargoPlans).save(cargoPlan);
        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.RL);
        assertThat(positionOf(cargoPlan, PMC)).isEqualTo(LoadingPosition.CL);
    }

    @Test
    void savesThePlanAfterClearingAPosition() {
        CargoPlan cargoPlan = givenPlannedFlightAndPlan();

        service(DEFAULT_POLICY, INSIDE).clearPosition(KEY, PMC);

        verify(cargoPlans).save(cargoPlan);
        assertThat(positionOf(cargoPlan, PMC)).isNull();
    }

    // the http call holds no db connection and load and save share one transaction
    @Test
    void asksFlightBeforeTheTransactionAndLoadsAndSavesInsideIt() {
        CargoPlan cargoPlan = givenPlannedFlightAndPlan();
        when(flights.findByFlightKey(KEY))
                .thenAnswer(
                        call -> {
                            steps.add("ask flight");
                            return Optional.of(flight());
                        });
        when(cargoPlans.findByFlightKey(KEY))
                .thenAnswer(
                        call -> {
                            steps.add("load plan");
                            return Optional.of(cargoPlan);
                        });
        doAnswer(call -> steps.add("save plan")).when(cargoPlans).save(any());

        service(DEFAULT_POLICY, INSIDE).assignPosition(KEY, AKE, LoadingPosition.CR);
        service(DEFAULT_POLICY, INSIDE).clearPosition(KEY, AKE);

        assertThat(steps)
                .containsExactly(
                        "ask flight",
                        "begin",
                        "load plan",
                        "save plan",
                        "commit",
                        "ask flight",
                        "begin",
                        "load plan",
                        "save plan",
                        "commit");
    }

    // window 06:00Z to 07:30Z with 2h before std and 30m before etd
    @Test
    void usesTheConfiguredLoadingWindowOffsets() {
        LoadingWindowPolicy configured =
                new LoadingWindowPolicy(Duration.ofHours(2), Duration.ofMinutes(30));
        Instant threeHoursBeforeStd = STD.minus(Duration.ofHours(3));
        Instant oneHourBeforeStd = STD.minus(Duration.ofHours(1));
        Instant twentyFiveMinutesBeforeEtd = STD.minus(Duration.ofMinutes(25));

        assertRefused(configured, threeHoursBeforeStd, LoadingRefusal.WINDOW_NOT_OPEN_YET);
        assertAllowed(configured, oneHourBeforeStd);
        assertRefused(configured, twentyFiveMinutesBeforeEtd, LoadingRefusal.WINDOW_CLOSED);
        assertAllowed(DEFAULT_POLICY, twentyFiveMinutesBeforeEtd);
    }

    @Test
    void unbookedUldOrFlightWithoutPlanIsNotFound() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(flight()));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.empty());
        UldPositionService service = service(DEFAULT_POLICY, INSIDE);

        assertThatThrownBy(() -> service.assignPosition(KEY, AKE, LoadingPosition.CL))
                .isInstanceOf(UldNotBookedException.class)
                .hasMessage("ULD AKE12345KL is not booked on flight HJ221/2026-10-22/BRU");
        assertThatThrownBy(() -> service.clearPosition(KEY, AKE))
                .isInstanceOf(UldNotBookedException.class);
        verify(cargoPlans, never()).save(any());
    }

    @Test
    void changesNothingWhenFlightIsDownOrUnknown() {
        UldPositionService service = service(DEFAULT_POLICY, INSIDE);
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignPosition(KEY, AKE, LoadingPosition.CL))
                .isInstanceOf(FlightLookupUnavailableException.class);
        assertThatThrownBy(() -> service.clearPosition(KEY, AKE))
                .isInstanceOf(UnknownFlightException.class);
        verifyNoInteractions(cargoPlans);
        assertThat(steps).isEmpty();
    }

    @Test
    void refusedChangeIsNotSaved() {
        CargoPlan cargoPlan = givenPlannedFlightAndPlan();
        UldPositionService closed = service(DEFAULT_POLICY, Instant.parse("2026-10-22T07:40:00Z"));

        assertThatThrownBy(() -> closed.assignPosition(KEY, AKE, LoadingPosition.CR))
                .isInstanceOf(LoadingNotAllowedException.class);
        assertThatThrownBy(() -> closed.clearPosition(KEY, PMC))
                .isInstanceOf(LoadingNotAllowedException.class);
        verify(cargoPlans, never()).save(any());
        assertThat(positionOf(cargoPlan, AKE)).isNull();
        assertThat(positionOf(cargoPlan, PMC)).isEqualTo(LoadingPosition.CL);
    }

    private void assertRefused(LoadingWindowPolicy policy, Instant now, LoadingRefusal refusal) {
        givenPlannedFlightAndPlan();

        assertThatThrownBy(() -> service(policy, now).assignPosition(KEY, AKE, LoadingPosition.CR))
                .isInstanceOfSatisfying(
                        LoadingNotAllowedException.class,
                        refused -> assertThat(refused.getRefusal()).isEqualTo(refusal));
    }

    private void assertAllowed(LoadingWindowPolicy policy, Instant now) {
        CargoPlan cargoPlan = givenPlannedFlightAndPlan();

        service(policy, now).assignPosition(KEY, AKE, LoadingPosition.CR);

        assertThat(positionOf(cargoPlan, AKE)).isEqualTo(LoadingPosition.CR);
    }

    // ake has no position and pmc is on cl
    private CargoPlan givenPlannedFlightAndPlan() {
        CargoPlan cargoPlan =
                CargoPlan.restore(
                        KEY,
                        CargoPlanState.DRAFT,
                        List.of(booking(AKE, null), booking(PMC, LoadingPosition.CL)));
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(flight()));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.of(cargoPlan));
        return cargoPlan;
    }

    private UldPositionService service(LoadingWindowPolicy policy, Instant now) {
        LoadingWindowService loadingWindowService =
                new LoadingWindowService(
                        flights, cargoPlans, policy, Clock.fixed(now, ZoneOffset.UTC));
        return new UldPositionService(cargoPlans, loadingWindowService, transaction);
    }

    private static FlightInformation flight() {
        return new FlightInformation(
                KEY,
                new AirportCode("AMS"),
                FlightStatus.PLANNED,
                STD,
                STD.plus(Duration.ofHours(2)),
                STD);
    }

    private static UldBooking booking(UldCode code, LoadingPosition position) {
        return new UldBooking(
                code,
                new Weight(new BigDecimal("1250.5"), WeightUnit.KG),
                new AirportCode("AMS"),
                position);
    }

    // null when the uld has no position
    private static LoadingPosition positionOf(CargoPlan cargoPlan, UldCode code) {
        return cargoPlan.findBooking(code).orElseThrow().getPosition().orElse(null);
    }
}
