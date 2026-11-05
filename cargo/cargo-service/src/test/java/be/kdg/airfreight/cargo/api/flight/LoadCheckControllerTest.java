package be.kdg.airfreight.cargo.api.flight;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class LoadCheckControllerTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-19/BRU/load-check";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
    // inside the window 2026-10-18T20:00Z to 2026-10-19T07:40Z
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
    void returnsEveryCheckWithNameSeverityResultAndReason() throws Exception {
        givenPlan(
                CargoPlanState.DRAFT,
                booking("AKE1", LoadingPosition.CL, "1000"),
                booking("AKE2", LoadingPosition.CR, "1101"),
                booking("PMC3", null, "500"));

        mvcAt(INSIDE)
                .perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hardChecksPassed").value(false))
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[0].name").value("Lateral balance"))
                .andExpect(jsonPath("$.results[0].severity").value("HARD"))
                .andExpect(jsonPath("$.results[0].passed").value(false))
                .andExpect(
                        jsonPath("$.results[0].reason")
                                .value(
                                        "Right side is too heavy: left 1000 KG, right 1101 KG, the"
                                                + " heavier side may weigh at most 1100 KG"))
                .andExpect(jsonPath("$.results[1].name").value("Offloading"))
                .andExpect(jsonPath("$.results[1].passed").value(true))
                .andExpect(jsonPath("$.results[2].name").value("Not all cargo loaded"))
                .andExpect(jsonPath("$.results[2].severity").value("SOFT"))
                .andExpect(jsonPath("$.results[2].passed").value(false));
        // checking saves nothing
        verify(cargoPlans, never()).save(any());
    }

    @Test
    void flightWithoutBookingsGetsAnEmptyReport() throws Exception {
        givenFlight();

        mvcAt(INSIDE)
                .perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hardChecksPassed").value(true))
                .andExpect(jsonPath("$.results", hasSize(3)));
    }

    @Test
    void closedLoadingWindowIsConflict() throws Exception {
        givenPlan(CargoPlanState.DRAFT);

        mvcAt(Instant.parse("2026-10-19T07:40:00Z"))
                .perform(get(URL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("WINDOW_CLOSED"));
    }

    @Test
    void finalizedPlanIsConflict() throws Exception {
        givenPlan(CargoPlanState.FINALIZED);

        mvcAt(INSIDE)
                .perform(get(URL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("PLAN_FINALIZED"));
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        mvcAt(INSIDE).perform(get(URL)).andExpect(status().isNotFound());
    }

    @Test
    void flightOperationsDownIsServiceUnavailable() throws Exception {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        mvcAt(INSIDE).perform(get(URL)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidFlightKeyIsBadRequest() throws Exception {
        mvcAt(INSIDE)
                .perform(get("/api/cargo-plans/HJ-221/2026-10-19/BRU/load-check"))
                .andExpect(status().isBadRequest());
    }

    private MockMvc mvcAt(Instant now) {
        when(clock.instant()).thenReturn(now);
        return mvc;
    }

    private void givenFlight() {
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
    }

    private void givenPlan(CargoPlanState state, UldBooking... bookings) {
        givenFlight();
        data.save(CargoPlan.restore(KEY, state, List.of(bookings)));
        clearInvocations(cargoPlans);
    }

    private static UldBooking booking(String code, LoadingPosition position, String kilograms) {
        return new UldBooking(
                new UldCode(code),
                new Weight(new BigDecimal(kilograms), WeightUnit.KG),
                new AirportCode("AMS"),
                position);
    }
}
