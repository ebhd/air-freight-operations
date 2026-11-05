package be.kdg.airfreight.cargo.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

// real token checks with the issuer and audience from the cargo properties file
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = CargoTokenValidationTest.Config.class)
@TestPropertySource("classpath:application.properties")
class CargoTokenValidationTest {
    private static final String ISSUER = "http://localhost:8180/realms/air-freight";

    // stands in for the signing key that keycloak publishes
    private static RSAKey keycloakKey;
    // same key id as keycloak so only the signature check can refuse its tokens
    private static RSAKey unknownKey;
    private static HttpServer keyServer;

    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeAll
    static void startKeyServer() throws Exception {
        keycloakKey = new RSAKeyGenerator(2048).keyID("keycloak").generate();
        unknownKey = new RSAKeyGenerator(2048).keyID("keycloak").generate();
        // only the public half is published like keycloak does on its certs url
        byte[] keys =
                new JWKSet(keycloakKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        keyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keyServer.createContext(
                "/certs",
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, keys.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(keys);
                    }
                });
        keyServer.start();
    }

    @AfterAll
    static void stopKeyServer() {
        keyServer.stop(0);
    }

    // only the address of the keys differs from the cargo properties file
    @DynamicPropertySource
    static void keyServerAddress(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + keyServer.getAddress().getPort() + "/certs");
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void validPlannerTokenIsAccepted() throws Exception {
        mvc.perform(get("/api/status").header(HttpHeaders.AUTHORIZATION, bearer(plannerToken())))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTokenIsRefused() throws Exception {
        Instant now = Instant.now();
        // two minutes ago is past the one minute clock difference that spring allows
        var expired =
                plannerToken()
                        .issuedAt(now.minus(Duration.ofMinutes(10)))
                        .expiresAt(now.minus(Duration.ofMinutes(2)));

        expectRefused(bearer(expired), "Jwt expired at");
    }

    @Test
    void tokenForAnotherServiceIsRefused() throws Exception {
        var forFlight = plannerToken().audience(List.of("flight-service"));

        expectRefused(bearer(forFlight), "The aud claim is not valid");
    }

    @Test
    void tokenFromAnotherRealmIsRefused() throws Exception {
        var otherRealm = plannerToken().issuer("http://localhost:8180/realms/other");

        expectRefused(bearer(otherRealm), "The iss claim is not valid");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRefused() throws Exception {
        expectRefused(bearer(plannerToken(), unknownKey), "Invalid signature");
    }

    private void expectRefused(String authorization, String reason) throws Exception {
        mvc.perform(get("/api/status").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString(reason)));
    }

    // the claims keycloak puts in a load planner token for cargo
    private static JwtClaimsSet.Builder plannerToken() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of("cargo-service"))
                .subject("user-id")
                .claim("preferred_username", "load-planner")
                .claim("realm_access", Map.of("roles", List.of("load-planner")))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)));
    }

    private static String bearer(JwtClaimsSet.Builder claims) {
        return bearer(claims, keycloakKey);
    }

    private static String bearer(JwtClaimsSet.Builder claims, RSAKey signingKey) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
        String token =
                encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return "Bearer " + token;
    }

    // our filter chain with the token decoder that spring boot builds from the properties
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @ComponentScan("be.kdg.airfreight.cargo.infrastructure.security")
    @ImportAutoConfiguration(OAuth2ResourceServerAutoConfiguration.class)
    @Import(StatusController.class)
    static class Config {}
}
