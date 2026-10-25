package be.kdg.airfreight.flight.domain.delayreason;

import java.util.Objects;

public record DelayReason(String code, String label) {
    public DelayReason {
        code = requireText(code, "delay reason code");
        label = requireText(label, "delay reason label");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
