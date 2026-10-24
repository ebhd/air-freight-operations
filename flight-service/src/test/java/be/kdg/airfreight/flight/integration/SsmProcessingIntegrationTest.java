package be.kdg.airfreight.flight.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import be.kdg.airfreight.flight.infrastructure.messaging.SsmMessage;
import be.kdg.airfreight.flight.infrastructure.messaging.SsmRabbitListener;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightRepository;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class SsmProcessingIntegrationTest {
    @Autowired private SsmRabbitListener listener;
    @Autowired private JpaFlightRepository flights;
    @Autowired private JpaOutboxMessageRepository outbox;
    @Autowired private JsonMapper json;

    @MockitoBean private RabbitTemplate rabbit;

    // Keep the real after-commit publisher, but do not run background polling in tests.
    @MockitoBean(
            name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    private ScheduledAnnotationBeanPostProcessor scheduling;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        flights.deleteAll();
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
                        eq("flight.events"),
                        eq("flight.created"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void newSsmPersistsFlightsAndPublishesOneMessagePerFlight() {
        receive("first", "2026-10-20", "2026-10-21", "AMS");

        assertThat(flights.count()).isEqualTo(2);
        assertThat(messages())
                .extracting(message -> message.get("scheduledDepartureDate").asText())
                .containsExactlyInAnyOrder("2026-10-20", "2026-10-21");
        assertThat(outbox.findAll())
                .allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());
        verify(rabbit, times(2))
                .send(
                        eq("flight.events"),
                        eq("flight.created"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void repeatedSsmDoesNotCreateMoreFlightsOrMessages() {
        receive("same-message", "2026-10-20", "2026-10-21", "AMS");
        var originalIds = outbox.findAll().stream().map(row -> row.getId()).toList();

        receive("same-message", "2026-10-20", "2026-10-21", "AMS");

        assertThat(flights.count()).isEqualTo(2);
        assertThat(outbox.findAll())
                .extracting(row -> row.getId())
                .containsExactlyInAnyOrderElementsOf(originalIds);
        verify(rabbit, times(2))
                .send(
                        eq("flight.events"),
                        eq("flight.created"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void overlappingSsmPreservesExistingFlightsAndOnlyPublishesNewDates() {
        receive("first", "2026-10-20", "2026-10-21", "AMS");
        receive("overlap", "2026-10-21", "2026-10-22", "LHR");

        assertThat(flights.findAll())
                .allSatisfy(
                        row -> {
                            var flight = row.toDomain();
                            String expected =
                                    flight.key()
                                                    .scheduledDepartureDate()
                                                    .equals(LocalDate.parse("2026-10-22"))
                                            ? "LHR"
                                            : "AMS";
                            assertThat(flight.arrivalAirportCode()).isEqualTo(expected);
                        });
        assertThat(flights.count()).isEqualTo(3);
        assertThat(messages())
                .extracting(
                        message ->
                                message.get("scheduledDepartureDate").asText()
                                        + "/"
                                        + message.get("arrivalAirportCode").asText())
                .containsExactlyInAnyOrder("2026-10-20/AMS", "2026-10-21/AMS", "2026-10-22/LHR");
        verify(rabbit, times(3))
                .send(
                        eq("flight.events"),
                        eq("flight.created"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    private List<JsonNode> messages() {
        return outbox.findAll().stream().map(row -> json.readTree(row.getPayload())).toList();
    }

    private void receive(String messageId, String start, String end, String arrival) {
        String body =
                """
                {"messageId":"%s","startDate":"%s","endDate":"%s",
                 "operatingDays":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],
                 "departureTime":"08:00:00","arrivalTime":"10:00:00",
                 "flightNumber":"HJ221","departureAirportCode":"BRU","arrivalAirportCode":"%s",
                 "aircraftRegistration":"SXBAA"}
                """
                        .formatted(messageId, start, end, arrival);
        listener.receive(json.readValue(body, SsmMessage.class));
    }
}
