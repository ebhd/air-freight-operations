package be.kdg.airfreight.cargo.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Checks the REST connection; flight contracts will be added with the first use case. */
@Component("flightApi")
public class FlightApiHealthIndicator implements HealthIndicator {
    private final RestClient client;

    public FlightApiHealthIndicator(@Value("${flight.api.url}") String baseUrl) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public Health health() {
        try {
            client.get().uri("/actuator/health").retrieve().toBodilessEntity();
            return Health.up().build();
        } catch (Exception exception) {
            return Health.down(exception).build();
        }
    }
}
