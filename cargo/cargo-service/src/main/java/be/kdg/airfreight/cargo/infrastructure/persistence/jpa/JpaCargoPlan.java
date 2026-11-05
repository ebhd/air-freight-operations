package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

// the plan row only holds the version that guards the whole aggregate
@Entity
@Table(name = "cargo_plans")
public class JpaCargoPlan {
    @EmbeddedId private JpaCargoPlanId id;

    // null means never saved so spring data inserts the row instead of merging it
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected JpaCargoPlan() {}

    public JpaCargoPlan(JpaCargoPlanId id) {
        this.id = id;
    }
}
