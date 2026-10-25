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
    static final String SSM_RETRY_QUEUE = "flight.ssm.retry";
    static final String DEAD_LETTER_EXCHANGE = "flight.dlx";
    static final String DEAD_LETTER_QUEUE = "flight.dlq"; // rejected messages queue

    @Bean
    TopicExchange flightEvents() {
        return new TopicExchange(FLIGHT_EVENTS);
    }

    @Bean
    Queue ssm() {
        return QueueBuilder.durable(SSM_QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).build();
    }

    @Bean
    Queue ssmRetryQueue() {
        return QueueBuilder.durable(SSM_RETRY_QUEUE)
                .quorum()
                .ttl(30_000)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .deadLetterExchange("")
                .deadLetterRoutingKey(SSM_QUEUE)
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

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
