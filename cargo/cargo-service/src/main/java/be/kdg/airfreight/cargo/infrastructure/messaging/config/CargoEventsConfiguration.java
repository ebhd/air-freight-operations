package be.kdg.airfreight.cargo.infrastructure.messaging.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// outgoing cargo events, every consumer binds its own queue to this exchange
@Configuration
@EnableScheduling
public class CargoEventsConfiguration {
    public static final String CARGO_EVENTS = "cargo.events";
    public static final String LOAD_FINALIZED = "cargo.load-finalized";

    @Bean
    TopicExchange cargoEvents() {
        return new TopicExchange(CARGO_EVENTS);
    }
}
