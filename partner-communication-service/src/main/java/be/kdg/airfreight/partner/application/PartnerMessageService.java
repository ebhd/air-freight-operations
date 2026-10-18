package be.kdg.airfreight.partner.application;

import be.kdg.airfreight.partner.domain.FlightCreatedFact;
import be.kdg.airfreight.partner.domain.MessageFormatter;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PartnerMessageRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerMessageService {
    private static final Logger log = LoggerFactory.getLogger(PartnerMessageService.class);
    private final PartnerMessageRepository messages;
    private final MessageFormatter<FlightCreatedFact> flmFormatter;

    public PartnerMessageService(
            PartnerMessageRepository messages, MessageFormatter<FlightCreatedFact> flmFormatter) {
        this.messages = messages;
        this.flmFormatter = flmFormatter;
    }

    @Transactional
    public PartnerMessage prepareFlm(UUID sourceMessageId, FlightCreatedFact flightFact) {
        Objects.requireNonNull(sourceMessageId, "source message ID must not be null");
        Objects.requireNonNull(flightFact, "flight fact must not be null");
        var content = flmFormatter.format(flightFact);
        var existing = messages.findBySourceMessageIdAndType(sourceMessageId, content.type());
        if (existing.isPresent()) {
            log.warn("Skipping duplicate flight-created message {}", sourceMessageId);
            return existing.get();
        }

        var message = PartnerMessage.create(sourceMessageId, Instant.now(), content);
        var saved = messages.save(message);
        log.info("Prepared FLM {} from source {}", saved.id().value(), sourceMessageId);
        return saved;
    }
}
