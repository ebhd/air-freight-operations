package be.kdg.airfreight.partner.application;

import be.kdg.airfreight.partner.domain.MessageContent;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PartnerMessageRepository;
import java.time.Instant;
import java.util.List;
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
    private final ApplicationEventPublisher events;

    public PartnerMessageService(
            PartnerMessageRepository messages, ApplicationEventPublisher events) {
        this.messages = messages;
        this.events = events;
    }

    @Transactional
    public void prepareMessages(UUID sourceMessageId, List<MessageContent> contents) {
        Objects.requireNonNull(sourceMessageId, "source message ID must not be null");
        for (var content : contents) {
            prepare(sourceMessageId, content);
        }
    }

    @Transactional(readOnly = true)
    public List<PartnerMessage> findLatest(int limit) {
        return messages.findLatest(limit);
    }

    private PartnerMessage prepare(UUID sourceMessageId, MessageContent content) {
        var existing = messages.findBySourceMessageIdAndType(sourceMessageId, content.type());
        if (existing.isPresent()) {
            log.warn(
                    "Skipping duplicate source message {} for {}", sourceMessageId, content.type());
            return existing.get();
        }

        var message = PartnerMessage.create(sourceMessageId, Instant.now(), content);
        var saved = messages.save(message);
        events.publishEvent(saved);
        log.info(
                "Prepared {} {} from source {}",
                content.type(),
                saved.id().value(),
                sourceMessageId);
        return saved;
    }
}
