package be.kdg.airfreight.flight.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.flight.infrastructure.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = StatusSecurityTest.Config.class)
class StatusSecurityTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestsReachTheController() throws Exception {
        mvc.perform(get("/api/status").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("flight-service"));
    }

    @Test
    void malformedBearerTokensAreRejected() throws Exception {
        mvc.perform(get("/api/status").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, StatusController.class})
    static class Config {
        @Bean
        JwtDecoder jwtDecoder() {
            var decoder = mock(JwtDecoder.class);
            when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid token"));
            return decoder;
        }
    }
}
