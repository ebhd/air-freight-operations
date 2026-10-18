package be.kdg.airfreight.partner.infrastructure.messaging;

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

    static final String FLIGHT_EVENTS = "flight.events";

    static final String FLIGHT_CREATED = "flight.created";
    static final String FLIGHT_CREATED_QUEUE = "partner.flight-created";
    static final String DEAD_LETTER_EXCHANGE = "partner.dlx";
    static final String DEAD_LETTER_QUEUE = "partner.dlq";

    @Bean
    Queue flightCreatedQueue() {
        return QueueBuilder.durable(FLIGHT_CREATED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    TopicExchange flightEvents() {
        return new TopicExchange(FLIGHT_EVENTS);
    }

    @Bean
    Binding flightCreatedBinding(Queue flightCreatedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightCreatedQueue).to(flightEvents).with(FLIGHT_CREATED);
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

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
