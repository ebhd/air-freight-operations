package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOutboxMessageRepository extends JpaRepository<JpaOutboxMessage, UUID> {
    List<JpaOutboxMessage> findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(Pageable pageable);
}
