package be.kdg.airfreight.cargo.api.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import be.kdg.airfreight.cargo.support.PostgresTestConfiguration;
import be.kdg.airfreight.cargo.support.TestHelper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "postgres"})
@Import({PostgresTestConfiguration.class, TestHelper.class})
@WithMockUser(roles = "load-planner")
class FinalizationControllerTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-19/BRU/finalization";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
    private static final Instant INSIDE = Instant.parse("2026-10-19T06:00:00Z");

    @MockitoBean private FlightRepository flights;
    @MockitoSpyBean private CargoPlanRepository cargoPlans;

    @Autowired private MockMvc mvc;
    @Autowired private TestHelper data;
    @MockitoBean private Clock clock;
    @MockitoBean private RabbitTemplate rabbit;
    @MockitoBean private JwtDecoder jwtDecoder;

    @MockitoBean(
            name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    private ScheduledAnnotationBeanPostProcessor scheduling;

    @BeforeEach
    void setUp() {
        data.cleanUp();
        data.prepare(clock, rabbit, Instant.parse("2026-10-19T06:00:00Z"));
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void finalizedLoadReturnsTheTimeAndTheReport() throws Exception {
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
        assertThat(data.find(KEY).getState()).isEqualTo(CargoPlanState.FINALIZED);
        assertThat(data.outboxCount()).isEqualTo(1);
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
        assertThat(data.outboxCount()).isZero();
    }

    @Test
    void finalizedPlanIsConflict() throws Exception {
        givenPlan(CargoPlanState.FINALIZED);

        mvcAt(INSIDE)
                .perform(post(URL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("PLAN_FINALIZED"));
        assertThat(data.outboxCount()).isZero();
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
        verifyNoInteractions(cargoPlans);
        assertThat(data.outboxCount()).isZero();
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        mvcAt(INSIDE).perform(post(URL)).andExpect(status().isNotFound());
    }

    private MockMvc mvcAt(Instant now) {
        when(clock.instant()).thenReturn(now);
        return mvc;
    }

    private void givenPlan(CargoPlanState state, UldBooking... bookings) {
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
        data.save(cargoPlan);
        clearInvocations(cargoPlans);
    }

    private static UldBooking booking(String code, LoadingPosition position) {
        return new UldBooking(
                new UldCode(code),
                new Weight(new BigDecimal("1000"), WeightUnit.KG),
                new AirportCode("AMS"),
                position);
    }
}
