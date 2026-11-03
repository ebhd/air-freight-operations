package be.kdg.airfreight.cargo.infrastructure.flight;

import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupRejectedException;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// flights open host service, only asked for a flight that is not in cargos copy yet
@Component
@Slf4j
class FlightOperationsClient {
    private final RestClient flightApiClient;

    FlightOperationsClient(RestClient flightApiClient) {
        this.flightApiClient = flightApiClient;
    }

    Optional<FlightInformation> fetch(FlightKey flightKey) {
        try {
            FlightResponse response =
                    flightApiClient
                            .get()
                            .uri(
                                    "/api/flights/{flightNumber}/{date}/{airport}",
                                    flightKey.flightNumber(),
                                    flightKey.scheduledDepartureDate(),
                                    flightKey.departureAirportCode().value())
                            .retrieve()
                            .body(FlightResponse.class);
            return Optional.ofNullable(response).map(FlightResponse::toFlightInformation);
        } catch (HttpClientErrorException.NotFound notFound) {
            // unknown flight not an error
            log.debug("Flight Operations does not know flight {}", flightKey);
            return Optional.empty();
        } catch (HttpClientErrorException clientError) {
            // 401 or 403 is a token or config problem that can be fixed so retry
            if (isAuthenticationProblem(clientError)) {
                throw unavailable(flightKey, clientError);
            }
            // any other 4xx means our request is invalid and retrying wont help
            throw new FlightLookupRejectedException(
                    flightKey, clientError.getStatusCode().value(), clientError);
        } catch (RestClientException | OAuth2AuthorizationException exception) {
            // flight down timeout 5xx or keycloak unreachable
            throw unavailable(flightKey, exception);
        }
    }

    private static boolean isAuthenticationProblem(HttpClientErrorException clientError) {
        return clientError.getStatusCode() == HttpStatus.UNAUTHORIZED
                || clientError.getStatusCode() == HttpStatus.FORBIDDEN;
    }

    private static FlightLookupUnavailableException unavailable(
            FlightKey flightKey, RuntimeException cause) {
        log.warn("Flight lookup for {} failed: {}", flightKey, cause.getMessage());
        return new FlightLookupUnavailableException(flightKey, cause);
    }
}
