package be.kdg.airfreight.cargo.infrastructure.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.retry.ImmediateRequeueMessageRecoverer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitConfiguration {
    static final String ULD_QUEUE = "cargo.uld";

    @Bean
    Queue uldQueue() {
        return QueueBuilder.durable(ULD_QUEUE).build();
    }

    // after the retries put the message back on the queue instead of dropping it
    @Bean
    MessageRecoverer requeueAfterRetries() {
        return new ImmediateRequeueMessageRecoverer();
    }
}
