package be.kdg.airfreight.formatter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.formatter.infrastructure.messaging.FlightCreatedListener;
import be.kdg.airfreight.formatter.infrastructure.messaging.FlightCreatedMessage;
import be.kdg.airfreight.formatter.infrastructure.messaging.PartnerMessagesPreparedMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class FlmFormattingIntegrationTest {
    @Autowired private FlightCreatedListener listener;
    @Autowired private JsonMapper json;
    @MockitoBean private RabbitTemplate rabbit;

    @BeforeEach
    void confirmPublication() {
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
    }

    @Test
    void flightCreatedIsFormattedAndPublishedWithEveryRequiredField() {
        var source = flightCreated(1);

        listener.receive(source);

        var sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit)
                .send(
                        eq("formatter.events"),
                        eq("partner-messages.prepared"),
                        sent.capture(),
                        any(CorrelationData.class));
        var batch = json.readValue(sent.getValue().getBody(), PartnerMessagesPreparedMessage.class);
        assertThat(batch.sourceMessageId()).isEqualTo(source.messageId());
        assertThat(batch.version()).isEqualTo(1);
        assertThat(batch.messages()).hasSize(1);
        var flm = batch.messages().getFirst();
        assertThat(flm.type()).isEqualTo("FLM");
        assertThat(flm.formatVersion()).isEqualTo(1);
        var payload = json.readTree(flm.payload());
        assertThat(payload.get("flightNumber").asText()).isEqualTo("HJ221");
        assertThat(payload.get("scheduledDepartureDate").asText()).isEqualTo("2026-10-20");
        assertThat(payload.get("departureAirportCode").asText()).isEqualTo("BRU");
        assertThat(payload.get("arrivalAirportCode").asText()).isEqualTo("AMS");
        assertThat(Instant.parse(payload.get("scheduledDeparture").asText()))
                .isEqualTo(source.scheduledDeparture());
        assertThat(Instant.parse(payload.get("scheduledArrival").asText()))
                .isEqualTo(source.scheduledArrival());
    }

    @Test
    void unsupportedFlightCreatedVersionPublishesNothing() {
        assertThatThrownBy(() -> listener.receive(flightCreated(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported FlightCreated version");
        verifyNoInteractions(rabbit);
    }

    private FlightCreatedMessage flightCreated(int version) {
        return new FlightCreatedMessage(
                UUID.randomUUID(),
                version,
                Instant.parse("2026-10-19T12:00:00Z"),
                "HJ221",
                LocalDate.of(2026, 10, 20),
                "BRU",
                "AMS",
                Instant.parse("2026-10-20T08:00:00Z"),
                Instant.parse("2026-10-20T10:00:00Z"));
    }
}
