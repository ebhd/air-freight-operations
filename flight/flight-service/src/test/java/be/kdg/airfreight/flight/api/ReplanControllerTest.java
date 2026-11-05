package be.kdg.airfreight.flight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import be.kdg.airfreight.flight.support.TestHelper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

// the replanning endpoint with the plan example: HJ500 with 400 + 200 minutes of delay
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
@Import(TestHelper.class)
@WithMockUser(roles = "flight-planner")
class ReplanControllerTest {
    private static final FlightKey KEY = new FlightKey("HJ500", LocalDate.of(2026, 11, 10), "BRU");
    private static final String PATH = "/api/flights/HJ500/2026-11-10/BRU/replanning";
    private static final UUID DEPARTURE_DELAY = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-10T14:03:12Z");

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
        data.save(
                Flight.restore(
                        KEY,
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-11-10T20:00:00Z"),
                        Instant.parse("2026-11-10T22:00:00Z"),
                        FlightStatus.PLANNED,
                        List.of(Delay.restore(DEPARTURE_DELAY, 400, "71", "Fog")),
                        List.of(Delay.restore(UUID.randomUUID(), 200, "71", "Holding"))));
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void replanReturnsTheOldFlightWithItsReplanning() throws Exception {
        replan(
                        """
                        {"delayType":"DEPARTURE","delayId":null,"minutes":140,"reasonCode":"71","description":"Hydraulic leak"}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("HJ500"))
                .andExpect(jsonPath("$.status").value("REPLANNED"))
                .andExpect(jsonPath("$.replanning.newFlightNumber").value("HJ500A"))
                .andExpect(jsonPath("$.replanning.newScheduledDepartureDate").value("2026-11-11"))
                .andExpect(
                        jsonPath("$.replanning.newScheduledDeparture")
                                .value("2026-11-11T05:00:00Z"))
                .andExpect(
                        jsonPath("$.replanning.newScheduledArrival").value("2026-11-11T10:20:00Z"))
                .andExpect(jsonPath("$.replanning.replacementCreated").value(false))
                .andExpect(jsonPath("$.replanning.replannedAt").value("2026-11-10T14:03:12Z"))
                .andExpect(jsonPath("$.replanning.delayType").value("DEPARTURE"))
                .andExpect(jsonPath("$.replanning.minutes").value(140))
                .andExpect(jsonPath("$.replanning.reasonCode").value("71"))
                .andExpect(jsonPath("$.replanning.description").value("Hydraulic leak"))
                // the old delays and estimates stay
                .andExpect(jsonPath("$.departureDelays.length()").value(1))
                .andExpect(jsonPath("$.estimatedDeparture").value("2026-11-11T02:40:00Z"));
        assertThat(data.find(KEY).status()).isEqualTo(FlightStatus.REPLANNED);
    }

    // the new flight shows which flight it replaces, so the history reads both ways
    @Test
    void replacementFlightShowsTheFlightItReplaces() throws Exception {
        FlightKey newKey = new FlightKey("HJ500A", LocalDate.of(2026, 11, 11), "BRU");
        Flight replacement =
                Flight.create(
                        newKey,
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-11-11T05:00:00Z"),
                        Instant.parse("2026-11-11T10:20:00Z"));
        replacement.recordReplaces(KEY);
        data.save(replacement);

        mvc.perform(get("/api/flights/HJ500A/2026-11-11/BRU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replanning").value(nullValue()))
                .andExpect(jsonPath("$.replaces.flightNumber").value("HJ500"))
                .andExpect(jsonPath("$.replaces.scheduledDepartureDate").value("2026-11-10"))
                .andExpect(jsonPath("$.replaces.departureAirportCode").value("BRU"));
    }

    @Test
    void editRequestIgnoresTheReasonAndUsesTheDelays() throws Exception {
        replan(
                        """
                        {"delayType":"DEPARTURE","delayId":"%s","minutes":540,"description":"Longer fog"}
                        """
                                .formatted(DEPARTURE_DELAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replanning.minutes").value(540))
                .andExpect(jsonPath("$.replanning.reasonCode").value("71"))
                .andExpect(
                        jsonPath("$.replanning.newScheduledDeparture")
                                .value("2026-11-11T05:00:00Z"));
    }

    @Test
    void secondReplanIsAConflictNotPlanned() throws Exception {
        String body =
                """
                {"delayType":"DEPARTURE","minutes":140,"reasonCode":"71","description":"Leak"}
                """;
        replan(body).andExpect(status().isOk());

        replan(body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("NOT_PLANNED"));
    }

    @Test
    void totalWithinTwelveHoursIsAConflictReplanningNotNeeded() throws Exception {
        replan(
                        """
                        {"delayType":"ARRIVAL","minutes":120,"reasonCode":"71","description":"Holding"}
                        """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("REPLANNING_NOT_NEEDED"));
        assertThat(data.find(KEY).status()).isEqualTo(FlightStatus.PLANNED);
    }

    @Test
    void unknownReasonAndMissingTypeAreBadRequests() throws Exception {
        replan(
                        """
                        {"delayType":"DEPARTURE","minutes":140,"reasonCode":"99","description":"Leak"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown delay reason code 99"));
        replan(
                        """
                        {"minutes":140,"reasonCode":"71","description":"Leak"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("delay type must be DEPARTURE or ARRIVAL"));
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        mvc.perform(
                        post("/api/flights/HJ999/2026-11-10/BRU/replanning")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"delayType":"DEPARTURE","minutes":140,"reasonCode":"71","description":"Leak"}
                                        """))
                .andExpect(status().isNotFound());
    }

    private ResultActions replan(String body) throws Exception {
        return mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
