package be.kdg.airfreight.flight.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.flight.api.delayreason.DelayReasonController;
import be.kdg.airfreight.flight.api.flight.FlightController;
import be.kdg.airfreight.flight.application.DelayReasonQueryService;
import be.kdg.airfreight.flight.application.FlightActualTimeService;
import be.kdg.airfreight.flight.application.FlightDelayService;
import be.kdg.airfreight.flight.application.FlightQueryService;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

// the real security rules in front of the real controllers with mocked services
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = FlightSecurityTest.Config.class)
class FlightSecurityTest {
    private static final String FLIGHT = "/api/flights/HJ221/2026-10-19/BRU";
    private static final String DELAY = FLIGHT + "/departure-delays/" + UUID.randomUUID();
    private static final String ADD_BODY =
            """
            {"minutes":20,"reasonCode":"71","description":"Fog"}
            """;
    private static final String EDIT_BODY =
            """
            {"minutes":30,"description":"Fog"}
            """;
    private static final String ACTUAL_TIME_BODY =
            """
            {"actualDeparture":"2026-10-19T08:03:00Z"}
            """;

    @Autowired private WebApplicationContext context;
    @Autowired private FlightQueryService flightQueryService;
    @Autowired private FlightDelayService flightDelayService;
    @Autowired private DelayReasonQueryService delayReasonQueryService;
    @Autowired private FlightActualTimeService flightActualTimeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(
                flightQueryService,
                flightDelayService,
                delayReasonQueryService,
                flightActualTimeService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Flight flight =
                Flight.create(
                        new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU"),
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-10-19T08:00:00Z"),
                        Instant.parse("2026-10-19T10:00:00Z"));
        when(flightQueryService.findFlight(any())).thenReturn(Optional.of(flight));
        when(flightQueryService.findFlightsOn(any())).thenReturn(List.of(flight));
        when(flightActualTimeService.recordDeparture(any())).thenReturn(flight);
        when(delayReasonQueryService.findAll()).thenReturn(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"departure-delays", "arrival-delays"})
    void loadPlannerCannotAddADelay(String category) throws Exception {
        mvc.perform(
                        post(FLIGHT + "/" + category)
                                .with(planner("load-planner"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ADD_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(flightDelayService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"departure-delays", "arrival-delays"})
    void loadPlannerCannotEditADelay(String category) throws Exception {
        mvc.perform(
                        patch(FLIGHT + "/" + category + "/" + UUID.randomUUID())
                                .with(planner("load-planner"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(EDIT_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(flightDelayService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"departure-delays", "arrival-delays"})
    void loadPlannerCannotDeleteADelay(String category) throws Exception {
        mvc.perform(
                        delete(FLIGHT + "/" + category + "/" + UUID.randomUUID())
                                .with(planner("load-planner")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(flightDelayService);
    }

    // shows the refusal above comes from the role and not from a broken request
    @Test
    void flightPlannerCanDeleteADelay() throws Exception {
        mvc.perform(delete(DELAY).with(planner("flight-planner")))
                .andExpect(status().isNoContent());
        verify(flightDelayService).removeDepartureDelay(any(), any());
    }

    @Test
    void flightPlannerCanDeleteAnArrivalDelay() throws Exception {
        mvc.perform(
                        delete(FLIGHT + "/arrival-delays/" + UUID.randomUUID())
                                .with(planner("flight-planner")))
                .andExpect(status().isNoContent());
        verify(flightDelayService).removeArrivalDelay(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"departure", "arrival"})
    void loadPlannerCannotRegisterAnActualTime(String action) throws Exception {
        mvc.perform(
                        post(FLIGHT + "/" + action)
                                .with(planner("load-planner"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ACTUAL_TIME_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(flightActualTimeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"departure", "arrival"})
    void anonymousCannotRegisterAnActualTime(String action) throws Exception {
        mvc.perform(
                        post(FLIGHT + "/" + action)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ACTUAL_TIME_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(flightActualTimeService);
    }

    @Test
    void flightPlannerCanRegisterTheDeparture() throws Exception {
        mvc.perform(
                        post(FLIGHT + "/departure")
                                .with(planner("flight-planner"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ACTUAL_TIME_BODY))
                .andExpect(status().isOk());
        verify(flightActualTimeService).recordDeparture(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"flight-planner", "load-planner"})
    void bothPlannerRolesCanReadFlightsAndDelayReasons(String role) throws Exception {
        mvc.perform(get(FLIGHT).with(planner(role))).andExpect(status().isOk());
        mvc.perform(get("/api/flights").param("date", "2026-10-19").with(planner(role)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/delay-reasons").with(planner(role))).andExpect(status().isOk());
    }

    private static RequestPostProcessor planner(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @ComponentScan("be.kdg.airfreight.flight.infrastructure.security")
    @Import({FlightController.class, DelayReasonController.class})
    static class Config {
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        FlightQueryService flightQueryService() {
            return mock(FlightQueryService.class);
        }

        @Bean
        FlightDelayService flightDelayService() {
            return mock(FlightDelayService.class);
        }

        @Bean
        DelayReasonQueryService delayReasonQueryService() {
            return mock(DelayReasonQueryService.class);
        }

        @Bean
        FlightActualTimeService flightActualTimeService() {
            return mock(FlightActualTimeService.class);
        }
    }
}
