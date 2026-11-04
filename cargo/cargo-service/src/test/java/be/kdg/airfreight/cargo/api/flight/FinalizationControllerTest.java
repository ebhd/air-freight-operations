package be.kdg.airfreight.cargo.api.flight;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.application.cargoplan.CargoEventOutbox;
import be.kdg.airfreight.cargo.application.cargoplan.FinalizeLoadService;
import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanState;
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
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionOperations;

class FinalizationControllerTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-19/BRU/finalization";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
    private static final LoadingWindowPolicy POLICY =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    private static final Instant INSIDE = Instant.parse("2026-10-19T06:00:00Z");

    private final FlightRepository flights = mock(FlightRepository.class);
    private final CargoPlanRepository cargoPlans = mock(CargoPlanRepository.class);
    private final CargoEventOutbox outbox = mock(CargoEventOutbox.class);

    @Test
    void finalizedLoadReturnsTheTimeAndTheReport() throws Exception {
        CargoPlan cargoPlan =
                givenPlan(
                        CargoPlanState.DRAFT,
                        // a single position counts for neither side so the balance passes
                        booking("AKE1", LoadingPosition.A1),
                        booking("PMC3", null));

        mvcAt(INSIDE)
                .perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalizedAt").value("2026-10-19T06:00:00Z"))
                .andExpect(jsonPath("$.report.hardChecksPassed").value(true))
                .andExpect(jsonPath("$.report.results", hasSize(3)))
                .andExpect(jsonPath("$.report.results[2].severity").value("SOFT"))
                .andExpect(jsonPath("$.report.results[2].passed").value(false));
        verify(cargoPlans).save(cargoPlan);
        verify(outbox).add(any());
    }

    @Test
    void hardFailureIsConflictWithTheWholeReport() throws Exception {
        givenPlan(
                CargoPlanState.DRAFT,
                booking("AKE1", LoadingPosition.CL),
                new UldBooking(
                        new UldCode("AKE2"),
                        new Weight(new BigDecimal("1101"), WeightUnit.KG),
                        new AirportCode("AMS"),
                        LoadingPosition.CR));

        mvcAt(INSIDE)
                .perform(post(URL))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.refusal").value("HARD_CHECKS_FAILED"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Load of flight HJ221/2026-10-19/BRU is not finalized: a"
                                                + " hard check failed"))
                .andExpect(jsonPath("$.report.hardChecksPassed").value(false))
                .andExpect(jsonPath("$.report.results", hasSize(3)))
                .andExpect(jsonPath("$.report.results[0].name").value("Lateral balance"))
                .andExpect(jsonPath("$.report.results[0].passed").value(false));
        verify(cargoPlans, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void finalizedPlanIsConflict() throws Exception {
        givenPlan(CargoPlanState.FINALIZED);

        mvcAt(INSIDE)
                .perform(post(URL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("PLAN_FINALIZED"));
        verifyNoInteractions(outbox);
    }

    @Test
    void closedLoadingWindowIsConflict() throws Exception {
        givenPlan(CargoPlanState.DRAFT);

        mvcAt(Instant.parse("2026-10-19T07:40:00Z"))
                .perform(post(URL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("WINDOW_CLOSED"));
    }

    @Test
    void concurrentPositionChangeIsConflict() throws Exception {
        givenPlan(CargoPlanState.DRAFT, booking("AKE1", LoadingPosition.A1));
        doThrow(new ObjectOptimisticLockingFailureException("cargo_plans", "HJ221"))
                .when(cargoPlans)
                .save(any());

        mvcAt(INSIDE)
                .perform(post(URL))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The cargo plan was changed by someone else at the same"
                                                + " time. Reload and try again."));
    }

    @Test
    void flightOperationsDownIsServiceUnavailable() throws Exception {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        mvcAt(INSIDE).perform(post(URL)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(cargoPlans, outbox);
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        mvcAt(INSIDE).perform(post(URL)).andExpect(status().isNotFound());
    }

    private MockMvc mvcAt(Instant now) {
        LoadingWindowService loadingWindowService =
                new LoadingWindowService(
                        flights, cargoPlans, POLICY, Clock.fixed(now, ZoneOffset.UTC));
        FinalizeLoadService finalizeLoadService =
                new FinalizeLoadService(
                        cargoPlans,
                        loadingWindowService,
                        new LoadChecks(new LateralBalanceCheck(new BigDecimal("0.10"))),
                        outbox,
                        TransactionOperations.withoutTransaction());
        return MockMvcBuilders.standaloneSetup(new FinalizationController(finalizeLoadService))
                .setControllerAdvice(new CargoApiExceptionHandler())
                .build();
    }

    private CargoPlan givenPlan(CargoPlanState state, UldBooking... bookings) {
        when(flights.findByFlightKey(KEY))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        KEY,
                                        new AirportCode("AMS"),
                                        FlightStatus.PLANNED,
                                        Instant.parse("2026-10-19T08:00:00Z"),
                                        Instant.parse("2026-10-19T10:00:00Z"),
                                        Instant.parse("2026-10-19T08:00:00Z"))));
        CargoPlan cargoPlan = CargoPlan.restore(KEY, state, List.of(bookings));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.of(cargoPlan));
        return cargoPlan;
    }

    private static UldBooking booking(String code, LoadingPosition position) {
        return new UldBooking(
                new UldCode(code),
                new Weight(new BigDecimal("1000"), WeightUnit.KG),
                new AirportCode("AMS"),
                position);
    }
}
