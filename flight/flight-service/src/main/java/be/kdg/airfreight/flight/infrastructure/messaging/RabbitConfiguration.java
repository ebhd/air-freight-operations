package be.kdg.airfreight.flight.infrastructure.messaging;

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
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableScheduling
public class RabbitConfiguration {
    public static final String FLIGHT_EVENTS = "flight.events";
    public static final String FLIGHT_CREATED = "flight.created";
    public static final String FLIGHT_ESTIMATES_CHANGED = "flight.estimates-changed";

    public static final String SSM_QUEUE = "flight.ssm";
    static final String DEAD_LETTER_EXCHANGE = "flight.dlx";
    static final String DEAD_LETTER_QUEUE = "flight.dlq"; // rejected messages queue

    // ----- Incoming cargo events (CPM as departure condition) -----
    static final String CARGO_EVENTS = "cargo.events";
    static final String LOAD_FINALIZED = "cargo.load-finalized";
    public static final String CARGO_LOAD_FINALIZED_QUEUE = "flight.cargo-load-finalized";

    @Bean
    TopicExchange flightEvents() {
        return new TopicExchange(FLIGHT_EVENTS);
    }

    @Bean
    Queue ssm() {
        return QueueBuilder.durable(SSM_QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).build();
    }

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

    // ----- Incoming cargo events (CPM as departure condition) -----
    @Bean
    TopicExchange cargoEvents() {
        return new TopicExchange(CARGO_EVENTS);
    }

    // own queue so flight gets its own copy next to the formatter
    @Bean
    Queue cargoLoadFinalized() {
        return QueueBuilder.durable(CARGO_LOAD_FINALIZED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Binding cargoLoadFinalizedBinding(Queue cargoLoadFinalized, TopicExchange cargoEvents) {
        return BindingBuilder.bind(cargoLoadFinalized).to(cargoEvents).with(LOAD_FINALIZED);
    }

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
