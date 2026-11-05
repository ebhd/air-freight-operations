package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanState;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

// the plan row holds the state and the version that guards the whole aggregate
@Entity
@Table(name = "cargo_plans")
public class JpaCargoPlan {
    @EmbeddedId private JpaCargoPlanId id;

    // null means never saved so spring data inserts the row instead of merging it
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private CargoPlanState state;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    protected JpaCargoPlan() {}

    public JpaCargoPlan(JpaCargoPlanId id, CargoPlanState state, Instant finalizedAt) {
        this.id = id;
        this.state = state;
        this.finalizedAt = finalizedAt;
    }

    public CargoPlanState getState() {
        return state;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    // a managed entity so hibernate writes the change at commit
    public void changeState(CargoPlanState state, Instant finalizedAt) {
        this.state = state;
        this.finalizedAt = finalizedAt;
    }
}
