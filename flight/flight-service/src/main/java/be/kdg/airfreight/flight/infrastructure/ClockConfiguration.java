package be.kdg.airfreight.flight.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfiguration {
    // one injected clock so tests can pass a fixed one
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
