package be.kdg.airfreight.partner.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerMessageRepository {
    Optional<PartnerMessage> findById(MessageId id);

    List<PartnerMessage> findPending(int limit);

    List<PartnerMessage> findLatest(int limit);

    Optional<PartnerMessage> findBySourceMessageIdAndType(UUID sourceMessageId, String type);

    PartnerMessage save(PartnerMessage message);
}
