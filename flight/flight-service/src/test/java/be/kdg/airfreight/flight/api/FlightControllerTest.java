package be.kdg.airfreight.flight.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
@Import(TestHelper.class)
@WithMockUser(roles = "flight-planner")
class FlightControllerTest {
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
        Flight flight =
                Flight.create(
                        new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU"),
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-10-19T08:00:00Z"),
                        Instant.parse("2026-10-19T10:00:00Z"));
        data.save(flight);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void knownFlightReturnsItsData() throws Exception {
        mvc.perform(get("/api/flights/HJ221/2026-10-19/BRU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("HJ221"))
                .andExpect(jsonPath("$.scheduledDepartureDate").value("2026-10-19"))
                .andExpect(jsonPath("$.departureAirportCode").value("BRU"))
                .andExpect(jsonPath("$.arrivalAirportCode").value("AMS"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.scheduledDeparture").value("2026-10-19T08:00:00Z"))
                .andExpect(jsonPath("$.replanning").value(nullValue()))
                .andExpect(jsonPath("$.replaces").value(nullValue()));
    }

    @Test
    void dayOverviewListsFlightsOfThatDate() throws Exception {
        mvc.perform(get("/api/flights").param("date", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].flightNumber").value("HJ221"))
                .andExpect(jsonPath("$[0].scheduledDeparture").value("2026-10-19T08:00:00Z"));
    }

    @Test
    void dayWithoutFlightsIsAnEmptyList() throws Exception {
        mvc.perform(get("/api/flights").param("date", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dayOverviewNeedsADate() throws Exception {
        mvc.perform(get("/api/flights")).andExpect(status().isBadRequest());
    }

    @Test
    void lowerCaseAirportFindsTheSameFlight() throws Exception {
        mvc.perform(get("/api/flights/HJ221/2026-10-19/bru")).andExpect(status().isOk());
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        mvc.perform(get("/api/flights/HJ221/2026-10-20/BRU")).andExpect(status().isNotFound());
    }

    @Test
    void invalidAirportIsBadRequest() throws Exception {
        mvc.perform(get("/api/flights/HJ221/2026-10-19/BRUX"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value("departure airport code must be a three-letter IATA code"));
    }

    @Test
    void nonIsoDateIsBadRequest() throws Exception {
        // without the iso format spring would expect a locale date like 10/19/26
        mvc.perform(get("/api/flights/HJ221/19-10-2026/BRU")).andExpect(status().isBadRequest());
    }
}
