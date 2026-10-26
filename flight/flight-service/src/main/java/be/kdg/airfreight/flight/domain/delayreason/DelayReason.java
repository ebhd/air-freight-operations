package be.kdg.airfreight.flight.domain.delayreason;

public record DelayReason(String code, String label) {
    public DelayReason {
        code = requireText(code, "delay reason code");
        label = requireText(label, "delay reason label");
    }

    // reasons only come from our own table so a broken row is a server error
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
        return value.trim();
    }
}
