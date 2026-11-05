package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface JpaOutboxMessageRepository extends JpaRepository<JpaOutboxMessage, UUID> {
    // oldest first so events leave in the order they happened
    List<JpaOutboxMessage> findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(Pageable pageable);

    // a pending message has no published_at, so this never deletes an undelivered message
    @Transactional
    @Modifying
    @Query("delete from JpaOutboxMessage message where message.publishedAt < :before")
    int deletePublishedBefore(Instant before);
}
