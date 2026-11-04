package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOutboxMessageRepository extends JpaRepository<JpaOutboxMessage, UUID> {
    // oldest first so events leave in the order they happened
    List<JpaOutboxMessage> findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(Pageable pageable);
}
