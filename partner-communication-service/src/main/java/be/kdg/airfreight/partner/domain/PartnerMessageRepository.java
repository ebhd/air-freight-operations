package be.kdg.airfreight.partner.domain;

import java.util.Optional;
import java.util.UUID;

public interface PartnerMessageRepository {
    Optional<PartnerMessage> findBySourceMessageIdAndType(UUID sourceMessageId, String type);

    PartnerMessage save(PartnerMessage message);
}
