package be.kdg.airfreight.cargo.application.cargoplan;

import static be.kdg.airfreight.cargo.support.TestHelper.restoredPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanState;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadFinalized;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadNotFinalizedException;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loadcheck.LateralBalanceCheck;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadChecks;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class FinalizeLoadServiceTest {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-10-22T06:00:00Z");

    private final FlightRepository flights = mock(FlightRepository.class);
    private final CargoPlanRepository cargoPlans = mock(CargoPlanRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final FinalizeLoadService service =
            new FinalizeLoadService(
                    cargoPlans,
                    new LoadingWindowService(
                            flights,
                            cargoPlans,
                            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20)),
                            Clock.fixed(INSIDE, ZoneOffset.UTC)),
                    new LoadChecks(new LateralBalanceCheck(new BigDecimal("0.10"))),
                    events);

    @Test
    void savesThePlanAndPublishesItsEvent() {
        CargoPlan cargoPlan = givenPlan(booking("AKE1", LoadingPosition.A1, "1000"));

        LoadFinalized event = service.finalizeLoad(KEY);

        assertThat(cargoPlan.isFinalized()).isTrue();
        verify(cargoPlans).save(cargoPlan);
        ArgumentCaptor<LoadFinalized> added = ArgumentCaptor.forClass(LoadFinalized.class);
        verify(events).publishEvent(added.capture());
        assertThat(added.getValue()).isSameAs(event);
        assertThat(event.ulds()).hasSize(1);
    }

    @Test
    void hardFailureSavesNothingAndAddsNoEvent() {
        givenPlan(
                booking("AKE1", LoadingPosition.CL, "1000"),
                booking("AKE2", LoadingPosition.CR, "1101"));

        assertThatThrownBy(() -> service.finalizeLoad(KEY))
                .isInstanceOf(LoadNotFinalizedException.class);

        verify(cargoPlans, never()).save(any());
        verifyNoInteractions(events);
    }

    // no bookings yet is an empty load that can still be finalized
    @Test
    void flightWithoutPlanGetsAFinalizedEmptyPlan() {
        givenFlight();
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.empty());

        LoadFinalized event = service.finalizeLoad(KEY);

        ArgumentCaptor<CargoPlan> saved = ArgumentCaptor.forClass(CargoPlan.class);
        verify(cargoPlans).save(saved.capture());
        assertThat(saved.getValue().isFinalized()).isTrue();
        assertThat(event.ulds()).isEmpty();
    }

    @Test
    void flightOperationsDownPublishesNoEvent() {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        assertThatThrownBy(() -> service.finalizeLoad(KEY))
                .isInstanceOf(FlightLookupUnavailableException.class);

        verifyNoInteractions(events);
    }

    private void givenFlight() {
        when(flights.findByFlightKey(KEY))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        KEY,
                                        new AirportCode("AMS"),
                                        FlightStatus.PLANNED,
                                        STD,
                                        STD.plus(Duration.ofHours(2)),
                                        STD,
                                        STD.plus(Duration.ofHours(2)))));
    }

    private CargoPlan givenPlan(UldBooking... bookings) {
        givenFlight();
        CargoPlan cargoPlan = restoredPlan(KEY, CargoPlanState.DRAFT, List.of(bookings));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.of(cargoPlan));
        return cargoPlan;
    }

    private static UldBooking booking(String code, LoadingPosition position, String kilograms) {
        return new UldBooking(
                new UldCode(code),
                new Weight(new BigDecimal(kilograms), WeightUnit.KG),
                new AirportCode("AMS"),
                position);
    }
}
