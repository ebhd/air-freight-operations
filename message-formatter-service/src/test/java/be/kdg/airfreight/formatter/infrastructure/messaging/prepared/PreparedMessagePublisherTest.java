package be.kdg.airfreight.formatter.infrastructure.messaging.prepared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import be.kdg.airfreight.formatter.domain.MessageContent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

class PreparedMessagePublisherTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final PreparedMessagePublisher publisher = new PreparedMessagePublisher(rabbit, json);
    private final UUID sourceId = UUID.randomUUID();
    private final List<MessageContent> contents =
            List.of(
                    new MessageContent("MVT_ED", "MVT\nHJ221/09.SXBAA.BRU\nED0715", 1),
                    new MessageContent("MVT_EA", "MVT\nHJ221/09.SXBAA.AMS\nEA0915", 1));

    @Test
    void confirmedBatchContainsBothMessagesAndTheSourceId() {
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(3);
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbit)
                .send(
                        eq("formatter.events"),
                        eq("partner-messages.prepared"),
                        any(Message.class),
                        any(CorrelationData.class));

        publisher.publish(sourceId, contents);

        var sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit)
                .send(
                        eq("formatter.events"),
                        eq("partner-messages.prepared"),
                        sent.capture(),
                        any(CorrelationData.class));
        var message = sent.getValue();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(sourceId.toString());
        assertThat(message.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        var batch = json.readValue(message.getBody(), PartnerMessagesPreparedMessage.class);
        assertThat(batch.version()).isEqualTo(1);
        assertThat(batch.sourceMessageId()).isEqualTo(sourceId);
        assertThat(batch.messages())
                .containsExactly(
                        new PartnerMessagesPreparedMessage.PreparedContent(
                                "MVT_ED", 1, contents.get(0).payload()),
                        new PartnerMessagesPreparedMessage.PreparedContent(
                                "MVT_EA", 1, contents.get(1).payload()));
    }

    @Test
    void rejectedBatchThrowsSoTheSourceCanBeRetried() {
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(3);
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(false, "Rejected"));
                            return null;
                        })
                .when(rabbit)
                .send(
                        eq("formatter.events"),
                        eq("partner-messages.prepared"),
                        any(Message.class),
                        any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(sourceId, contents))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("not confirmed and routed");
    }

    @Test
    void returnedBatchIsRefusedEvenWhenTheBrokerAcknowledgesIt() {
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(3);
                            Message message = invocation.getArgument(2);
                            correlation.setReturned(
                                    new ReturnedMessage(
                                            message,
                                            312,
                                            "NO_ROUTE",
                                            "formatter.events",
                                            "partner-messages.prepared"));
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbit)
                .send(
                        eq("formatter.events"),
                        eq("partner-messages.prepared"),
                        any(Message.class),
                        any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(sourceId, contents))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("not confirmed and routed");
    }

    @Test
    void missingConfirmationTimesOutSoTheSourceCanBeRetried() {
        // The mock never completes the confirmation future.
        assertThatThrownBy(() -> publisher.publish(sourceId, contents))
                .isInstanceOf(AmqpException.class)
                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }
}
