package be.kdg.airfreight.cargo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.application.CargoPlanQueryService;
import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.CargoPlan;
import be.kdg.airfreight.cargo.domain.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.FlightRepository;
import be.kdg.airfreight.cargo.domain.FlightStatus;
import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.UldBooking;
import be.kdg.airfreight.cargo.domain.UldCode;
import be.kdg.airfreight.cargo.domain.Weight;
import be.kdg.airfreight.cargo.domain.WeightUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CargoPlanControllerTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-19/BRU";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));

    private final FlightRepository flights = mock(FlightRepository.class);
    private final CargoPlanRepository cargoPlans = mock(CargoPlanRepository.class);

    // security is the same filter chain as every api call which CargoPlanSecurityTest covers
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(
                            new CargoPlanController(new CargoPlanQueryService(flights, cargoPlans)))
                    .setControllerAdvice(new CargoApiExceptionHandler())
                    .build();

    @Test
    void returnsFlightInfoStatusAndBookings() throws Exception {
        givenFlight(FlightStatus.PLANNED, booking("AKE12345KL", "1250.5", WeightUnit.KG, "AMS"));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flight.flightNumber").value("HJ221"))
                .andExpect(jsonPath("$.flight.scheduledDepartureDate").value("2026-10-19"))
                .andExpect(jsonPath("$.flight.departureAirportCode").value("BRU"))
                .andExpect(jsonPath("$.flight.arrivalAirportCode").value("AMS"))
                .andExpect(jsonPath("$.flight.status").value("PLANNED"))
                .andExpect(jsonPath("$.flight.scheduledDeparture").value("2026-10-19T08:00:00Z"))
                .andExpect(jsonPath("$.flight.scheduledArrival").value("2026-10-19T10:00:00Z"))
                .andExpect(jsonPath("$.bookings", hasSize(1)))
                .andExpect(jsonPath("$.bookings[0].uldCode").value("AKE12345KL"))
                .andExpect(jsonPath("$.bookings[0].weight.value").value(1250.5))
                .andExpect(jsonPath("$.bookings[0].weight.unit").value("KG"))
                .andExpect(jsonPath("$.bookings[0].destination").value("AMS"))
                .andExpect(jsonPath("$.bookings[0].position").value(nullValue()));
    }

    @Test
    void sortsBookingsByUldCode() throws Exception {
        givenFlight(
                FlightStatus.PLANNED,
                booking("PMC00001KL", "1000", WeightUnit.LB, "ATH"),
                booking("AKE12345KL", "1250.5", WeightUnit.KG, "AMS"));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[0].uldCode").value("AKE12345KL"))
                .andExpect(jsonPath("$.bookings[1].uldCode").value("PMC00001KL"));
    }

    // jsonPath would also read 1E+3 as 1000 so check the raw body
    @Test
    void writesWeightAsPlainNumber() throws Exception {
        givenFlight(FlightStatus.PLANNED, booking("PMC00001KL", "1000", WeightUnit.KG, "ATH"));

        String body = mvc.perform(get(URL)).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"value\":1000,").doesNotContain("E+");
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.empty());

        mvc.perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Unknown flight HJ221/2026-10-19/BRU"));
    }

    @Test
    void flightOperationsDownIsServiceUnavailable() throws Exception {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        mvc.perform(get(URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(
                        jsonPath("$.detail")
                                .value("Flight Operations is unavailable, try again later"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/cargo-plans/HJ-221/2026-10-19/BRU",
                "/api/cargo-plans/HJ221/2026-10-19/BR",
                "/api/cargo-plans/HJ221/2026-13-01/BRU"
            })
    void invalidFlightKeyIsBadRequest(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isBadRequest());

        verifyNoInteractions(flights, cargoPlans);
    }

    @Test
    void departedFlightIsStillReturned() throws Exception {
        givenFlight(FlightStatus.DEPARTED, booking("AKE12345KL", "1250.5", WeightUnit.KG, "AMS"));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flight.status").value("DEPARTED"))
                .andExpect(jsonPath("$.bookings", hasSize(1)));
    }

    // bad data from flight is our bug and must not look like a bad request
    @Test
    void invalidValueFromFlightIsNotBadRequest() {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new InvalidCargoValueException("Airport code must be three letters: X"));

        assertThatThrownBy(() -> mvc.perform(get(URL)))
                .hasRootCauseInstanceOf(InvalidCargoValueException.class);
    }

    private void givenFlight(FlightStatus status, UldBooking... bookings) {
        when(flights.findByFlightKey(KEY))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        KEY,
                                        new AirportCode("AMS"),
                                        status,
                                        Instant.parse("2026-10-19T08:00:00Z"),
                                        Instant.parse("2026-10-19T10:00:00Z"),
                                        Instant.parse("2026-10-19T08:00:00Z"))));
        when(cargoPlans.findByFlightKey(KEY))
                .thenReturn(Optional.of(CargoPlan.restore(KEY, List.of(bookings))));
    }

    private static UldBooking booking(
            String uldCode, String weight, WeightUnit unit, String destination) {
        return new UldBooking(
                new UldCode(uldCode),
                new Weight(new BigDecimal(weight), unit),
                new AirportCode(destination));
    }
}
