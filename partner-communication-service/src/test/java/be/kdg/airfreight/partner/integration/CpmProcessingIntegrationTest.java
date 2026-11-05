package be.kdg.airfreight.partner.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import be.kdg.airfreight.partner.domain.PublicationState;
import be.kdg.airfreight.partner.infrastructure.messaging.PartnerMessagesPreparedListener;
import be.kdg.airfreight.partner.infrastructure.messaging.PartnerMessagesPreparedMessage;
import be.kdg.airfreight.partner.infrastructure.persistence.jpa.JpaPartnerMessageRepository;
import java.time.Instant;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles({"test", "h2"})
class CpmProcessingIntegrationTest {
    private static final String CPM =
            """
            {"flightNumber":"HJ221","scheduledDepartureDate":"2026-11-20",
             "departureAirportCode":"BRU",
             "ulds":[{"uldCode":"AKE12345HJ","position":"CL",
                      "weight":{"value":1250.0,"unit":"KG"},"destinationAirportCode":"AMS"}]}
            """;

    @Autowired private PartnerMessagesPreparedListener listener;
    @Autowired private JpaPartnerMessageRepository messages;
    @Autowired private JsonMapper json;

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
                        eq("cpm"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void cpmIsStoredAndPublishedWithRoutingKeyCpmInAJsonEnvelope() {
        var batch = batch(UUID.randomUUID(), CPM);

        listener.receive(batch);

        var saved = messages.findAll().getFirst().toDomain();
        assertThat(saved.sourceMessageId()).isEqualTo(batch.sourceMessageId());
        assertThat(saved.state()).isEqualTo(PublicationState.PUBLISHED);
        var sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit)
                .send(
                        eq("partner.messages"),
                        eq("cpm"),
                        sent.capture(),
                        any(CorrelationData.class));
        assertThat(sent.getValue().getMessageProperties().getMessageId())
                .isEqualTo(saved.id().value().toString());
        assertThat(sent.getValue().getMessageProperties().getContentType())
                .isEqualTo("application/json");
        var body = json.readTree(sent.getValue().getBody());
        assertThat(body.get("messageId").asString()).isEqualTo(saved.id().value().toString());
        assertThat(Instant.parse(body.get("createdAt").asString())).isEqualTo(saved.createdAtUTC());
        assertThat(body.get("type").asString()).isEqualTo("CPM");
        assertThat(body.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(body.get("payload")).isEqualTo(json.readTree(CPM));
    }

    // the formatter sends the same batch again after a redelivery of the cargo event
    @Test
    void redeliveredCpmIsStoredAndSentOnce() {
        var batch = batch(UUID.randomUUID(), CPM);
        listener.receive(batch);

        listener.receive(batch);

        assertThat(messages.count()).isEqualTo(1);
        verify(rabbit)
                .send(
                        eq("partner.messages"),
                        eq("cpm"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void cpmThatIsNoJsonObjectIsRejected() {
        assertThatThrownBy(() -> listener.receive(batch(UUID.randomUUID(), "[1, 2]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CPM payload must be a JSON object");
        assertThat(messages.count()).isZero();
    }

    @Test
    void unknownTypeIsStillRejected() {
        var batch =
                new PartnerMessagesPreparedMessage(
                        1,
                        UUID.randomUUID(),
                        List.of(
                                new PartnerMessagesPreparedMessage.PreparedContent(
                                        "XYZ", 1, "{}")));

        assertThatThrownBy(() -> listener.receive(batch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported or repeated prepared type: XYZ");
        assertThat(messages.count()).isZero();
    }

    private static PartnerMessagesPreparedMessage batch(UUID sourceId, String payload) {
        return new PartnerMessagesPreparedMessage(
                1,
                sourceId,
                List.of(new PartnerMessagesPreparedMessage.PreparedContent("CPM", 1, payload)));
    }
}
