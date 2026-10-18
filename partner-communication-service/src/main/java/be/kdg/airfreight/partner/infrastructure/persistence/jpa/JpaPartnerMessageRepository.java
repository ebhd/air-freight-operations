package be.kdg.airfreight.partner.infrastructure.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPartnerMessageRepository extends JpaRepository<JpaPartnerMessage, UUID> {
    Optional<JpaPartnerMessage> findBySourceMessageIdAndType(UUID sourceMessageId, String type);
}
