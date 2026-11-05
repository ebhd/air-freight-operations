package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.domain.flight.DelayType;
import be.kdg.airfreight.flight.domain.flight.replanning.Replanning;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

// the replanning columns of the flights table, hibernate gives null when they are all null
@Embeddable
public class JpaReplanning {
    @Column(name = "replanned_flight_number", length = 32)
    private String newFlightNumber;

    @Column(name = "replanned_departure")
    private Instant newScheduledDeparture;

    @Column(name = "replanned_arrival")
    private Instant newScheduledArrival;

    @Enumerated(EnumType.STRING)
    @Column(name = "replan_delay_type", length = 16)
    private DelayType delayType;

    // boxed so the column can be null for a flight that was not replanned
    @Column(name = "replan_minutes")
    private Integer minutes;

    @Column(name = "replan_reason_code", length = 16)
    private String reasonCode;

    @Column(name = "replan_description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "replanned_at")
    private Instant replannedAt;

    protected JpaReplanning() {}

    static JpaReplanning fromDomain(Replanning replanning) {
        if (replanning == null) {
            return null;
        }
        JpaReplanning jpa = new JpaReplanning();
        jpa.newFlightNumber = replanning.newFlightNumber();
        jpa.newScheduledDeparture = replanning.newScheduledDeparture();
        jpa.newScheduledArrival = replanning.newScheduledArrival();
        jpa.delayType = replanning.delayType();
        jpa.minutes = replanning.minutes();
        jpa.reasonCode = replanning.reasonCode();
        jpa.description = replanning.description();
        jpa.replannedAt = replanning.replannedAt();
        return jpa;
    }

    Replanning toDomain() {
        return new Replanning(
                newFlightNumber,
                newScheduledDeparture,
                newScheduledArrival,
                delayType,
                minutes,
                reasonCode,
                description,
                replannedAt);
    }
}
