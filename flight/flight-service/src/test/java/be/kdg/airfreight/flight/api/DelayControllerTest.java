package be.kdg.airfreight.flight.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.flight.api.delayreason.DelayReasonController;
import be.kdg.airfreight.flight.api.flight.FlightController;
import be.kdg.airfreight.flight.application.DelayReasonQueryService;
import be.kdg.airfreight.flight.application.FlightDelayService;
import be.kdg.airfreight.flight.application.FlightQueryService;
import be.kdg.airfreight.flight.domain.delayreason.DelayReason;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.DelayType;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import jakarta.servlet.ServletException;
import java.time.Instant;
import java.time.LocalDate;
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

class DelayControllerTest {
    private static final FlightKey PLANNED =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final FlightKey DEPARTED =
            new FlightKey("HJ222", LocalDate.of(2026, 10, 19), "BRU");
    private static final UUID DEPARTURE_DELAY = UUID.randomUUID();

    private final Map<FlightKey, Flight> flights = new HashMap<>();
    private boolean reasonTableBroken;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        flights.put(
                PLANNED,
                Flight.create(
                        PLANNED,
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-10-19T08:00:00Z"),
                        Instant.parse("2026-10-19T10:00:00Z")));
        flights.put(
                DEPARTED,
                Flight.restore(
                        DEPARTED,
                        "AMS",
                        "SXBAB",
                        Instant.parse("2026-10-19T09:00:00Z"),
                        Instant.parse("2026-10-19T11:00:00Z"),
                        FlightStatus.DEPARTED,
                        List.of(
                                Delay.restore(
                                        DEPARTURE_DELAY,
                                        DelayType.DEPARTURE,
                                        20,
                                        "71",
                                        "Fog at departure"))));
        FlightRepository flightRepository = new InMemoryFlights();
        DelayReasonRepository reasons = new OneReason();
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new FlightController(
                                        new FlightQueryService(flightRepository),
                                        new FlightDelayService(
                                                flightRepository, reasons, event -> {})),
                                new DelayReasonController(new DelayReasonQueryService(reasons)))
                        .setControllerAdvice(new ErrorHandling())
                        .build();
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
        reasonTableBroken = true;

        assertThatThrownBy(() -> mvc.perform(get("/api/delay-reasons")))
                .isInstanceOf(ServletException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private ResultActions addDelay(int minutes, String reasonCode, String description)
            throws Exception {
        return mvc.perform(
                post("/api/flights/HJ221/2026-10-19/BRU/delays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"type":"DEPARTURE","minutes":%d,"reasonCode":"%s","description":"%s"}
                                """
                                        .formatted(minutes, reasonCode, description)));
    }

    private ResultActions updateDelay(FlightKey key, UUID delayId, int minutes, String description)
            throws Exception {
        return mvc.perform(
                patch(
                                "/api/flights/{number}/{date}/{airport}/delays/{id}",
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
        public Flight save(Flight flight) {
            flights.put(flight.key(), flight);
            return flight;
        }
    }

    // only reason 71 exists and a broken table throws a plain library exception
    private final class OneReason implements DelayReasonRepository {
        @Override
        public Optional<DelayReason> findByCode(String code) {
            return code.equals("71")
                    ? Optional.of(new DelayReason("71", "Weather at departure station"))
                    : Optional.empty();
        }

        @Override
        public List<DelayReason> findAll() {
            if (reasonTableBroken) {
                throw new IllegalArgumentException("broken reason table");
            }
            return List.of(new DelayReason("71", "Weather at departure station"));
        }
    }
}
