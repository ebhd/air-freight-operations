package be.kdg.airfreight.cargo.api;

import static org.mockito.Mockito.mock;

import be.kdg.airfreight.cargo.api.cargoplan.CargoPlanController;
import be.kdg.airfreight.cargo.api.loadcheck.FinalizationController;
import be.kdg.airfreight.cargo.api.loadcheck.LoadCheckController;
import be.kdg.airfreight.cargo.api.loadingposition.LoadingPositionController;
import be.kdg.airfreight.cargo.api.loadingwindow.LoadingWindowController;
import be.kdg.airfreight.cargo.application.cargoplan.CargoPlanQueryService;
import be.kdg.airfreight.cargo.application.cargoplan.FinalizeLoadService;
import be.kdg.airfreight.cargo.application.cargoplan.UldPositionService;
import be.kdg.airfreight.cargo.application.loadcheck.LoadCheckService;
import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@TestConfiguration
@EnableWebMvc
@EnableWebSecurity
@ComponentScan("be.kdg.airfreight.cargo.infrastructure.security")
@Import({
    CargoPlanController.class,
    LoadingPositionController.class,
    LoadingWindowController.class,
    LoadCheckController.class,
    FinalizationController.class,
    CargoApiExceptionHandler.class,
    CargoPlanQueryService.class,
    LoadingWindowService.class
})
public class CargoPlanSecurityConfiguration {
    @Bean
    UldPositionService uldPositionService() {
        return mock(UldPositionService.class);
    }

    @Bean
    LoadCheckService loadCheckService() {
        return mock(LoadCheckService.class);
    }

    @Bean
    FinalizeLoadService finalizeLoadService() {
        return mock(FinalizeLoadService.class);
    }

    @Bean
    FlightRepository flightRepository() {
        return mock(FlightRepository.class);
    }

    @Bean
    CargoPlanRepository cargoPlanRepository() {
        return mock(CargoPlanRepository.class);
    }

    @Bean
    LoadingWindowPolicy loadingWindowPolicy() {
        return new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    }

    @Bean
    Clock clock() {
        return Clock.fixed(Instant.parse("2026-10-22T06:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return mock(JwtDecoder.class);
    }
}
