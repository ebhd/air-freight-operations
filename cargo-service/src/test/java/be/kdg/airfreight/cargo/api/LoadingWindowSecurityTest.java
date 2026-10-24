package be.kdg.airfreight.cargo.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.application.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.FlightInformation;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.FlightRepository;
import be.kdg.airfreight.cargo.domain.FlightStatus;
import be.kdg.airfreight.cargo.domain.LoadingWindowPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
@ContextConfiguration(classes = LoadingWindowSecurityTest.Config.class)
class LoadingWindowSecurityTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-22/BRU/loading-window";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU"));
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");

    @Autowired private WebApplicationContext context;
    @Autowired private FlightRepository flights;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(flights.findByFlightKey(KEY))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        KEY,
                                        new AirportCode("AMS"),
                                        FlightStatus.PLANNED,
                                        STD,
                                        Instant.parse("2026-10-22T10:00:00Z"),
                                        STD)));
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUserReadsTheLoadingWindow() throws Exception {
        mvc.perform(
                        get(URL).with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_load-planner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closesAt").value("2026-10-22T07:40:00Z"))
                .andExpect(jsonPath("$.loadingAllowed").value(true));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @ComponentScan("be.kdg.airfreight.cargo.infrastructure.security")
    @Import({
        LoadingWindowController.class,
        CargoApiExceptionHandler.class,
        LoadingWindowService.class
    })
    static class Config {
        @Bean
        FlightRepository flightRepository() {
            return mock(FlightRepository.class);
        }

        // no bookings stored so the plan is an empty draft
        @Bean
        CargoPlanRepository cargoPlanRepository() {
            return mock(CargoPlanRepository.class);
        }

        @Bean
        LoadingWindowPolicy loadingWindowPolicy() {
            return new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
        }

        // inside the window
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-22T06:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
