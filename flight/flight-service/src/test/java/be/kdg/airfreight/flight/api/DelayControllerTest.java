package be.kdg.airfreight.flight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import be.kdg.airfreight.flight.support.TestHelper;
import jakarta.servlet.ServletException;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
@Import(TestHelper.class)
@WithMockUser(roles = "flight-planner")
class DelayControllerTest {
    private static final FlightKey PLANNED =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final FlightKey DEPARTED =
            new FlightKey("HJ222", LocalDate.of(2026, 10, 19), "BRU");
    private static final UUID DEPARTURE_DELAY = UUID.randomUUID();
    private static final UUID ARRIVAL_DELAY = UUID.randomUUID();
    private static final String DEPARTED_PATH = "/api/flights/HJ222/2026-10-19/BRU";

    @MockitoSpyBean private DelayReasonRepository reasons;
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
        data.prepare(clock, rabbit, Instant.parse("2026-10-19T12:00:00Z"));
        data.save(
                Flight.create(
                        PLANNED,
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-10-19T08:00:00Z"),
                        Instant.parse("2026-10-19T10:00:00Z")));
        data.save(
                Flight.restore(
                        DEPARTED,
                        "AMS",
                        "SXBAB",
                        Instant.parse("2026-10-19T09:00:00Z"),
                        Instant.parse("2026-10-19T11:00:00Z"),
                        FlightStatus.DEPARTED,
                        List.of(Delay.restore(DEPARTURE_DELAY, 20, "71", "Fog at departure")),
                        List.of(Delay.restore(ARRIVAL_DELAY, 15, "71", "Holding"))));
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void zeroMinutesIsBadRequestWithTheReason() throws Exception {
        addDelay(0, "71", "Fog at departure")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("delay minutes must be between 1 and 720"));
    }

    @Test
    void moreThan720MinutesIsBadRequestWithTheReason() throws Exception {
        addDelay(721, "71", "Fog at departure")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("delay minutes must be between 1 and 720"));
    }

    @Test
    void blankDescriptionIsBadRequestWithTheReason() throws Exception {
        addDelay(20, "71", "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("delay description must not be blank"));
    }

    @Test
    void unknownReasonIsBadRequestWithTheReason() throws Exception {
        addDelay(20, "99", "Fog at departure")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown delay reason code 99"));
    }

    @Test
    void editWithBlankDescriptionIsBadRequestWithTheReason() throws Exception {
        updateDelay(PLANNED, UUID.randomUUID(), 20, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("delay description must not be blank"));
    }

    @Test
    void unknownDelayIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        updateDelay(PLANNED, unknown, 20, "Fog at departure")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Unknown delay " + unknown));
    }

    @Test
    void delayOverTheTotalOfTwelveHoursIsAConflictWithTheNumbers() throws Exception {
        addDelay(600, "71", "Fog at departure").andExpect(status().isOk());

        addDelay(140, "71", "Hydraulic leak")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("TOTAL_DELAY_EXCEEDED"))
                .andExpect(jsonPath("$.totalMinutes").value(740))
                .andExpect(jsonPath("$.maxTotalMinutes").value(720))
                .andExpect(jsonPath("$.replanningPossible").value(true))
                // the proposal: std 08:00 + 740 min of departure delay, sta 10:00 + 740 min
                .andExpect(jsonPath("$.newFlightNumber").value("HJ221A"))
                .andExpect(jsonPath("$.newScheduledDeparture").value("2026-10-19T20:20:00Z"))
                .andExpect(jsonPath("$.newScheduledArrival").value("2026-10-19T22:20:00Z"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "All delays together would be 740 minutes (12 h 20 min)."
                                                + " At most 720 minutes (12 h) is allowed. You can"
                                                + " replan this flight as HJ221A instead."));
        assertThat(data.find(PLANNED).totalDelayMinutes()).isEqualTo(600);
    }

    @Test
    void departureDelayOfADepartedFlightIsAConflict() throws Exception {
        updateDelay(DEPARTED, DEPARTURE_DELAY, 30, "Fog at departure")
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "departure delays can only be changed while the flight"
                                                + " is planned"));
    }

    // not translated by ErrorHandling so spring boot answers 500 instead of blaming the user
    @Test
    void otherIllegalArgumentExceptionIsNotTurnedIntoBadRequest() {
        doThrow(new IllegalArgumentException("broken reason table")).when(reasons).findAll();

        assertThatThrownBy(() -> mvc.perform(get("/api/delay-reasons")))
                .isInstanceOf(ServletException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arrivalDelayCanBeAddedAfterDeparture() throws Exception {
        mvc.perform(
                        post(DEPARTED_PATH + "/arrival-delays")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"minutes":10,"reasonCode":"71","description":"Holding"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivalDelays.length()").value(2))
                .andExpect(jsonPath("$.arrivalDelays[1].minutes").value(10))
                .andExpect(jsonPath("$.estimatedArrival").value("2026-10-19T11:45:00Z"));
    }

    @Test
    void arrivalDelayCanBeEditedAfterDeparture() throws Exception {
        mvc.perform(
                        patch(DEPARTED_PATH + "/arrival-delays/" + ARRIVAL_DELAY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"minutes":30,"description":"Longer holding"}
                        """))
                .andExpect(status().isNoContent());

        mvc.perform(get(DEPARTED_PATH))
                .andExpect(jsonPath("$.arrivalDelays[0].minutes").value(30))
                .andExpect(jsonPath("$.arrivalDelays[0].description").value("Longer holding"))
                .andExpect(jsonPath("$.estimatedArrival").value("2026-10-19T11:50:00Z"));
    }

    @Test
    void arrivalDelayCanBeDeletedAfterDeparture() throws Exception {
        mvc.perform(delete(DEPARTED_PATH + "/arrival-delays/" + ARRIVAL_DELAY))
                .andExpect(status().isNoContent());

        mvc.perform(get(DEPARTED_PATH))
                .andExpect(jsonPath("$.arrivalDelays").isEmpty())
                .andExpect(jsonPath("$.departureDelays[0].minutes").value(20))
                .andExpect(jsonPath("$.estimatedArrival").value("2026-10-19T11:20:00Z"));
    }

    @Test
    void departureDelayCannotBeAddedAfterDeparture() throws Exception {
        mvc.perform(
                        post(DEPARTED_PATH + "/departure-delays")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"minutes":10,"reasonCode":"71","description":"Fog"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void arrivalDelayChangesAfterLandingAreConflicts() throws Exception {
        Flight departed = data.find(DEPARTED);
        data.save(
                Flight.restore(
                        DEPARTED,
                        departed.arrivalAirportCode(),
                        departed.aircraftRegistration(),
                        departed.scheduledDeparture(),
                        departed.scheduledArrival(),
                        FlightStatus.LANDED,
                        departed.departureDelays(),
                        departed.arrivalDelays()));

        mvc.perform(
                        post(DEPARTED_PATH + "/arrival-delays")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"minutes":10,"reasonCode":"71","description":"Holding"}
                        """))
                .andExpect(status().isConflict());
        mvc.perform(
                        patch(DEPARTED_PATH + "/arrival-delays/" + ARRIVAL_DELAY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"minutes":30,"description":"Longer holding"}
                        """))
                .andExpect(status().isConflict());
        mvc.perform(delete(DEPARTED_PATH + "/arrival-delays/" + ARRIVAL_DELAY))
                .andExpect(status().isConflict());
    }

    @Test
    void departureDelayIdOnArrivalRouteIsNotFound() throws Exception {
        mvc.perform(
                        patch(DEPARTED_PATH + "/arrival-delays/" + DEPARTURE_DELAY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"minutes":30,"description":"Holding"}
                        """))
                .andExpect(status().isNotFound());
    }

    private ResultActions addDelay(int minutes, String reasonCode, String description)
            throws Exception {
        return mvc.perform(
                post("/api/flights/HJ221/2026-10-19/BRU/departure-delays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"minutes":%d,"reasonCode":"%s","description":"%s"}
                                """
                                        .formatted(minutes, reasonCode, description)));
    }

    private ResultActions updateDelay(FlightKey key, UUID delayId, int minutes, String description)
            throws Exception {
        return mvc.perform(
                patch(
                                "/api/flights/{number}/{date}/{airport}/departure-delays/{id}",
                                key.flightNumber(),
                                key.scheduledDepartureDate(),
                                key.departureAirportCode(),
                                delayId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"minutes":%d,"description":"%s"}
                                """
                                        .formatted(minutes, description)));
    }
}
