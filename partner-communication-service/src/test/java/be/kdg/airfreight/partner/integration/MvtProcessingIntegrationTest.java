package be.kdg.airfreight.partner.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import be.kdg.airfreight.partner.domain.PublicationState;
import be.kdg.airfreight.partner.infrastructure.messaging.PartnerMessagesPreparedListener;
import be.kdg.airfreight.partner.infrastructure.messaging.PartnerMessagesPreparedMessage;
import be.kdg.airfreight.partner.infrastructure.persistence.jpa.JpaPartnerMessageRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// the ad and aa telegrams from the formatter go out as text with their own routing key
@SpringBootTest
@ActiveProfiles({"test", "h2"})
class MvtProcessingIntegrationTest {
    private static final String AD = "MVT\nHJ221/09.SXBAA.BRU\nAD0718";
    private static final String AA = "MVT\nHJ221/09.SXBAA.AMS\nAA0921";

    @Autowired private PartnerMessagesPreparedListener listener;
    @Autowired private JpaPartnerMessageRepository messages;

    @MockitoBean private RabbitTemplate rabbit;

    // keep the real after-commit publisher, but do not run background polling in tests
    @MockitoBean(
            name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    private ScheduledAnnotationBeanPostProcessor scheduling;

    @BeforeEach
    void setUp() {
        messages.deleteAll();
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
                        eq("partner.messages"),
                        any(String.class),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void adIsStoredAndPublishedAsTextWithRoutingKeyMvtAd() {
        var batch = batch(UUID.randomUUID(), "MVT_AD", AD);

        listener.receive(batch);

        var saved = messages.findAll().getFirst().toDomain();
        assertThat(saved.sourceMessageId()).isEqualTo(batch.sourceMessageId());
        assertThat(saved.state()).isEqualTo(PublicationState.PUBLISHED);
        var sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit)
                .send(
                        eq("partner.messages"),
                        eq("mvt.ad"),
                        sent.capture(),
                        any(CorrelationData.class));
        var properties = sent.getValue().getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(saved.id().value().toString());
        assertThat(properties.getContentType()).isEqualTo("text/plain");
        assertThat(properties.getType()).isEqualTo("MVT_AD");
        assertThat(properties.getHeaders()).containsEntry("formatVersion", 1);
        assertThat(new String(sent.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo(AD);
    }

    @Test
    void aaGoesOutWithRoutingKeyMvtAa() {
        listener.receive(batch(UUID.randomUUID(), "MVT_AA", AA));

        verify(rabbit)
                .send(
                        eq("partner.messages"),
                        eq("mvt.aa"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    // the outbox of flight can deliver the same event twice, partner keeps one ad per source
    @Test
    void redeliveredAdIsStoredAndSentOnce() {
        var batch = batch(UUID.randomUUID(), "MVT_AD", AD);
        listener.receive(batch);

        listener.receive(batch);

        assertThat(messages.count()).isEqualTo(1);
        verify(rabbit)
                .send(
                        eq("partner.messages"),
                        eq("mvt.ad"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    // the first moment is the reference for the mvt time, so a redelivery must not move it
    @Test
    void redeliveredAdKeepsItsFirstCreationTime() {
        var batch = batch(UUID.randomUUID(), "MVT_AD", AD);
        listener.receive(batch);
        var firstCreatedAt = messages.findAll().getFirst().toDomain().createdAtUTC();

        listener.receive(batch);

        var createdAt = messages.findAll().getFirst().toDomain().createdAtUTC();
        assertThat(createdAt).isEqualTo(firstCreatedAt);
    }

    private static PartnerMessagesPreparedMessage batch(UUID sourceId, String type, String text) {
        return new PartnerMessagesPreparedMessage(
                1,
                sourceId,
                List.of(new PartnerMessagesPreparedMessage.PreparedContent(type, 1, text)));
    }
}
