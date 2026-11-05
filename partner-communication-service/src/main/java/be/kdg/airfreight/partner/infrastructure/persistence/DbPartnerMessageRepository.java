package be.kdg.airfreight.partner.infrastructure.persistence;

import be.kdg.airfreight.partner.domain.MessageId;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PartnerMessageRepository;
import be.kdg.airfreight.partner.domain.PublicationState;
import be.kdg.airfreight.partner.infrastructure.persistence.jpa.JpaPartnerMessage;
import be.kdg.airfreight.partner.infrastructure.persistence.jpa.JpaPartnerMessageRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class DbPartnerMessageRepository implements PartnerMessageRepository {
    private final JpaPartnerMessageRepository messages;

    DbPartnerMessageRepository(JpaPartnerMessageRepository messages) {
        this.messages = messages;
    }

    @Override
    public Optional<PartnerMessage> findById(MessageId id) {
        return messages.findById(id.value()).map(JpaPartnerMessage::toDomain);
    }

    @Override
    public List<PartnerMessage> findPending(int limit) {
        return messages
                .findByStateOrderByCreatedAtUTCAscIdAsc(
                        PublicationState.PENDING, PageRequest.of(0, limit))
                .stream()
                .map(JpaPartnerMessage::toDomain)
                .toList();
    }

    @Override
    public List<PartnerMessage> findLatest(int limit) {
        return messages.findAllByOrderByCreatedAtUTCDescIdDesc(PageRequest.of(0, limit)).stream()
                .map(JpaPartnerMessage::toDomain)
                .toList();
    }

    @Override
    public Optional<PartnerMessage> findBySourceMessageIdAndType(
            UUID sourceMessageId, String type) {
        return messages.findBySourceMessageIdAndType(sourceMessageId, type)
                .map(JpaPartnerMessage::toDomain);
    }

    @Override
    public PartnerMessage save(PartnerMessage message) {
        return messages.save(JpaPartnerMessage.fromDomain(message)).toDomain();
    }
}
