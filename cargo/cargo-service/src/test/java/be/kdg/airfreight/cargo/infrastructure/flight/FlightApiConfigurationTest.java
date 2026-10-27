package be.kdg.airfreight.cargo.infrastructure.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.client.MockRestServiceServer;

class FlightApiConfigurationTest {
    private HttpServer tokenServer;
    private final AtomicInteger tokenRequests = new AtomicInteger();

    @BeforeEach
    void startTokenEndpoint() throws Exception {
        tokenServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tokenServer.createContext(
                "/token",
                exchange -> {
                    tokenRequests.incrementAndGet();
                    byte[] body =
                            "{\"access_token\":\"service-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        tokenServer.start();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        tokenServer.stop(0);
    }

    @Test
    void reusesTheServiceTokenAcrossUsersAndBackgroundCalls() {
        var registrations = registrations();
        var authorizedClients = new InMemoryOAuth2AuthorizedClientService(registrations);
        var builder =
                new FlightApiConfiguration()
                        .flightApiClient(
                                "http://flight", "test-secret", registrations, authorizedClients)
                        .mutate();
        var server = MockRestServiceServer.bindTo(builder).build();
        for (int request = 0; request < 3; request++) {
            server.expect(requestTo("http://flight/api/status"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }
        var client = builder.build();
        for (String user : new String[] {"alice", "bob"}) {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(user, "unused"));
            client.get().uri("/api/status").retrieve().toBodilessEntity();
        }
        SecurityContextHolder.clearContext();
        client.get().uri("/api/status").retrieve().toBodilessEntity();

        server.verify();
        assertThat(tokenRequests).hasValue(1);
        OAuth2AuthorizedClient cargoClient =
                authorizedClients.loadAuthorizedClient("flight", "cargo-service");
        OAuth2AuthorizedClient aliceClient =
                authorizedClients.loadAuthorizedClient("flight", "alice");
        OAuth2AuthorizedClient bobClient = authorizedClients.loadAuthorizedClient("flight", "bob");
        assertThat(cargoClient).isNotNull();
        assertThat(aliceClient).isNull();
        assertThat(bobClient).isNull();
    }

    @Test
    void refusesABlankClientSecret() {
        var registrations = registrations();
        assertThatThrownBy(
                        () ->
                                new FlightApiConfiguration()
                                        .flightApiClient(
                                                "http://flight",
                                                " ",
                                                registrations,
                                                new InMemoryOAuth2AuthorizedClientService(
                                                        registrations)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CARGO_SERVICE_CLIENT_SECRET must not be blank");
    }

    private InMemoryClientRegistrationRepository registrations() {
        return new InMemoryClientRegistrationRepository(
                ClientRegistration.withRegistrationId("flight")
                        .clientId("cargo-service")
                        .clientSecret("test-secret")
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .tokenUri(
                                "http://127.0.0.1:" + tokenServer.getAddress().getPort() + "/token")
                        .build());
    }
}
