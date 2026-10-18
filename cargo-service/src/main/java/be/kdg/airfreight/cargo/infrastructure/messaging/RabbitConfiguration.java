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

    /**
     * Only failures that may pass, such as an unreachable database or Flight Operations, reach the
     * listener's retry. After the retries the message goes back on the queue instead of being
     * dropped. Rejected input is handled in the listener itself.
     */
    @Bean
    MessageRecoverer requeueAfterRetries() {
        return new ImmediateRequeueMessageRecoverer();
    }
}
