package be.kdg.airfreight.cargo.api.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.application.cargoplan.CargoPlanQueryService;
import be.kdg.airfreight.cargo.application.cargoplan.UldPositionService;
import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanState;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionOperations;

class CargoPlanControllerTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-19/BRU";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));

    private static final String POSITION_URL = URL + "/bookings/AKE12345KL/position";
    private static final LoadingWindowPolicy POLICY =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    // inside the window 2026-10-18T20:00Z to 2026-10-19T07:40Z of the flight below
    private static final Instant INSIDE = Instant.parse("2026-10-19T06:00:00Z");

    private final FlightRepository flights = mock(FlightRepository.class);
    private final CargoPlanRepository cargoPlans = mock(CargoPlanRepository.class);

    // security is the same filter chain as every api call which CargoPlanSecurityTest covers
    private final MockMvc mvc = mvcAt(INSIDE);

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
    void draftPlanHasItsStateAndNoMovedTo() throws Exception {
        givenFlight(FlightStatus.PLANNED, booking("AKE12345KL", "1250.5", WeightUnit.KG, "AMS"));

        mvc.perform(get(URL))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.finalizedAt").value(nullValue()))
                .andExpect(jsonPath("$.movedTo").value(nullValue()));
    }

    // the old plan of a replanning tells the page where the cargo went
    @Test
    void replannedPlanSaysWhereTheCargoMovedTo() throws Exception {
        givenFlight(FlightStatus.REPLANNED);
        when(cargoPlans.findByFlightKey(KEY))
                .thenReturn(
                        Optional.of(
                                CargoPlan.restore(
                                        KEY,
                                        CargoPlanState.REPLANNED,
                                        null,
                                        new FlightKey(
                                                "HJ221A",
                                                LocalDate.of(2026, 10, 20),
                                                new AirportCode("BRU")),
                                        List.of())));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REPLANNED"))
                .andExpect(jsonPath("$.bookings", hasSize(0)))
                .andExpect(jsonPath("$.movedTo.flightNumber").value("HJ221A"))
                .andExpect(jsonPath("$.movedTo.scheduledDepartureDate").value("2026-10-20"))
                .andExpect(jsonPath("$.movedTo.departureAirportCode").value("BRU"));
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

    @Test
    void showsThePositionOfAPositionedUld() throws Exception {
        givenFlight(
                FlightStatus.PLANNED,
                booking("AKE12345KL", "1250.5", WeightUnit.KG, "AMS", LoadingPosition.RL));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[0].position").value("RL"));
    }

    @Test
    void assignAndClearReturnNoContent() throws Exception {
        CargoPlan cargoPlan =
                givenFlight(
                        FlightStatus.PLANNED,
                        booking("AKE12345KL", "1250.5", WeightUnit.KG, "AMS"));

        mvc.perform(putPosition("{\"position\":\"rl\"}")).andExpect(status().isNoContent());
        assertThat(positionOf(cargoPlan)).isEqualTo(LoadingPosition.RL);

        mvc.perform(delete(POSITION_URL)).andExpect(status().isNoContent());
        assertThat(positionOf(cargoPlan)).isNull();
        verify(cargoPlans, times(2)).save(cargoPlan);
    }

    static Stream<Arguments> invalidPositionRequests() {
        String valid = "{\"position\":\"RL\"}";
        String tooLongUldCode = "A".repeat(33);
        return Stream.of(
                arguments(
                        "unknown position",
                        putPosition("{\"position\":\"ZZ\"}"),
                        "Unknown loading position ZZ, valid positions are A1, A2, B1, CL, CR, DL,"
                                + " DR, EL, ER, FL, FR, GL, GR, HL, HR, JL, JR, KL, KR, LL, LR, ML,"
                                + " MR, PL, PR, RL, RR, SL, SR, TL, TR, UL, UR, X"),
                arguments("missing position", putPosition("{}"), "Loading position is required"),
                arguments(
                        "uld code too long on put",
                        put(URL + "/bookings/" + tooLongUldCode + "/position")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(valid),
                        "ULD code must be 1 to 32 characters: " + tooLongUldCode),
                arguments(
                        "uld code too long on delete",
                        delete(URL + "/bookings/" + tooLongUldCode + "/position"),
                        "ULD code must be 1 to 32 characters: " + tooLongUldCode),
                arguments(
                        "bad flight number",
                        put("/api/cargo-plans/HJ-221/2026-10-19/BRU/bookings/AKE12345KL/position")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(valid),
                        "Flight number must be 1 to 32 letters or digits: HJ-221"),
                arguments(
                        "bad airport code",
                        delete("/api/cargo-plans/HJ221/2026-10-19/BR/bookings/AKE12345KL/position"),
                        "Airport code must be three letters: BR"),
                // spring itself refuses these before our code runs
                arguments(
                        "bad date",
                        put("/api/cargo-plans/HJ221/2026-13-01/BRU/bookings/AKE12345KL/position")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(valid),
                        null),
                arguments("unreadable body", putPosition("RL"), null),
                arguments(
                        "no body",
                        put(POSITION_URL).contentType(MediaType.APPLICATION_JSON),
                        null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPositionRequests")
    void invalidInputIsBadRequest(
            String description, MockHttpServletRequestBuilder request, String reason)
            throws Exception {
        ResultActions result = mvc.perform(request).andExpect(status().isBadRequest());
        if (reason != null) {
            result.andExpect(status().reason(reason));
        }

        verifyNoInteractions(flights, cargoPlans);
    }

    @Test
    void uldNotBookedIsNotFound() throws Exception {
        givenFlight(FlightStatus.PLANNED, booking("PMC00001KL", "1000", WeightUnit.KG, "ATH"));

        mvc.perform(putPosition("{\"position\":\"RL\"}"))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "ULD AKE12345KL is not booked on flight HJ221/2026-10-19/BRU"));
        mvc.perform(delete(POSITION_URL)).andExpect(status().isNotFound());
        verify(cargoPlans, never()).save(any());
    }

    @Test
    void flightWithoutPlanIsNotFound() throws Exception {
        givenFlight(FlightStatus.PLANNED);
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.empty());

        mvc.perform(putPosition("{\"position\":\"RL\"}")).andExpect(status().isNotFound());
    }

    @Test
    void takenPositionIsConflict() throws Exception {
        givenFlight(
                FlightStatus.PLANNED,
                booking("AKE12345KL", "1250.5", WeightUnit.KG, "AMS"),
                booking("PMC00001KL", "1000", WeightUnit.KG, "ATH", LoadingPosition.RL));

        mvc.perform(putPosition("{\"position\":\"RL\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Position RL on flight HJ221/2026-10-19/BRU is already"
                                                + " taken by ULD PMC00001KL"));
        verify(cargoPlans, never()).save(any());
    }

    @Test
    void closedLoadingWindowIsConflict() throws Exception {
        givenFlight(FlightStatus.PLANNED, booking("AKE12345KL", "1250.5", WeightUnit.KG, "AMS"));
        MockMvc closed = mvcAt(Instant.parse("2026-10-19T07:40:00Z"));

        closed.perform(putPosition("{\"position\":\"RL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("WINDOW_CLOSED"));
        closed.perform(delete(POSITION_URL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.refusal").value("WINDOW_CLOSED"));
        verify(cargoPlans, never()).save(any());
    }

    static Stream<RuntimeException> concurrentChanges() {
        return Stream.of(
                new ObjectOptimisticLockingFailureException("cargo_plans", "HJ221"),
                new DataIntegrityViolationException("uld_bookings_one_uld_per_position"));
    }

    @ParameterizedTest
    @MethodSource("concurrentChanges")
    void concurrentChangeIsConflict(RuntimeException concurrentChange) throws Exception {
        givenFlight(FlightStatus.PLANNED, booking("AKE12345KL", "1250.5", WeightUnit.KG, "AMS"));
        doThrow(concurrentChange).when(cargoPlans).save(any());

        mvc.perform(putPosition("{\"position\":\"RL\"}"))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The cargo plan was changed by someone else at the same"
                                                + " time. Reload and try again."));
    }

    @Test
    void flightOperationsDownIsServiceUnavailableForPositionChanges() throws Exception {
        when(flights.findByFlightKey(KEY))
                .thenThrow(new FlightLookupUnavailableException(KEY, null));

        mvc.perform(putPosition("{\"position\":\"RL\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(delete(POSITION_URL)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(cargoPlans);
    }

    private MockMvc mvcAt(Instant now) {
        LoadingWindowService loadingWindowService =
                new LoadingWindowService(
                        flights, cargoPlans, POLICY, Clock.fixed(now, ZoneOffset.UTC));
        UldPositionService uldPositionService =
                new UldPositionService(
                        cargoPlans,
                        loadingWindowService,
                        TransactionOperations.withoutTransaction());
        return MockMvcBuilders.standaloneSetup(
                        new CargoPlanController(
                                new CargoPlanQueryService(flights, cargoPlans), uldPositionService))
                .setControllerAdvice(new CargoApiExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder putPosition(String body) {
        return put(POSITION_URL).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // null when the uld has no position
    private static LoadingPosition positionOf(CargoPlan cargoPlan) {
        return cargoPlan
                .findBooking(new UldCode("AKE12345KL"))
                .orElseThrow()
                .getPosition()
                .orElse(null);
    }

    private CargoPlan givenFlight(FlightStatus status, UldBooking... bookings) {
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
        CargoPlan cargoPlan = CargoPlan.restore(KEY, CargoPlanState.DRAFT, List.of(bookings));
        when(cargoPlans.findByFlightKey(KEY)).thenReturn(Optional.of(cargoPlan));
        return cargoPlan;
    }

    private static UldBooking booking(
            String uldCode, String weight, WeightUnit unit, String destination) {
        return booking(uldCode, weight, unit, destination, null);
    }

    private static UldBooking booking(
            String uldCode,
            String weight,
            WeightUnit unit,
            String destination,
            LoadingPosition position) {
        return new UldBooking(
                new UldCode(uldCode),
                new Weight(new BigDecimal(weight), unit),
                new AirportCode(destination),
                position);
    }
}
