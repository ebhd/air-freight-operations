package be.kdg.airfreight.simulator.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class MessagingConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                    .withUserConfiguration(MessagingConfiguration.class);

    // must equal flights declaration or rabbitmq refuses the second declare
    @Test
    void declaresTheSameQueueAsFlight() {
        runner.run(
                context -> {
                    Queue queue = context.getBean("planningReplanned", Queue.class);
                    assertThat(queue.getName()).isEqualTo("planning.flight-replanned");
                    assertThat(queue.isDurable()).isTrue();
                    assertThat(queue.getArguments())
                            .containsExactly(Map.entry("x-dead-letter-exchange", "flight.dlx"));
                });
    }

    @Test
    void bindsTheQueueToTheReplannedEvent() {
        runner.run(
                context ->
                        assertThat(context.getBeansOfType(Binding.class).values())
                                .extracting(
                                        Binding::getExchange,
                                        Binding::getRoutingKey,
                                        Binding::getDestination)
                                .containsExactly(
                                        tuple(
                                                "flight.events",
                                                "flight.replanned",
                                                "planning.flight-replanned")));
    }
}
