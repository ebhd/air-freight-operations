package be.kdg.airfreight.cargo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoadingWindowTest {
    private static final Instant OPENS_AT = Instant.parse("2026-10-21T20:00:00Z");
    private static final Instant CLOSES_AT = Instant.parse("2026-10-22T07:40:00Z");
    private static final LoadingWindow WINDOW = new LoadingWindow(OPENS_AT, CLOSES_AT);

    @Test
    void isOpenFromTheOpeningInstant() {
        assertThat(WINDOW.hasNotOpenedAt(OPENS_AT.minusNanos(1))).isTrue();
        assertThat(WINDOW.hasNotOpenedAt(OPENS_AT)).isFalse();
    }

    // q06 our reading is that the window is over at exactly etd minus 20 minutes
    @Test
    void isClosedFromTheClosingInstant() {
        assertThat(WINDOW.hasClosedAt(CLOSES_AT.minusNanos(1))).isFalse();
        assertThat(WINDOW.hasClosedAt(CLOSES_AT)).isTrue();
    }
}
