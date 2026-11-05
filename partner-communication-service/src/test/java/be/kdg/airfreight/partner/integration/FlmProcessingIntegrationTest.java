package be.kdg.airfreight.partner.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import be.kdg.airfreight.partner.domain.PublicationState;
import be.kdg.airfreight.partner.infrastructure.messaging.FlightCreatedListener;
import be.kdg.airfreight.partner.infrastructure.messaging.FlightCreatedMessage;
import be.kdg.airfreight.partner.infrastructure.persistence.jpa.JpaPartnerMessageRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class FlmProcessingIntegrationTest {
    @Autowired private FlightCreatedListener listener;
    @Autowired private JpaPartnerMessageRepository messages;
    @Autowired private JsonMapper json;

    @MockitoBean private RabbitTemplate rabbit;

    // Keep the real after-commit publisher, but do not run background polling in tests.
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
                        eq("flm"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void flightCreatedPersistsAndPublishesACompleteFlm() {
        var incoming = flightCreated();
        listener.receive(incoming);

        assertThat(messages.count()).isEqualTo(1);
        var saved = messages.findAll().getFirst().toDomain();
        assertThat(saved.sourceMessageId()).isEqualTo(incoming.messageId());
        assertThat(saved.state()).isEqualTo(PublicationState.PUBLISHED);

        var sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit)
                .send(
                        eq("partner.messages"),
                        eq("flm"),
                        sent.capture(),
                        any(CorrelationData.class));
        var properties = sent.getValue().getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(saved.id().value().toString());
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getContentType()).isEqualTo("application/json");

        var body = json.readTree(sent.getValue().getBody());
        assertThat(body.get("messageId").asText()).isEqualTo(saved.id().value().toString());
        assertThat(Instant.parse(body.get("createdAt").asText())).isEqualTo(saved.createdAtUTC());
        assertThat(body.get("type").asText()).isEqualTo("FLM");
        assertThat(body.get("formatVersion").asInt()).isEqualTo(1);
        var payload = body.get("payload");
        assertThat(payload).isEqualTo(json.readTree(saved.content().payload()));
        assertThat(payload.get("flightNumber").asText()).isEqualTo("HJ221");
        assertThat(payload.get("scheduledDepartureDate").asText()).isEqualTo("2026-10-20");
        assertThat(payload.get("departureAirportCode").asText()).isEqualTo("BRU");
        assertThat(payload.get("arrivalAirportCode").asText()).isEqualTo("AMS");
        assertThat(Instant.parse(payload.get("scheduledDeparture").asText()))
                .isEqualTo(incoming.scheduledDeparture());
        assertThat(Instant.parse(payload.get("scheduledArrival").asText()))
                .isEqualTo(incoming.scheduledArrival());
    }

    @Test
    void redeliveredFlightCreatedDoesNotCreateOrPublishAnotherFlm() {
        var incoming = flightCreated();
        listener.receive(incoming);
        var original = messages.findAll().getFirst().toDomain();

        listener.receive(incoming);

        assertThat(messages.count()).isEqualTo(1);
        var saved = messages.findAll().getFirst().toDomain();
        assertThat(saved.id()).isEqualTo(original.id());
        assertThat(saved.createdAtUTC()).isEqualTo(original.createdAtUTC());
        assertThat(saved.content()).isEqualTo(original.content());
        assertThat(saved.state()).isEqualTo(PublicationState.PUBLISHED);
        verify(rabbit)
                .send(
                        eq("partner.messages"),
                        eq("flm"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    private FlightCreatedMessage flightCreated() {
        String body =
                """
                {"messageId":"%s","version":1,"occurredOn":"2026-10-19T12:00:00Z",
                 "flightNumber":"HJ221","scheduledDepartureDate":"2026-10-20",
                 "departureAirportCode":"BRU","arrivalAirportCode":"AMS",
                 "scheduledDeparture":"2026-10-20T08:00:00Z",
                 "scheduledArrival":"2026-10-20T10:00:00Z"}
                """
                        .formatted(UUID.randomUUID());
        return json.readValue(body, FlightCreatedMessage.class);
    }
}
