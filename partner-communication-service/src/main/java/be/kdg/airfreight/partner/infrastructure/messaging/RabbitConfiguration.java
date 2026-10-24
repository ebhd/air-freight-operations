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
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableScheduling
public class RabbitConfiguration {

    // ----- Incoming Flight events -----
    static final String FLIGHT_EVENTS = "flight.events";
    static final String FLIGHT_CREATED = "flight.created";
    static final String FLIGHT_CREATED_QUEUE = "partner.flight-created";

    // ----- Incoming message retry and dead-letter handling -----
    static final String FLIGHT_CREATED_RETRY_QUEUE = "partner.flight-created.retry";
    static final String DEAD_LETTER_EXCHANGE = "partner.dlx";
    static final String DEAD_LETTER_QUEUE = "partner.dlq";

    // ----- Outgoing partner messages -----
    static final String PARTNER_MESSAGES = "partner.messages";
    static final String PARTNER_OUTGOING_QUEUE = "partner.outgoing";
    static final String FLM = "flm";

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

    // ----- incoming message retry and dead-letter handling -----
    @Bean
    Queue flightCreatedRetryQueue() {
        return QueueBuilder.durable(FLIGHT_CREATED_RETRY_QUEUE)
                .quorum()
                .ttl(30_000)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .deadLetterExchange("")
                .deadLetterRoutingKey(FLIGHT_CREATED_QUEUE)
                .build();
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

    // ----- Outgoing partner messages -----
    @Bean
    TopicExchange partnerMessages() {
        return new TopicExchange(PARTNER_MESSAGES);
    }

    @Bean
    Queue partnerOutgoingQueue() {
        return QueueBuilder.durable(PARTNER_OUTGOING_QUEUE).build();
    }

    @Bean
    Binding partnerOutgoingBinding(Queue partnerOutgoingQueue, TopicExchange partnerMessages) {
        return BindingBuilder.bind(partnerOutgoingQueue).to(partnerMessages).with("#");
    }

    // ----- Message conversion -----
    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
