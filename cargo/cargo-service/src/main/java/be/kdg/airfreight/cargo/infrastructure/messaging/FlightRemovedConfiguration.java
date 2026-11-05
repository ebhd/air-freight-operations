package be.kdg.airfreight.cargo.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// the flight passed its retention: a durable queue so the cleanup waits while cargo is down
@Configuration
class FlightRemovedConfiguration {
    static final String FLIGHT_REMOVED = "flight.removed";
    static final String FLIGHT_REMOVED_QUEUE = "cargo.flight-removed";

    @Bean
    Queue flightRemovedQueue() {
        return QueueBuilder.durable(FLIGHT_REMOVED_QUEUE).build();
    }

    @Bean
    Binding flightRemovedBinding(Queue flightRemovedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightRemovedQueue).to(flightEvents).with(FLIGHT_REMOVED);
    }
}
