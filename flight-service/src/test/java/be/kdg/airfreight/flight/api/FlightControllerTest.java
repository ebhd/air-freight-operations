package be.kdg.airfreight.flight.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.flight.application.FlightQueryService;
import be.kdg.airfreight.flight.domain.Flight;
import be.kdg.airfreight.flight.domain.FlightKey;
import be.kdg.airfreight.flight.domain.FlightRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FlightControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Flight flight =
                Flight.create(
                        new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU"),
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-10-19T08:00:00Z"),
                        Instant.parse("2026-10-19T10:00:00Z"));
        // one flight in memory no database
        FlightRepository flights =
                new FlightRepository() {
                    @Override
                    public Optional<Flight> findById(FlightKey key) {
                        return flight.key().equals(key) ? Optional.of(flight) : Optional.empty();
                    }

                    @Override
                    public Flight save(Flight saved) {
                        return saved;
                    }
                };
        // security is the same filter chain as /api/status which StatusSecurityTest covers
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new FlightController(new FlightQueryService(flights)))
                        .build();
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
                .andExpect(jsonPath("$.scheduledDeparture").value("2026-10-19T08:00:00Z"));
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
