package be.kdg.airfreight.partner.infrastructure.persistence.jpa;

import be.kdg.airfreight.partner.domain.PublicationState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPartnerMessageRepository extends JpaRepository<JpaPartnerMessage, UUID> {
    List<JpaPartnerMessage> findByStateOrderByCreatedAtUTCAscIdAsc(
            PublicationState state, Pageable pageable);

    Optional<JpaPartnerMessage> findBySourceMessageIdAndType(UUID sourceMessageId, String type);
}
