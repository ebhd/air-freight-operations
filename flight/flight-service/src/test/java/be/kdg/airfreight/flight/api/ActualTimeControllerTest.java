package be.kdg.airfreight.flight.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.support.TestHelper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
@Import(TestHelper.class)
@WithMockUser(roles = "flight-planner")
class ActualTimeControllerTest {
    private static final String FLIGHT = "/api/flights/HJ221/2026-10-19/BRU";
    private static final FlightKey KEY = new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

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
        data.prepare(clock, rabbit, NOW);
        Flight flight =
                Flight.create(
                        KEY,
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-10-19T08:00:00Z"),
                        Instant.parse("2026-10-19T10:00:00Z"));
        flight.recordFinalizedLoad();
        data.save(flight);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void departureAnswersWithTheDepartedFlight() throws Exception {
        register("departure", "{\"actualDeparture\":\"2026-10-19T08:03:00Z\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPARTED"))
                .andExpect(jsonPath("$.actualDeparture").value("2026-10-19T08:03:00Z"))
                .andExpect(jsonPath("$.actualArrival").isEmpty());
    }

    @Test
    void arrivalAfterDepartureAnswersWithTheLandedFlight() throws Exception {
        register("departure", "{\"actualDeparture\":\"2026-10-19T08:03:00Z\"}");

        register("arrival", "{\"actualArrival\":\"2026-10-19T09:58:00Z\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LANDED"))
                .andExpect(jsonPath("$.actualDeparture").value("2026-10-19T08:03:00Z"))
                .andExpect(jsonPath("$.actualArrival").value("2026-10-19T09:58:00Z"));
    }

    @Test
    void tooLateIsAConflictWithTheLimit() throws Exception {
        register("departure", "{\"actualDeparture\":\"2026-10-19T08:05:01Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("TOO_LATE"))
                .andExpect(jsonPath("$.latestAllowed").value("2026-10-19T08:05:00Z"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The actual departure 2026-10-19T08:05:01Z is later than"
                                                + " the estimated departure 2026-10-19T08:00:00Z plus"
                                                + " the tolerance of 5 minutes"
                                                + " (2026-10-19T08:05:00Z). Add a departure delay"
                                                + " first, then register the departure."));
    }

    @Test
    void futureTimeIsAConflictWithoutALimit() throws Exception {
        register("departure", "{\"actualDeparture\":\"2026-10-19T12:00:01Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("IN_THE_FUTURE"))
                .andExpect(jsonPath("$.latestAllowed").doesNotExist());
    }

    @Test
    void arrivalBeforeDepartureIsAConflict() throws Exception {
        register("arrival", "{\"actualArrival\":\"2026-10-19T09:58:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("NOT_DEPARTED"));
    }

    @Test
    void secondDepartureIsAConflict() throws Exception {
        register("departure", "{\"actualDeparture\":\"2026-10-19T08:03:00Z\"}");

        register("departure", "{\"actualDeparture\":\"2026-10-19T08:04:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("NOT_PLANNED"));
    }

    @Test
    void missingTimeIsBadRequestWithTheReason() throws Exception {
        register("departure", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("actual departure is required"));
        // the status is checked first, so only a departed flight gets to the missing time
        register("departure", "{\"actualDeparture\":\"2026-10-19T08:03:00Z\"}");
        register("arrival", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("actual arrival is required"));
    }

    // only a utc instant is read, the page converts brussels time itself
    @Test
    void unreadableTimeIsBadRequest() throws Exception {
        register("departure", "{\"actualDeparture\":\"21:03\"}").andExpect(status().isBadRequest());
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        mvc.perform(
                        post("/api/flights/HJ999/2026-10-19/BRU/departure")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"actualDeparture\":\"2026-10-19T08:03:00Z\"}"))
                .andExpect(status().isNotFound());
    }

    private ResultActions register(String action, String body) throws Exception {
        return mvc.perform(
                post(FLIGHT + "/" + action).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
