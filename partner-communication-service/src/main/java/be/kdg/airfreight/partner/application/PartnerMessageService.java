package be.kdg.airfreight.partner.application;

import be.kdg.airfreight.partner.domain.FlightCreatedFact;
import be.kdg.airfreight.partner.domain.MessageFormatter;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PartnerMessageRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PartnerMessageService {
    private final PartnerMessageRepository messages;
    private final MessageFormatter<FlightCreatedFact> flmFormatter;
    private final ApplicationEventPublisher events;

    public PartnerMessageService(
            PartnerMessageRepository messages,
            MessageFormatter<FlightCreatedFact> flmFormatter,
            ApplicationEventPublisher events) {
        this.messages = messages;
        this.flmFormatter = flmFormatter;
        this.events = events;
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
        events.publishEvent(saved);
        log.info("Prepared FLM {} from source {}", saved.id().value(), sourceMessageId);
        return saved;
    }
}
