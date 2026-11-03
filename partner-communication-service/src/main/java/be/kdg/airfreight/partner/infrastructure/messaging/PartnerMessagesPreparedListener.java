package be.kdg.airfreight.partner.infrastructure.messaging;

import be.kdg.airfreight.partner.application.PartnerMessageService;
import be.kdg.airfreight.partner.domain.MessageContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PartnerMessagesPreparedListener {
    private final PartnerMessageService service;
    private final JsonMapper json;

    public PartnerMessagesPreparedListener(PartnerMessageService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @RabbitListener(queues = RabbitConfiguration.PREPARED_QUEUE)
    public void receive(PartnerMessagesPreparedMessage batch) {
        if (batch.version() != PartnerMessagesPreparedMessage.VERSION
                || batch.sourceMessageId() == null
                || batch.messages() == null
                || batch.messages().isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid prepared-message batch or unsupported version");
        }
        var types = new HashSet<String>();
        var contents = new ArrayList<MessageContent>();
        for (var item : batch.messages()) {
            if (item == null) {
                throw new IllegalArgumentException("Prepared content must not be null");
            }
            var content = new MessageContent(item.type(), item.payload(), item.formatVersion());
            if (!Set.of("FLM", "MVT_ED", "MVT_EA").contains(content.type())
                    || !types.add(content.type())) {
                throw new IllegalArgumentException(
                        "Unsupported or repeated prepared type: " + content.type());
            }
            if ("FLM".equals(content.type()) && !json.readTree(content.payload()).isObject()) {
                throw new IllegalArgumentException("FLM payload must be a JSON object");
            }
            contents.add(content);
        }
        try {
            service.prepareMessages(batch.sourceMessageId(), contents);
        } catch (DataIntegrityViolationException exception) {
            // A concurrent insert can win after our lookup. Retry outside the failed transaction.
            if (!isDuplicateSource(exception)) {
                throw exception;
            }
            service.prepareMessages(batch.sourceMessageId(), contents);
        }
    }

    private boolean isDuplicateSource(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return "partner_messages_source_type_unique".equals(violation.getConstraintName());
            }
        }
        return false;
    }
}
