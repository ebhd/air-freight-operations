package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRejectedMessageRepository extends JpaRepository<JpaRejectedMessage, Long> {
    boolean existsByMessageId(String messageId);
}
