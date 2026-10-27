package be.kdg.airfreight.cargo.infrastructure.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupRejectedException;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpFlightRepositoryTest {
    private static final String URL = "http://flight/api/flights/HJ221/2026-10-19/BRU";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));

    private MockRestServiceServer flight;
    private HttpFlightRepository repository;

    @BeforeEach
    void setUp() {
        // fake flight service no real http and no token
        RestClient.Builder builder = RestClient.builder().baseUrl("http://flight");
        flight = MockRestServiceServer.bindTo(builder).build();
        repository = new HttpFlightRepository(builder.build());
    }

    @Test
    void plannedFlightIsFound() {
        flight.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json("PLANNED"), MediaType.APPLICATION_JSON));

        assertThat(repository.findByFlightKey(KEY))
                .hasValueSatisfying(
                        info -> {
                            assertThat(info.flightKey()).isEqualTo(KEY);
                            assertThat(info.planned()).isTrue();
                        });
        flight.verify();
    }

    @Test
    void departedFlightIsNotPlanned() {
        flight.expect(requestTo(URL))
                .andRespond(withSuccess(json("DEPARTED"), MediaType.APPLICATION_JSON));

        assertThat(repository.findByFlightKey(KEY))
                .hasValueSatisfying(info -> assertThat(info.planned()).isFalse());
    }

    // flight sends no delays until issue 15 so etd equals std
    @Test
    void estimatedDepartureEqualsScheduledDepartureUntilFlightSendsDelays() {
        flight.expect(requestTo(URL))
                .andRespond(withSuccess(json("PLANNED"), MediaType.APPLICATION_JSON));

        assertThat(repository.findByFlightKey(KEY))
                .hasValueSatisfying(
                        info ->
                                assertThat(info.estimatedDeparture())
                                        .isEqualTo(Instant.parse("2026-10-19T08:00:00Z")));
    }

    @Test
    void notFoundMeansUnknownFlight() {
        flight.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(repository.findByFlightKey(KEY)).isEmpty();
    }

    @Test
    void badRequestIsInvalidInputNotAnOutage() {
        // retrying a request flight refuses would loop forever
        flight.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> repository.findByFlightKey(KEY))
                .isInstanceOf(FlightLookupRejectedException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = HttpStatus.class,
            names = {"UNAUTHORIZED", "FORBIDDEN", "INTERNAL_SERVER_ERROR", "SERVICE_UNAVAILABLE"})
    void authProblemsAndServerErrorsAreRetryable(HttpStatus status) {
        flight.expect(requestTo(URL)).andRespond(withStatus(status));

        assertThatThrownBy(() -> repository.findByFlightKey(KEY))
                .isInstanceOf(FlightLookupUnavailableException.class);
    }

    @Test
    void dayOverviewIsTranslatedToTheCargoModel() {
        flight.expect(requestTo("http://flight/api/flights?date=2026-10-19"))
                .andRespond(withSuccess("[" + json("DEPARTED") + "]", MediaType.APPLICATION_JSON));

        assertThat(repository.findByScheduledDepartureDate(LocalDate.of(2026, 10, 19)))
                .singleElement()
                .satisfies(
                        info -> {
                            assertThat(info.flightKey()).isEqualTo(KEY);
                            assertThat(info.arrivalAirportCode()).isEqualTo(new AirportCode("AMS"));
                            assertThat(info.status()).isEqualTo(FlightStatus.DEPARTED);
                            assertThat(info.scheduledDeparture())
                                    .isEqualTo(Instant.parse("2026-10-19T08:00:00Z"));
                        });
    }

    @Test
    void dayOverviewOutageIsRetryable() {
        flight.expect(requestTo("http://flight/api/flights?date=2026-10-19"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(
                        () -> repository.findByScheduledDepartureDate(LocalDate.of(2026, 10, 19)))
                .isInstanceOf(FlightLookupUnavailableException.class);
    }

    @Test
    void connectionFailureIsRetryable() {
        flight.expect(requestTo(URL)).andRespond(withException(new IOException("refused")));

        assertThatThrownBy(() -> repository.findByFlightKey(KEY))
                .isInstanceOf(FlightLookupUnavailableException.class);
    }

    @Test
    void tokenFailureIsRetryable() {
        // keycloak down surfaces as an oauth2 exception from the token interceptor
        RestClient failingTokenClient =
                RestClient.builder()
                        .baseUrl("http://flight")
                        .requestInterceptor(
                                (request, body, execution) -> {
                                    throw new OAuth2AuthorizationException(
                                            new OAuth2Error("server_error"));
                                })
                        .build();

        assertThatThrownBy(() -> new HttpFlightRepository(failingTokenClient).findByFlightKey(KEY))
                .isInstanceOf(FlightLookupUnavailableException.class);
    }

    private static String json(String status) {
        return """
                {"flightNumber":"HJ221","scheduledDepartureDate":"2026-10-19",
                 "departureAirportCode":"BRU","arrivalAirportCode":"AMS","status":"%s",
                 "scheduledDeparture":"2026-10-19T08:00:00Z","scheduledArrival":"2026-10-19T10:00:00Z"}
                """
                .formatted(status);
    }
}
