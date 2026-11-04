package be.kdg.airfreight.simulator.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class MessagingConfiguration {
    // the planning systems inbox: flight declares the same queue with the same arguments,
    // a different declaration would be refused by rabbitmq (precondition failed)
    public static final String PLANNING_REPLANNED_QUEUE = "planning.flight-replanned";
    static final String FLIGHT_EVENTS = "flight.events";
    static final String FLIGHT_REPLANNED = "flight.replanned";
    static final String FLIGHT_DEAD_LETTER_EXCHANGE = "flight.dlx";

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    TopicExchange flightEvents() {
        return new TopicExchange(FLIGHT_EVENTS);
    }

    @Bean
    Queue planningReplanned() {
        return QueueBuilder.durable(PLANNING_REPLANNED_QUEUE)
                .deadLetterExchange(FLIGHT_DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Binding planningReplannedBinding(Queue planningReplanned, TopicExchange flightEvents) {
        return BindingBuilder.bind(planningReplanned).to(flightEvents).with(FLIGHT_REPLANNED);
    }
}
