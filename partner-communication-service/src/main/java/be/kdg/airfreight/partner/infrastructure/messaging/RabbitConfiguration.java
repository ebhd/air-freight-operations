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

    static final String FORMATTER_EVENTS = "formatter.events";
    static final String PREPARED = "partner-messages.prepared";
    static final String PREPARED_QUEUE = "partner.messages-prepared";

    // ----- Incoming message dead-letter handling -----
    static final String DEAD_LETTER_EXCHANGE = "partner.sender.dlx";
    static final String DEAD_LETTER_QUEUE = "partner.sender.dlq";

    // ----- Outgoing partner messages -----
    static final String PARTNER_MESSAGES = "partner.messages";
    static final String PARTNER_OUTGOING_QUEUE = "partner.outgoing";
    static final String FLM = "flm";
    static final String MVT_ED = "mvt.ed";
    static final String MVT_EA = "mvt.ea";
    static final String MVT_AD = "mvt.ad";
    static final String MVT_AA = "mvt.aa";
    static final String CPM = "cpm";
    static final String FLIGHT_REPLANNED = "flight.replanned";

    @Bean
    TopicExchange formatterEvents() {
        return new TopicExchange(FORMATTER_EVENTS);
    }

    @Bean
    Queue preparedQueue() {
        return QueueBuilder.durable(PREPARED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Binding preparedBinding(Queue preparedQueue, TopicExchange formatterEvents) {
        return BindingBuilder.bind(preparedQueue).to(formatterEvents).with(PREPARED);
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
