package be.kdg.airfreight.simulator.uld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class UldPublisherTest {
    private static final UldRequest REQUEST =
            new UldRequest(
                    "HJ221",
                    LocalDate.of(2026, 10, 26),
                    "BRU",
                    "AKE12345KL",
                    new UldWeight(new BigDecimal("1250.5"), "KG"),
                    "AMS");

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final UldPublisher publisher = new UldPublisher(rabbit);

    @Test
    void publishesOneMessageToCargoUldOnDefaultExchange() {
        String messageId = publisher.publish(REQUEST);

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(rabbit)
                .convertAndSend(
                        eq(""), eq("cargo.uld"), sent.capture(), any(MessagePostProcessor.class));
        assertThat(sent.getValue()).isEqualTo(UldMessage.from(messageId, REQUEST));
    }

    @Test
    void usesTheSameIdInPropertyAndBody() {
        String messageId = publisher.publish(REQUEST);

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> properties =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbit)
                .convertAndSend(eq(""), eq("cargo.uld"), sent.capture(), properties.capture());
        // run the post processor on an empty message to see the amqp properties it sets
        Message amqpMessage =
                properties
                        .getValue()
                        .postProcessMessage(new Message(new byte[0], new MessageProperties()));

        assertThat(((UldMessage) sent.getValue()).messageId()).isEqualTo(messageId);
        assertThat(amqpMessage.getMessageProperties().getMessageId()).isEqualTo(messageId);
        assertThat(amqpMessage.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    void generatesAnIdWhenNoneIsGiven() {
        String messageId = publisher.publish(REQUEST);

        assertThatCode(() -> UUID.fromString(messageId)).doesNotThrowAnyException();
    }
}
