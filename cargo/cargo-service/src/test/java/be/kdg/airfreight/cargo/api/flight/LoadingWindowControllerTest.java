package be.kdg.airfreight.cargo.api.flight;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.support.PostgresTestConfiguration;
import be.kdg.airfreight.cargo.support.TestHelper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class LoadingWindowControllerTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-22/BRU/loading-window";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    // 10:00 brussels so the default window is 2026-10-21T20:00Z to 2026-10-22T07:40Z
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");

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
    void returnsWindowAndRefusal() throws Exception {
        givenFlight(FlightStatus.PLANNED);

        mvcAt(Instant.parse("2026-10-21T18:00:00Z"))
                .perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("HJ221"))
                .andExpect(jsonPath("$.scheduledDepartureDate").value("2026-10-22"))
                .andExpect(jsonPath("$.departureAirportCode").value("BRU"))
                .andExpect(jsonPath("$.flightStatus").value("PLANNED"))
                .andExpect(jsonPath("$.opensAt").value("2026-10-21T20:00:00Z"))
                .andExpect(jsonPath("$.closesAt").value("2026-10-22T07:40:00Z"))
                .andExpect(jsonPath("$.finalized").value(false))
                .andExpect(jsonPath("$.checkedAt").value("2026-10-21T18:00:00Z"))
                .andExpect(jsonPath("$.loadingAllowed").value(false))
                .andExpect(jsonPath("$.refusal").value("WINDOW_NOT_OPEN_YET"));
    }

    @Test
    void openWindowAllowsLoading() throws Exception {
        givenFlight(FlightStatus.PLANNED);

        mvcAt(Instant.parse("2026-10-22T06:00:00Z"))
                .perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loadingAllowed").value(true))
                .andExpect(jsonPath("$.refusal").value(nullValue()));
    }

    // reading stays possible after departure
    @Test
    void departedFlightIsReadNotRefused() throws Exception {
        givenFlight(FlightStatus.DEPARTED);

        mvcAt(Instant.parse("2026-10-22T09:00:00Z"))
                .perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightStatus").value("DEPARTED"))
                .andExpect(jsonPath("$.refusal").value("FLIGHT_NOT_PLANNED"));
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        mvcAt(STD)
                .perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Unknown flight HJ221/2026-10-22/BRU"));
    }

    @Test
    void flightOperationsDownIsServiceUnavailable() throws Exception {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        mvcAt(STD)
                .perform(get(URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(
                        jsonPath("$.detail")
                                .value("Flight Operations is unavailable, try again later"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/cargo-plans/HJ-221/2026-10-22/BRU/loading-window",
                "/api/cargo-plans/HJ221/2026-10-22/BR/loading-window",
                "/api/cargo-plans/HJ221/2026-13-01/BRU/loading-window"
            })
    void invalidFlightKeyIsBadRequest(String url) throws Exception {
        mvcAt(STD).perform(get(url)).andExpect(status().isBadRequest());

        verifyNoInteractions(flights, cargoPlans);
    }

    private void givenFlight(FlightStatus status) {
        when(flights.findByFlightKey(KEY))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        KEY,
                                        new AirportCode("AMS"),
                                        status,
                                        STD,
                                        Instant.parse("2026-10-22T10:00:00Z"),
                                        STD)));
    }

    private MockMvc mvcAt(Instant now) {
        when(clock.instant()).thenReturn(now);
        return mvc;
    }
}
