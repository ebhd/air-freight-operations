package be.kdg.airfreight.partner.application;

import be.kdg.airfreight.partner.domain.FlightCreatedFact;
import be.kdg.airfreight.partner.domain.FlightEstimatesChangedFact;
import be.kdg.airfreight.partner.domain.MessageContent;
import be.kdg.airfreight.partner.domain.MessageFormatter;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PartnerMessageRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PartnerMessageService {
    private final PartnerMessageRepository messages;
    private final MessageFormatter<FlightCreatedFact> flmFormatter;
    private final MessageFormatter<FlightEstimatesChangedFact> edFormatter;
    private final MessageFormatter<FlightEstimatesChangedFact> eaFormatter;
    private final ApplicationEventPublisher events;

    public PartnerMessageService(
            PartnerMessageRepository messages,
            MessageFormatter<FlightCreatedFact> flmFormatter,
            @Qualifier("mvtEdFormatter") MessageFormatter<FlightEstimatesChangedFact> edFormatter,
            @Qualifier("mvtEaFormatter") MessageFormatter<FlightEstimatesChangedFact> eaFormatter,
            ApplicationEventPublisher events) {
        this.messages = messages;
        this.flmFormatter = flmFormatter;
        this.edFormatter = edFormatter;
        this.eaFormatter = eaFormatter;
        this.events = events;
    }

    @Transactional
    public PartnerMessage prepareFlm(UUID sourceMessageId, FlightCreatedFact flightFact) {
        Objects.requireNonNull(sourceMessageId, "source message ID must not be null");
        Objects.requireNonNull(flightFact, "flight fact must not be null");

        var content = flmFormatter.format(flightFact);
        return prepare(sourceMessageId, content);
    }

    @Transactional
    public void prepareEstimates(UUID sourceMessageId, FlightEstimatesChangedFact flightFact) {
        Objects.requireNonNull(sourceMessageId, "source message ID must not be null");
        Objects.requireNonNull(flightFact, "flight fact must not be null");
        if (flightFact.departureChanged()) {
            prepare(sourceMessageId, edFormatter.format(flightFact));
        }
        if (flightFact.departureChanged() || flightFact.arrivalChanged()) {
            prepare(sourceMessageId, eaFormatter.format(flightFact));
        }
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
