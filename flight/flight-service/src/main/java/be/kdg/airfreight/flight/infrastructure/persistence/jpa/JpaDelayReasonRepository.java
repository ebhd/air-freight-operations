package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDelayReasonRepository extends JpaRepository<JpaDelayReason, String> {
    List<JpaDelayReason> findAllByOrderByCodeAsc();
}
