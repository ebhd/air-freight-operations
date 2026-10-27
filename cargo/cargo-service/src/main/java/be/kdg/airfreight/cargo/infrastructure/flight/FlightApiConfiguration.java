package be.kdg.airfreight.cargo.infrastructure.flight;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Configuration
class FlightApiConfiguration {
    private static final String CLIENT_REGISTRATION_ID = "flight";
    private static final Authentication CARGO_SERVICE =
            new AnonymousAuthenticationToken(
                    "cargo-service",
                    "cargo-service",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    @Bean
    RestClient flightApiClient(
            @Value("${flight.api.url}") String baseUrl,
            @Value("${spring.security.oauth2.client.registration.flight.client-secret}")
                    String clientSecret,
            ClientRegistrationRepository clientRegistrations,
            OAuth2AuthorizedClientService authorizedClients) {
        Assert.hasText(clientSecret, "CARGO_SERVICE_CLIENT_SECRET must not be blank");
        // this manager works outside an http request too which we need for the rabbit listener
        var authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrations, authorizedClients);
        // fetches and caches a client credentials token and adds it as bearer header
        var tokenInterceptor = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        tokenInterceptor.setClientRegistrationIdResolver(request -> CLIENT_REGISTRATION_ID);
        tokenInterceptor.setPrincipalResolver(request -> CARGO_SERVICE);
        tokenInterceptor.setAuthorizationFailureHandler(
                OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClients));

        // dont let a hanging flight service block the listener for long
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(tokenInterceptor)
                .build();
    }
}
