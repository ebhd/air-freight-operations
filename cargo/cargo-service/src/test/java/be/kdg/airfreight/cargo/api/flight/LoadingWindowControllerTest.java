package be.kdg.airfreight.cargo.api.flight;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoadingWindowControllerTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-22/BRU/loading-window";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    // 10:00 brussels so the default window is 2026-10-21T20:00Z to 2026-10-22T07:40Z
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");

    private final FlightRepository flights = mock(FlightRepository.class);
    private final CargoPlanRepository cargoPlans = mock(CargoPlanRepository.class);

    @Test
    void returnsWindowAndRefusal() throws Exception {
        givenFlight(FlightStatus.PLANNED);

        mvc(Instant.parse("2026-10-21T18:00:00Z"))
                .perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("HJ221"))
                .andExpect(jsonPath("$.scheduledDepartureDate").value("2026-10-22"))
                .andExpect(jsonPath("$.departureAirportCode").value("BRU"))
                .andExpect(jsonPath("$.flightStatus").value("PLANNED"))
                .andExpect(jsonPath("$.opensAt").value("2026-10-21T20:00:00Z"))
                .andExpect(jsonPath("$.closesAt").value("2026-10-22T07:40:00Z"))
                .andExpect(jsonPath("$.finalized").value(false))
                .andExpect(jsonPath("$.checkedAt").value("2026-10-21T18:00:00Z"))
                .andExpect(jsonPath("$.loadingAllowed").value(false))
                .andExpect(jsonPath("$.refusal").value("WINDOW_NOT_OPEN_YET"));
    }

    @Test
    void openWindowAllowsLoading() throws Exception {
        givenFlight(FlightStatus.PLANNED);

        mvc(Instant.parse("2026-10-22T06:00:00Z"))
                .perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loadingAllowed").value(true))
                .andExpect(jsonPath("$.refusal").value(nullValue()));
    }

    // reading stays possible after departure
    @Test
    void departedFlightIsReadNotRefused() throws Exception {
        givenFlight(FlightStatus.DEPARTED);

        mvc(Instant.parse("2026-10-22T09:00:00Z"))
                .perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightStatus").value("DEPARTED"))
                .andExpect(jsonPath("$.refusal").value("FLIGHT_NOT_PLANNED"));
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        mvc(STD).perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Unknown flight HJ221/2026-10-22/BRU"));
    }

    @Test
    void flightOperationsDownIsServiceUnavailable() throws Exception {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        mvc(STD).perform(get(URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(
                        jsonPath("$.detail")
                                .value("Flight Operations is unavailable, try again later"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/cargo-plans/HJ-221/2026-10-22/BRU/loading-window",
                "/api/cargo-plans/HJ221/2026-10-22/BR/loading-window",
                "/api/cargo-plans/HJ221/2026-13-01/BRU/loading-window"
            })
    void invalidFlightKeyIsBadRequest(String url) throws Exception {
        mvc(STD).perform(get(url)).andExpect(status().isBadRequest());

        verifyNoInteractions(flights, cargoPlans);
    }

    private void givenFlight(FlightStatus status) {
        when(flights.findByFlightKey(KEY))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        KEY,
                                        new AirportCode("AMS"),
                                        status,
                                        STD,
                                        Instant.parse("2026-10-22T10:00:00Z"),
                                        STD)));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.empty());
    }

    // security is the same filter chain as every api call which LoadingWindowSecurityTest covers
    private MockMvc mvc(Instant now) {
        LoadingWindowService service =
                new LoadingWindowService(
                        flights,
                        cargoPlans,
                        new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20)),
                        Clock.fixed(now, ZoneOffset.UTC));
        return MockMvcBuilders.standaloneSetup(new LoadingWindowController(service))
                .setControllerAdvice(new CargoApiExceptionHandler())
                .build();
    }
}
