package be.kdg.airfreight.flight.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.flight.api.flight.FlightController;
import be.kdg.airfreight.flight.application.FlightActualTimeService;
import be.kdg.airfreight.flight.application.FlightDelayService;
import be.kdg.airfreight.flight.application.FlightQueryService;
import be.kdg.airfreight.flight.application.FlightReplanningService;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.flight.ActualTimePolicy;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// the real service and flight behind the controller, with a fixed clock, no database
class ActualTimeControllerTest {
    private static final String FLIGHT = "/api/flights/HJ221/2026-10-19/BRU";
    private static final FlightKey KEY = new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    private final Map<FlightKey, Flight> flights = new HashMap<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Flight flight =
                Flight.create(
                        KEY,
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-10-19T08:00:00Z"),
                        Instant.parse("2026-10-19T10:00:00Z"));
        flight.recordFinalizedLoad();
        flights.put(KEY, flight);
        FlightRepository repository = new InMemoryFlights();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new FlightController(
                                        new FlightQueryService(repository),
                                        new FlightDelayService(
                                                repository,
                                                mock(DelayReasonRepository.class),
                                                mock(FlightReplanningService.class),
                                                mock(ApplicationEventPublisher.class),
                                                clock),
                                        new FlightActualTimeService(
                                                repository,
                                                mock(ApplicationEventPublisher.class),
                                                clock,
                                                new ActualTimePolicy(Duration.ofMinutes(5))),
                                        mock(FlightReplanningService.class)))
                        .setControllerAdvice(new ErrorHandling())
                        .build();
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

    private final class InMemoryFlights implements FlightRepository {
        @Override
        public Optional<Flight> findById(FlightKey key) {
            return Optional.ofNullable(flights.get(key));
        }

        @Override
        public Optional<Flight> findByIdForUpdate(FlightKey key) {
            return findById(key);
        }

        @Override
        public Optional<Flight> findReplacedBy(FlightKey newKey, Instant newScheduledDeparture) {
            return Optional.empty();
        }

        @Override
        public List<Flight> findByScheduledDepartureDate(LocalDate date) {
            return List.of();
        }

        @Override
        public Flight save(Flight flight) {
            flights.put(flight.key(), flight);
            return flight;
        }
    }
}
