package be.kdg.airfreight.cargo.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RabbitConfigurationTest {
    // only this configuration so no broker is needed, it checks what cargo declares
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(RabbitConfiguration.class);

    // the names as flight publishes them, so a typo in a constant fails here
    @Test
    void bindsTheFlightQueuesToFlightsExchange() {
        runner.run(
                context ->
                        assertThat(context.getBeansOfType(Binding.class).values())
                                .extracting(
                                        Binding::getExchange,
                                        Binding::getRoutingKey,
                                        Binding::getDestination)
                                .containsExactlyInAnyOrder(
                                        tuple(
                                                "flight.events",
                                                "flight.created",
                                                "cargo.flight-created"),
                                        tuple(
                                                "flight.events",
                                                "flight.estimates-changed",
                                                "cargo.flight-estimates-changed"),
                                        tuple(
                                                "flight.events",
                                                "flight.departed",
                                                "cargo.flight-departed"),
                                        tuple(
                                                "flight.events",
                                                "flight.landed",
                                                "cargo.flight-landed")));
    }

    // durable so events wait in the queue while cargo is down
    @Test
    void flightQueuesAreDurable() {
        runner.run(
                context ->
                        assertThat(context.getBeansOfType(Queue.class).values())
                                .filteredOn(queue -> queue.getName().startsWith("cargo.flight-"))
                                .hasSize(4)
                                .allMatch(Queue::isDurable));
    }
}
