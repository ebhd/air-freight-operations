package be.kdg.airfreight.flight.infrastructure.persistence;

import be.kdg.airfreight.flight.domain.delayreason.DelayReason;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaDelayReason;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaDelayReasonRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class DbDelayReasonRepository implements DelayReasonRepository {
    private final JpaDelayReasonRepository delayReasons;

    DbDelayReasonRepository(JpaDelayReasonRepository delayReasons) {
        this.delayReasons = delayReasons;
    }

    @Override
    public Optional<DelayReason> findByCode(String code) {
        return delayReasons.findById(code).map(JpaDelayReason::toDomain);
    }

    @Override
    public List<DelayReason> findAll() {
        return delayReasons.findAllByOrderByCodeAsc().stream()
                .map(JpaDelayReason::toDomain)
                .toList();
    }
}
