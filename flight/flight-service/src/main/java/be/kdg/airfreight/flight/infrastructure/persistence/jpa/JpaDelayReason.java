package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.domain.delayreason.DelayReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "delay_reasons")
public class JpaDelayReason {
    @Id
    @Column(length = 16)
    private String code;

    @Column(nullable = false, length = 255)
    private String label;

    protected JpaDelayReason() {}

    public DelayReason toDomain() {
        return new DelayReason(code, label);
    }
}
