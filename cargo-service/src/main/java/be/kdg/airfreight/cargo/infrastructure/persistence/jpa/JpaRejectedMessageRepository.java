package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRejectedMessageRepository extends JpaRepository<JpaRejectedMessage, Long> {
    boolean existsByMessageId(String messageId);
}
