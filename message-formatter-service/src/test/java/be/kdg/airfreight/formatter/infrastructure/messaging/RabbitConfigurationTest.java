package be.kdg.airfreight.formatter.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class RabbitConfigurationTest {
    // only this configuration so no broker is needed, it checks what the formatter declares
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                    .withUserConfiguration(RabbitConfiguration.class);

    // the routing keys as flight publishes them, so a typo in a constant fails here
    @Test
    void bindsTheFlightEventQueuesToFlightsExchange() {
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
                                                "flight.departed",
                                                "formatter.flight-departed"),
                                        tuple(
                                                "flight.events",
                                                "flight.landed",
                                                "formatter.flight-landed")));
    }

    // durable with the dead letter exchange so invalid events end up in partner.dlq
    @Test
    void actualTimeQueuesAreDurableWithTheDeadLetterExchange() {
        runner.run(
                context ->
                        assertThat(context.getBeansOfType(Queue.class).values())
                                .filteredOn(
                                        queue -> queue.getName().startsWith("formatter.flight-"))
                                .hasSize(2)
                                .allSatisfy(
                                        queue -> {
                                            assertThat(queue.isDurable()).isTrue();
                                            assertThat(queue.getArguments())
                                                    .containsEntry(
                                                            "x-dead-letter-exchange",
                                                            "partner.dlx");
                                        }));
    }
}
