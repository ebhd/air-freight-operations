package be.kdg.airfreight.cargo.api.cargoplan;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.api.CargoPlanSecurityConfiguration;
import be.kdg.airfreight.cargo.application.cargoplan.UldPositionService;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = CargoPlanSecurityConfiguration.class)
class CargoPlanSecurityTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-19/BRU";
    private static final String POSITION_URL = URL + "/bookings/AKE12345KL/position";
    private static final String BODY = "{\"position\":\"RL\"}";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));

    @Autowired private WebApplicationContext context;
    @Autowired private FlightRepository flights;
    @Autowired private UldPositionService uldPositionService;
    @Autowired private JwtDecoder jwtDecoder;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        clearInvocations(uldPositionService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(flights.findByFlightKey(KEY))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        KEY,
                                        new AirportCode("AMS"),
                                        FlightStatus.PLANNED,
                                        Instant.parse("2026-10-19T08:00:00Z"),
                                        Instant.parse("2026-10-19T10:00:00Z"),
                                        Instant.parse("2026-10-19T08:00:00Z"),
                                        Instant.parse("2026-10-19T10:00:00Z"))));
    }

    @Test
    void cargoPlanNeedsToken() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void loadPlannerReadsTheCargoPlan() throws Exception {
        mvc.perform(
                        get(URL).with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_load-planner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flight.flightNumber").value("HJ221"))
                .andExpect(jsonPath("$.flight.status").value("PLANNED"))
                .andExpect(jsonPath("$.bookings").isEmpty());
    }

    // no csrf check so a write without a token is refused with 401 like a read
    @Test
    void positionChangesWithoutTokenAreRefused() throws Exception {
        mvc.perform(put(POSITION_URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(POSITION_URL)).andExpect(status().isUnauthorized());

        verifyNoInteractions(uldPositionService);
    }

    // a real bearer header like the page sends so the token goes through the jwt decoder
    @Test
    void loadPlannerReachesThePositionEndpoints() throws Exception {
        when(jwtDecoder.decode("token"))
                .thenReturn(
                        Jwt.withTokenValue("token")
                                .header("alg", "none")
                                .subject("load-planner")
                                .claim("preferred_username", "load-planner")
                                .claim("realm_access", Map.of("roles", List.of("load-planner")))
                                .build());

        mvc.perform(
                        put(POSITION_URL)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isNoContent());
        mvc.perform(delete(POSITION_URL).header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isNoContent());

        verify(uldPositionService)
                .assignPosition(KEY, new UldCode("AKE12345KL"), LoadingPosition.RL);
        verify(uldPositionService).clearPosition(KEY, new UldCode("AKE12345KL"));
    }

    @Test
    void flightPlannerReadsButCannotChangePositions() throws Exception {
        var flightPlanner = jwt().authorities(new SimpleGrantedAuthority("ROLE_flight-planner"));
        mvc.perform(get(URL).with(flightPlanner)).andExpect(status().isOk());
        mvc.perform(
                        put(POSITION_URL)
                                .with(flightPlanner)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(delete(POSITION_URL).with(flightPlanner)).andExpect(status().isForbidden());
        verifyNoInteractions(uldPositionService);
    }

    @Test
    void authenticatedUserWithoutPlannerRoleCannotReadOrChangeCargo() throws Exception {
        mvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
        mvc.perform(
                        put(POSITION_URL)
                                .with(jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(delete(POSITION_URL).with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(uldPositionService);
    }

    @Test
    void loadingPositionsNeedTokenAndAreListed() throws Exception {
        mvc.perform(get("/api/loading-positions")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/loading-positions")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_load-planner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(34)))
                .andExpect(jsonPath("$[0].code").value("A1"))
                .andExpect(jsonPath("$[0].row").value("A"))
                .andExpect(jsonPath("$[0].side").value("NONE"));
    }
}
