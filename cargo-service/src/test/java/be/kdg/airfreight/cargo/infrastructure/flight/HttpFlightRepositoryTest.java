package be.kdg.airfreight.cargo.infrastructure.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.FlightLookupRejectedException;
import be.kdg.airfreight.cargo.domain.FlightLookupUnavailableException;
import java.io.IOException;
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
