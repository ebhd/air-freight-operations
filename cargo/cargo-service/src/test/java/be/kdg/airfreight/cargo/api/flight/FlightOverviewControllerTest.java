package be.kdg.airfreight.cargo.api.flight;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.support.PostgresTestConfiguration;
import be.kdg.airfreight.cargo.support.TestHelper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
@ActiveProfiles({"test", "postgres"})
@Import({PostgresTestConfiguration.class, TestHelper.class})
@WithMockUser(roles = "load-planner")
class FlightOverviewControllerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);

    @MockitoBean private FlightRepository flights;
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
    void listsTheFlightsOfTheDay() throws Exception {
        when(flights.findByScheduledDepartureDate(DAY))
                .thenReturn(List.of(flight("HJ221", FlightStatus.PLANNED)));
        mvc.perform(get("/api/flights").param("date", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightNumber").value("HJ221"))
                .andExpect(jsonPath("$[0].scheduledDepartureDate").value("2026-10-19"))
                .andExpect(jsonPath("$[0].departureAirportCode").value("BRU"))
                .andExpect(jsonPath("$[0].arrivalAirportCode").value("AMS"))
                .andExpect(jsonPath("$[0].status").value("PLANNED"))
                .andExpect(jsonPath("$[0].scheduledDeparture").value("2026-10-19T08:00:00Z"))
                .andExpect(jsonPath("$[0].scheduledArrival").value("2026-10-19T10:00:00Z"));
    }

    @Test
    void hidesDepartedFlightsUnlessIncluded() throws Exception {
        when(flights.findByScheduledDepartureDate(DAY))
                .thenReturn(
                        List.of(
                                flight("HJ221", FlightStatus.PLANNED),
                                flight("HJ300", FlightStatus.DEPARTED)));

        mvc.perform(get("/api/flights").param("date", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].flightNumber").value("HJ221"));
        mvc.perform(
                        get("/api/flights")
                                .param("date", "2026-10-19")
                                .param("includeDeparted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].flightNumber").value("HJ300"))
                .andExpect(jsonPath("$[1].status").value("DEPARTED"));
    }

    @Test
    void missingDateIsBadRequest() throws Exception {
        mvc.perform(get("/api/flights")).andExpect(status().isBadRequest());
    }

    @Test
    void invalidDateIsBadRequest() throws Exception {
        mvc.perform(get("/api/flights").param("date", "2026-13-40"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidIncludeDepartedIsBadRequest() throws Exception {
        mvc.perform(
                        get("/api/flights")
                                .param("date", "2026-10-19")
                                .param("includeDeparted", "maybe"))
                .andExpect(status().isBadRequest());
    }

    private static FlightInformation flight(String flightNumber, FlightStatus status) {
        return new FlightInformation(
                new FlightKey(flightNumber, DAY, new AirportCode("BRU")),
                new AirportCode("AMS"),
                status,
                Instant.parse("2026-10-19T08:00:00Z"),
                Instant.parse("2026-10-19T10:00:00Z"),
                Instant.parse("2026-10-19T08:00:00Z"));
    }
}
