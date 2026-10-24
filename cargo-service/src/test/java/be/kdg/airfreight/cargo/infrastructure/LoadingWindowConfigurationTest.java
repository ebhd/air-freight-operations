package be.kdg.airfreight.cargo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.LoadingWindowPolicy;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    static Stream<Arguments> invalidSettings() {
        return Stream.of(
                arguments(
                        new String[] {"cargo.loading-window.closes-before-etd=20m"},
                        "Loading window offset before STD is required"),
                arguments(
                        new String[] {
                            "cargo.loading-window.opens-before-std=-1h",
                            "cargo.loading-window.closes-before-etd=20m"
                        },
                        "Loading window offsets must not be negative"),
                arguments(
                        new String[] {
                            "cargo.loading-window.opens-before-std=10m",
                            "cargo.loading-window.closes-before-etd=20m"
                        },
                        "Loading window must open longer before STD than it closes before ETD"));
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void failsToStartWithInvalidOffsets(String[] settings, String message) {
        runner.withPropertyValues(settings)
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .isInstanceOf(InvalidCargoValueException.class)
                                    .hasMessageContaining(message);
                        });
    }
}
