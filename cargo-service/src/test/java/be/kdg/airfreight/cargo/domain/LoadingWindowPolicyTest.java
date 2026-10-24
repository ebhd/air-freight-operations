package be.kdg.airfreight.cargo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LoadingWindowPolicyTest {
    // 10:00 brussels summer time
    private static final Instant STD = Instant.parse("2026-10-22T08:00:00Z");
    private static final LoadingWindowPolicy DEFAULTS =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));

    @Test
    void defaultWindowOpensTwelveHoursBeforeStdAndClosesTwentyMinutesBeforeEtd() {
        LoadingWindow window = DEFAULTS.windowFor(STD, STD);

        assertThat(window.opensAt()).isEqualTo(Instant.parse("2026-10-21T20:00:00Z"));
        assertThat(window.closesAt()).isEqualTo(Instant.parse("2026-10-22T07:40:00Z"));
    }

    @Test
    void departureDelayMovesOnlyTheEnd() {
        Instant etd = STD.plus(Duration.ofMinutes(25));

        LoadingWindow window = DEFAULTS.windowFor(STD, etd);

        assertThat(window.opensAt()).isEqualTo(Instant.parse("2026-10-21T20:00:00Z"));
        assertThat(window.closesAt()).isEqualTo(Instant.parse("2026-10-22T08:05:00Z"));
    }

    @Test
    void usesConfiguredOffsets() {
        LoadingWindowPolicy policy =
                new LoadingWindowPolicy(Duration.ofHours(24), Duration.ofMinutes(30));

        LoadingWindow window = policy.windowFor(STD, STD);

        assertThat(window.opensAt()).isEqualTo(Instant.parse("2026-10-21T08:00:00Z"));
        assertThat(window.closesAt()).isEqualTo(Instant.parse("2026-10-22T07:30:00Z"));
    }

    static Stream<Arguments> invalidOffsets() {
        return Stream.of(
                arguments(
                        null,
                        Duration.ofMinutes(20),
                        "Loading window offset before STD is required"),
                arguments(
                        Duration.ofHours(12), null, "Loading window offset before ETD is required"),
                arguments(
                        Duration.ofMinutes(-1),
                        Duration.ofMinutes(20),
                        "Loading window offsets must not be negative: opens PT-1M before STD,"
                                + " closes PT20M before ETD"),
                arguments(
                        Duration.ofHours(12),
                        Duration.ofMinutes(-1),
                        "Loading window offsets must not be negative: opens PT12H before STD,"
                                + " closes PT-1M before ETD"),
                arguments(
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(20),
                        "Loading window must open longer before STD than it closes before ETD:"
                                + " opens PT10M before STD, closes PT20M before ETD"),
                arguments(
                        Duration.ofMinutes(20),
                        Duration.ofMinutes(20),
                        "Loading window must open longer before STD than it closes before ETD:"
                                + " opens PT20M before STD, closes PT20M before ETD"));
    }

    @ParameterizedTest
    @MethodSource("invalidOffsets")
    void rejectsMissingNegativeOrInvertedOffsets(
            Duration opensBeforeStd, Duration closesBeforeEtd, String message) {
        assertThatThrownBy(() -> new LoadingWindowPolicy(opensBeforeStd, closesBeforeEtd))
                .isInstanceOf(InvalidCargoValueException.class)
                .hasMessage(message);
    }
}
