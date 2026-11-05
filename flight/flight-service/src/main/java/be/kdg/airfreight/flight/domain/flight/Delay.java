package be.kdg.airfreight.flight.domain.flight;

import java.util.Objects;
import java.util.UUID;

public final class Delay {
    // one delay is at most 12 hours, flt-08
    public static final int MAX_MINUTES = 720;

    private final UUID id;
    private int minutes;
    private final String reasonCode;
    private String description;

    Delay(int minutes, String reasonCode, String description) {
        this(UUID.randomUUID(), minutes, reasonCode, description);
    }

    private Delay(UUID id, int minutes, String reasonCode, String description) {
        this.id = Objects.requireNonNull(id, "delay id must not be null");
        this.minutes = requireMinutes(minutes);
        this.reasonCode = requireText(reasonCode, "delay reason code");
        this.description = requireText(description, "delay description");
    }

    public static Delay restore(UUID id, int minutes, String reasonCode, String description) {
        return new Delay(id, minutes, reasonCode, description);
    }

    void update(int minutes, String description) {
        int validatedMinutes = requireMinutes(minutes);
        String validatedDescription = requireText(description, "delay description");
        this.minutes = validatedMinutes;
        this.description = validatedDescription;
    }

    // package private so flight can validate the minutes before it checks the total
    static int requireMinutes(int minutes) {
        if (minutes < 1 || minutes > MAX_MINUTES) {
            throw new InvalidDelayException("delay minutes must be between 1 and " + MAX_MINUTES);
        }
        return minutes;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidDelayException(name + " must not be blank");
        }
        return value.trim();
    }

    public UUID id() {
        return id;
    }

    public int minutes() {
        return minutes;
    }

    public String reasonCode() {
        return reasonCode;
    }

    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Delay delay && id.equals(delay.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
