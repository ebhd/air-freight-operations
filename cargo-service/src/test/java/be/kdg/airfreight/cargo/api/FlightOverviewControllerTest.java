package be.kdg.airfreight.cargo.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.application.FlightOverviewService;
import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.FlightRepository;
import be.kdg.airfreight.cargo.domain.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FlightOverviewControllerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);

    @Test
    void listsTheFlightsOfTheDay() throws Exception {
        FlightInformation flight =
                new FlightInformation(
                        new FlightKey("HJ221", DAY, new AirportCode("BRU")),
                        new AirportCode("AMS"),
                        FlightStatus.PLANNED,
                        Instant.parse("2026-10-19T08:00:00Z"),
                        Instant.parse("2026-10-19T10:00:00Z"));

        mvc(date -> List.of(flight))
                .perform(get("/api/flights").param("date", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightNumber").value("HJ221"))
                .andExpect(jsonPath("$[0].status").value("PLANNED"))
                .andExpect(jsonPath("$[0].scheduledDeparture").value("2026-10-19T08:00:00Z"));
    }

    @Test
    void flightOperationsDownIsServiceUnavailable() throws Exception {
        mvc(date -> {
                    throw new FlightLookupUnavailableException(date, null);
                })
                .perform(get("/api/flights").param("date", "2026-10-19"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(
                        jsonPath("$.detail")
                                .value("Flight Operations is unavailable, try again later"));
    }

    // security is the same filter chain as /api/status which StatusSecurityTest covers
    private static MockMvc mvc(Function<LocalDate, List<FlightInformation>> dayLookup) {
        FlightRepository flights =
                new FlightRepository() {
                    @Override
                    public Optional<FlightInformation> findByFlightKey(FlightKey flightKey) {
                        return Optional.empty();
                    }

                    @Override
                    public List<FlightInformation> findByScheduledDepartureDate(LocalDate date) {
                        return dayLookup.apply(date);
                    }
                };
        return MockMvcBuilders.standaloneSetup(
                        new FlightOverviewController(new FlightOverviewService(flights)))
                .build();
    }
}
