package be.kdg.airfreight.formatter.domain;

import java.util.List;
import java.util.UUID;

public interface MessagePublisher {
    void publish(UUID sourceMessageId, List<MessageContent> contents);
}
