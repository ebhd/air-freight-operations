package be.kdg.airfreight.cargo.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;

class LoadingWindowConfigurationTest {
    // only this configuration so no database or broker is needed
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(LoadingWindowConfiguration.class);

    // reads the real application.properties
    @Test
    void bindsDefaultOffsets() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(
                        context ->
                                assertThat(context.getBean(LoadingWindowPolicy.class))
                                        .isEqualTo(
                                                new LoadingWindowPolicy(
                                                        Duration.ofHours(12),
                                                        Duration.ofMinutes(20))));
    }

    // the environment variables from the readme win over application.properties
    @Test
    void bindsOverriddenOffsets() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(
                        context ->
                                TestPropertyValues.of(
                                                "CARGO_LOADINGWINDOW_OPENSBEFORESTD=24h",
                                                "CARGO_LOADINGWINDOW_CLOSESBEFOREETD=30m")
                                        .applyTo(
                                                context.getEnvironment(),
                                                TestPropertyValues.Type.SYSTEM_ENVIRONMENT))
                .run(
                        context ->
                                assertThat(context.getBean(LoadingWindowPolicy.class))
                                        .isEqualTo(
                                                new LoadingWindowPolicy(
                                                        Duration.ofHours(24),
                                                        Duration.ofMinutes(30))));
    }
}
