package be.kdg.airfreight.flight.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class RabbitConfigurationTest {
    // only this configuration so no broker is needed, it checks what flight declares
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                    .withUserConfiguration(RabbitConfiguration.class);

    // the simulator declares the same queue so the name and arguments must match exactly
    @Test
    void planningQueueIsDurableWithTheDeadLetterExchange() {
        runner.run(
                context -> {
                    Queue queue = context.getBean("planningReplanned", Queue.class);
                    assertThat(queue.getName()).isEqualTo("planning.flight-replanned");
                    assertThat(queue.isDurable()).isTrue();
                    assertThat(queue.getArguments())
                            .containsExactly(
                                    java.util.Map.entry("x-dead-letter-exchange", "flight.dlx"));
                });
    }

    @Test
    void planningQueueIsBoundToTheReplannedEvent() {
        runner.run(
                context ->
                        assertThat(context.getBeansOfType(Binding.class).values())
                                .extracting(
                                        Binding::getExchange,
                                        Binding::getRoutingKey,
                                        Binding::getDestination)
                                .contains(
                                        tuple(
                                                "flight.events",
                                                "flight.replanned",
                                                "planning.flight-replanned")));
    }
}
