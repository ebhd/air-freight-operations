package be.kdg.airfreight.flight.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.flight.domain.flight.ActualTimePolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;

class ActualTimeConfigurationTest {
    // only this configuration so no database or broker is needed
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ActualTimeConfiguration.class);

    // reads the real application.properties
    @Test
    void bindsTheDefaultTolerance() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(
                        context ->
                                assertThat(context.getBean(ActualTimePolicy.class))
                                        .isEqualTo(new ActualTimePolicy(Duration.ofMinutes(5))));
    }

    // the environment variable from the readme wins over application.properties
    @Test
    void bindsAnOverriddenTolerance() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(
                        context ->
                                TestPropertyValues.of("FLIGHT_ACTUALTIMES_TOLERANCE=10m")
                                        .applyTo(
                                                context.getEnvironment(),
                                                TestPropertyValues.Type.SYSTEM_ENVIRONMENT))
                .run(
                        context ->
                                assertThat(context.getBean(ActualTimePolicy.class))
                                        .isEqualTo(new ActualTimePolicy(Duration.ofMinutes(10))));
    }
}
