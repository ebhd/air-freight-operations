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
    public static final String FLIGHT_DEPARTED = "flight.departed";
    public static final String FLIGHT_LANDED = "flight.landed";
    public static final String FLIGHT_REPLANNED = "flight.replanned";
    public static final String FLIGHT_REPLACED = "flight.replaced";
    public static final String FLIGHT_REMOVED = "flight.removed";
    // the planning systems inbox, in our project read by the simulator
    public static final String PLANNING_REPLANNED_QUEUE = "planning.flight-replanned";

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

    // ----- Outgoing replanning request to the planning system -----
    // flight declares this queue itself: with mandatory publishing and other bound queues a
    // missing queue would silently lose the event for the planning system; declaring the same
    // durable queue twice (here and in the simulator) is harmless when the arguments are equal
    @Bean
    Queue planningReplanned() {
        return QueueBuilder.durable(PLANNING_REPLANNED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Binding planningReplannedBinding(Queue planningReplanned, TopicExchange flightEvents) {
        return BindingBuilder.bind(planningReplanned).to(flightEvents).with(FLIGHT_REPLANNED);
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
