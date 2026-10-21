package be.kdg.airfreight.cargo.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.application.BookUldCommand;
import be.kdg.airfreight.cargo.application.BookUldService;
import be.kdg.airfreight.cargo.infrastructure.persistence.MessageReceipts;
import be.kdg.airfreight.cargo.infrastructure.persistence.RejectedMessageStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

class UldRabbitListenerTest {
    private static final String MESSAGE_ID = "uld-0001";

    private final BookUldService bookUldService = mock(BookUldService.class);
    private final RejectedMessageStore rejectedMessageStore = mock(RejectedMessageStore.class);
    private final MessageReceipts messageReceipts = mock(MessageReceipts.class);
    private final UldRabbitListener listener =
            new UldRabbitListener(
                    bookUldService,
                    rejectedMessageStore,
                    messageReceipts,
                    JsonMapper.builder().build());

    @Test
    void booksAVersionOneMessage() {
        when(messageReceipts.firstReceivedAt(eq(MESSAGE_ID), any()))
                .thenReturn(Instant.parse("2026-10-18T10:00:00Z"));

        listener.receive(message(payload("\"version\": 1,")));

        ArgumentCaptor<BookUldCommand> command = ArgumentCaptor.forClass(BookUldCommand.class);
        verify(bookUldService).bookUld(command.capture());
        assertThat(command.getValue().messageId()).isEqualTo(MESSAGE_ID);
        assertThat(command.getValue().scheduledDepartureDay()).isEqualTo(19);
        verify(rejectedMessageStore, never()).store(any(), any(), any());
    }

    @Test
    void rejectsAMessageWithoutVersion() {
        String payload = payload("");

        listener.receive(message(payload));

        verify(rejectedMessageStore)
                .store(MESSAGE_ID, payload, "Message version must be 1, was null");
        verifyNoInteractions(bookUldService, messageReceipts);
    }

    @Test
    void rejectsAnUnknownVersion() {
        String payload = payload("\"version\": 2,");

        listener.receive(message(payload));

        verify(rejectedMessageStore).store(MESSAGE_ID, payload, "Message version must be 1, was 2");
        verifyNoInteractions(bookUldService, messageReceipts);
    }

    private static Message message(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(MESSAGE_ID);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static String payload(String versionField) {
        return """
                {"messageId": "uld-0001", %s "flightNumber": "HJ221", "scheduledDepartureDay": 19,
                 "departureAirportCode": "BRU", "uldCode": "AKE12345KL",
                 "weight": {"value": 1250.5, "unit": "KG"}, "destination": "AMS"}
                """
                .formatted(versionField);
    }
}
