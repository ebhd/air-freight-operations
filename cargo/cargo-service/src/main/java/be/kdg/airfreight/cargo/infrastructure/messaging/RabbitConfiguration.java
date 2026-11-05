package be.kdg.airfreight.cargo.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.ImmediateRequeueMessageRecoverer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;

@Configuration
@Slf4j
class RabbitConfiguration {
    static final String ULD_QUEUE = "cargo.uld";

    static final String FLIGHT_EVENTS = "flight.events";
    static final String FLIGHT_CREATED = "flight.created";
    static final String FLIGHT_CREATED_QUEUE = "cargo.flight-created";
    static final String FLIGHT_ESTIMATES_CHANGED = "flight.estimates-changed";
    static final String FLIGHT_ESTIMATES_CHANGED_QUEUE = "cargo.flight-estimates-changed";
    static final String FLIGHT_DEPARTED = "flight.departed";
    static final String FLIGHT_DEPARTED_QUEUE = "cargo.flight-departed";
    static final String FLIGHT_LANDED = "flight.landed";
    static final String FLIGHT_LANDED_QUEUE = "cargo.flight-landed";
    static final String FLIGHT_REPLANNED = "flight.replanned";
    static final String FLIGHT_REPLANNED_QUEUE = "cargo.flight-replanned";
    static final String FLIGHT_REPLACED = "flight.replaced";
    static final String FLIGHT_REPLACED_QUEUE = "cargo.flight-replaced";

    @Bean
    Queue uldQueue() {
        return QueueBuilder.durable(ULD_QUEUE).build();
    }

    // flight declares the same exchange so declaring it here too changes nothing
    @Bean
    TopicExchange flightEvents() {
        return new TopicExchange(FLIGHT_EVENTS);
    }

    // cargo's own queues so flight and partner never wait for cargo
    @Bean
    Queue flightCreatedQueue() {
        return QueueBuilder.durable(FLIGHT_CREATED_QUEUE).build();
    }

    @Bean
    Binding flightCreatedBinding(Queue flightCreatedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightCreatedQueue).to(flightEvents).with(FLIGHT_CREATED);
    }

    @Bean
    Queue flightEstimatesChangedQueue() {
        return QueueBuilder.durable(FLIGHT_ESTIMATES_CHANGED_QUEUE).build();
    }

    @Bean
    Binding flightEstimatesChangedBinding(
            Queue flightEstimatesChangedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightEstimatesChangedQueue)
                .to(flightEvents)
                .with(FLIGHT_ESTIMATES_CHANGED);
    }

    @Bean
    Queue flightDepartedQueue() {
        return QueueBuilder.durable(FLIGHT_DEPARTED_QUEUE).build();
    }

    @Bean
    Binding flightDepartedBinding(Queue flightDepartedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightDepartedQueue).to(flightEvents).with(FLIGHT_DEPARTED);
    }

    @Bean
    Queue flightLandedQueue() {
        return QueueBuilder.durable(FLIGHT_LANDED_QUEUE).build();
    }

    @Bean
    Binding flightLandedBinding(Queue flightLandedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightLandedQueue).to(flightEvents).with(FLIGHT_LANDED);
    }

    // the old flight of a replanning: only its status changes in the copy
    @Bean
    Queue flightReplannedQueue() {
        return QueueBuilder.durable(FLIGHT_REPLANNED_QUEUE).build();
    }

    @Bean
    Binding flightReplannedBinding(Queue flightReplannedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightReplannedQueue).to(flightEvents).with(FLIGHT_REPLANNED);
    }

    // the new flight exists: move the cargo of the old one
    @Bean
    Queue flightReplacedQueue() {
        return QueueBuilder.durable(FLIGHT_REPLACED_QUEUE).build();
    }

    @Bean
    Binding flightReplacedBinding(Queue flightReplacedQueue, TopicExchange flightEvents) {
        return BindingBuilder.bind(flightReplacedQueue).to(flightEvents).with(FLIGHT_REPLACED);
    }

    // after the retries put the message back on the queue instead of dropping it
    // only an outage gets here, the listeners store rejections themselves
    @Bean
    MessageRecoverer requeueAfterRetries() {
        MessageRecoverer requeue = new ImmediateRequeueMessageRecoverer();
        return (message, cause) -> {
            log.error(
                    "Requeuing message {} on queue {}, not processed after the retries: {}",
                    message.getMessageProperties().getMessageId(),
                    message.getMessageProperties().getConsumerQueue(),
                    NestedExceptionUtils.getMostSpecificCause(cause).toString());
            requeue.recover(message, cause);
        };
    }
}
