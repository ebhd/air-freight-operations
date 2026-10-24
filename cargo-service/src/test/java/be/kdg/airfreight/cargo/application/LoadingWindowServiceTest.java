package be.kdg.airfreight.cargo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.CargoPlan;
import be.kdg.airfreight.cargo.domain.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.CargoPlanState;
import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.FlightRepository;
import be.kdg.airfreight.cargo.domain.FlightStatus;
import be.kdg.airfreight.cargo.domain.LoadingContext;
import be.kdg.airfreight.cargo.domain.LoadingRefusal;
import be.kdg.airfreight.cargo.domain.LoadingWindow;
import be.kdg.airfreight.cargo.domain.LoadingWindowPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoadingWindowServiceTest {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");
    private static final Instant STA = Instant.parse("2026-10-22T10:00:00Z");
    // inside the default window 2026-10-21T20:00Z to 2026-10-22T07:40Z
    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    private final FlightRepository flights = mock(FlightRepository.class);
    private final CargoPlanRepository cargoPlans = mock(CargoPlanRepository.class);
    private final LoadingWindowService service =
            new LoadingWindowService(
                    flights,
                    cargoPlans,
                    new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20)),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void contextUsesFlightEtdAndClock() {
        FlightInformation delayed = flight(FlightStatus.PLANNED, STA, STD.plusSeconds(25 * 60));
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(delayed));

        LoadingContext context = service.contextFor(KEY);

        assertThat(context.flight()).isEqualTo(delayed);
        assertThat(context.window())
                .isEqualTo(
                        new LoadingWindow(
                                Instant.parse("2026-10-21T20:00:00Z"),
                                Instant.parse("2026-10-22T08:05:00Z")));
        assertThat(context.now()).isEqualTo(NOW);
    }

    @Test
    void arrivalTimesDoNotChangeTheWindow() {
        when(flights.findByFlightKey(KEY))
                .thenReturn(Optional.of(flight(FlightStatus.PLANNED, STA, STD)))
                .thenReturn(
                        Optional.of(
                                flight(FlightStatus.PLANNED, STA.plus(Duration.ofHours(3)), STD)));

        LoadingWindow first = service.contextFor(KEY).window();
        LoadingWindow second = service.contextFor(KEY).window();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void statusOfPlanWithoutBookingsAllowsLoadingInsideTheWindow() {
        when(flights.findByFlightKey(KEY))
                .thenReturn(Optional.of(flight(FlightStatus.PLANNED, STA, STD)));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.empty());

        LoadingStatus status = service.statusFor(KEY);

        assertThat(status.finalized()).isFalse();
        assertThat(status.refusal()).isNull();
        assertThat(status.loadingAllowed()).isTrue();
    }

    @Test
    void statusReportsRefusal() {
        when(flights.findByFlightKey(KEY))
                .thenReturn(Optional.of(flight(FlightStatus.PLANNED, STA, STD)));
        when(cargoPlans.findByFlightKey(KEY))
                .thenReturn(
                        Optional.of(CargoPlan.restore(KEY, CargoPlanState.FINALIZED, List.of())));

        LoadingStatus status = service.statusFor(KEY);

        assertThat(status.finalized()).isTrue();
        assertThat(status.refusal()).isEqualTo(LoadingRefusal.PLAN_FINALIZED);
        assertThat(status.loadingAllowed()).isFalse();
    }

    // reading stays possible after departure
    @Test
    void statusOfDepartedFlightIsReturnedNotThrown() {
        when(flights.findByFlightKey(KEY))
                .thenReturn(Optional.of(flight(FlightStatus.DEPARTED, STA, STD)));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.empty());

        assertThat(service.statusFor(KEY).refusal()).isEqualTo(LoadingRefusal.FLIGHT_NOT_PLANNED);
    }

    @Test
    void unknownFlightIsReported() {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.statusFor(KEY))
                .isInstanceOf(UnknownFlightException.class)
                .hasMessage("Unknown flight HJ221/2026-10-22/BRU");
        verifyNoInteractions(cargoPlans);
    }

    @Test
    void flightOperationsDownPropagates() {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        assertThatThrownBy(() -> service.contextFor(KEY))
                .isInstanceOf(FlightLookupUnavailableException.class);
        verifyNoInteractions(cargoPlans);
    }

    private static FlightInformation flight(FlightStatus status, Instant sta, Instant etd) {
        return new FlightInformation(KEY, new AirportCode("AMS"), status, STD, sta, etd);
    }
}
