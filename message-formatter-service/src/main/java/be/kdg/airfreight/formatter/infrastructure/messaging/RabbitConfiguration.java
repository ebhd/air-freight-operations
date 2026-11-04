package be.kdg.airfreight.formatter.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitConfiguration {

    // ----- Incoming Flight events -----
    static final String FLIGHT_EVENTS = "flight.events";
    static final String FLIGHT_CREATED = "flight.created";
    static final String FLIGHT_CREATED_QUEUE = "partner.flight-created";
    static final String FLIGHT_ESTIMATES_CHANGED = "flight.estimates-changed";
    static final String FLIGHT_ESTIMATES_CHANGED_QUEUE = "partner.flight-estimates-changed";

    // ----- Incoming Cargo events -----
    static final String CARGO_EVENTS = "cargo.events";
    static final String LOAD_FINALIZED = "cargo.load-finalized";
    static final String LOAD_FINALIZED_QUEUE = "formatter.cargo-load-finalized";

    // ----- Incoming message dead-letter handling -----
    static final String DEAD_LETTER_EXCHANGE = "partner.dlx";
    static final String DEAD_LETTER_QUEUE = "partner.dlq";

    static final String FORMATTER_EVENTS = "formatter.events";
    static final String PREPARED = "partner-messages.prepared";

    // ----- Incoming Flight events -----
    @Bean
    TopicExchange flightEvents() {
        return new TopicExchange(FLIGHT_EVENTS);
    }

    @Bean
    Queue flightCreatedQueue() {
        return QueueBuilder.durable(FLIGHT_CREATED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Binding flightCreatedBinding(Queue flightCreatedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightCreatedQueue).to(flightEvents).with(FLIGHT_CREATED);
    }

    @Bean
    Queue flightEstimatesChangedQueue() {
        return QueueBuilder.durable(FLIGHT_ESTIMATES_CHANGED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Binding flightEstimatesChangedBinding(
            Queue flightEstimatesChangedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightEstimatesChangedQueue)
                .to(flightEvents)
                .with(FLIGHT_ESTIMATES_CHANGED);
    }

    // ----- Incoming Cargo events -----
    @Bean
    TopicExchange cargoEvents() {
        return new TopicExchange(CARGO_EVENTS);
    }

    @Bean
    Queue loadFinalizedQueue() {
        return QueueBuilder.durable(LOAD_FINALIZED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Binding loadFinalizedBinding(Queue loadFinalizedQueue, TopicExchange cargoEvents) {
        return BindingBuilder.bind(loadFinalizedQueue).to(cargoEvents).with(LOAD_FINALIZED);
    }

    // ----- Incoming message dead-letter handling -----
    @Bean
    FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }

    @Bean
    TopicExchange formatterEvents() {
        return new TopicExchange(FORMATTER_EVENTS);
    }

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
