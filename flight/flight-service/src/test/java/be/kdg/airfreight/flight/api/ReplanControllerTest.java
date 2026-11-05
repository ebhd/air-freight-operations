package be.kdg.airfreight.flight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.flight.api.flight.FlightController;
import be.kdg.airfreight.flight.application.FlightActualTimeService;
import be.kdg.airfreight.flight.application.FlightDelayService;
import be.kdg.airfreight.flight.application.FlightQueryService;
import be.kdg.airfreight.flight.application.FlightReplanningService;
import be.kdg.airfreight.flight.domain.delayreason.DelayReason;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// the replanning endpoint with the plan example: HJ500 with 400 + 200 minutes of delay
class ReplanControllerTest {
    private static final FlightKey KEY = new FlightKey("HJ500", LocalDate.of(2026, 11, 10), "BRU");
    private static final String PATH = "/api/flights/HJ500/2026-11-10/BRU/replanning";
    private static final UUID DEPARTURE_DELAY = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-10T14:03:12Z");

    private final Map<FlightKey, Flight> flights = new HashMap<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        flights.put(
                KEY,
                Flight.restore(
                        KEY,
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-11-10T20:00:00Z"),
                        Instant.parse("2026-11-10T22:00:00Z"),
                        FlightStatus.PLANNED,
                        List.of(Delay.restore(DEPARTURE_DELAY, 400, "71", "Fog")),
                        List.of(Delay.restore(UUID.randomUUID(), 200, "71", "Holding"))));
        FlightRepository flightRepository = new InMemoryFlights();
        DelayReasonRepository reasons = new OneReason();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FlightReplanningService replanningService =
                new FlightReplanningService(flightRepository, reasons, event -> {}, clock);
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new FlightController(
                                        new FlightQueryService(flightRepository),
                                        new FlightDelayService(
                                                flightRepository,
                                                reasons,
                                                replanningService,
                                                event -> {},
                                                clock),
                                        mock(FlightActualTimeService.class),
                                        replanningService))
                        .setControllerAdvice(new ErrorHandling())
                        .build();
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
        assertThat(flights.get(KEY).status()).isEqualTo(FlightStatus.REPLANNED);
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
        flights.put(newKey, replacement);

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
        assertThat(flights.get(KEY).status()).isEqualTo(FlightStatus.PLANNED);
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
        public List<Flight> findByScheduledDepartureDate(LocalDate date) {
            return List.of();
        }

        @Override
        public List<Flight> findScheduledBefore(Instant end) {
            return List.of();
        }

        @Override
        public Flight save(Flight flight) {
            flights.put(flight.key(), flight);
            return flight;
        }

        @Override
        public List<FlightKey> findKeysUpTo(LocalDate lastDate) {
            return List.of();
        }

        @Override
        public void delete(FlightKey key) {}

        @Override
        public Optional<Flight> findReplacedBy(FlightKey newKey, Instant newScheduledDeparture) {
            return Optional.empty();
        }
    }

    private static final class OneReason implements DelayReasonRepository {
        @Override
        public Optional<DelayReason> findByCode(String code) {
            return code.equals("71")
                    ? Optional.of(new DelayReason("71", "Weather at departure station"))
                    : Optional.empty();
        }

        @Override
        public List<DelayReason> findAll() {
            return List.of(new DelayReason("71", "Weather at departure station"));
        }
    }
}
