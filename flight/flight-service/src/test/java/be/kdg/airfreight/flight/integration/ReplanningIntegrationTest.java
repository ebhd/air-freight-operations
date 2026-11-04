package be.kdg.airfreight.flight.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import be.kdg.airfreight.flight.application.FlightReplanningService;
import be.kdg.airfreight.flight.application.command.ReplanFlightCommand;
import be.kdg.airfreight.flight.domain.delayreason.DelayReason;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.DelayType;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import be.kdg.airfreight.flight.infrastructure.messaging.ssm.SsmMessage;
import be.kdg.airfreight.flight.infrastructure.messaging.ssm.SsmRabbitListener;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlight;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightId;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightRepository;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessage;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// the replan transaction: the flight columns and the outbox row commit together
@SpringBootTest
@ActiveProfiles("test")
class ReplanningIntegrationTest {
    private static final FlightKey KEY = new FlightKey("HJ500", LocalDate.of(2026, 11, 10), "BRU");

    @Autowired private FlightReplanningService replanningService;
    @Autowired private SsmRabbitListener ssmListener;
    @Autowired private JpaFlightRepository flights;
    @MockitoSpyBean private DelayReasonRepository delayReasons;
    @Autowired private JpaOutboxMessageRepository outbox;
    @Autowired private JsonMapper json;

    @MockitoBean private RabbitTemplate rabbit;

    // no background polling in tests
    @MockitoBean(
            name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    private ScheduledAnnotationBeanPostProcessor scheduling;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        flights.deleteAll();
        // the reason table is empty in the h2 test database
        doReturn(Optional.of(new DelayReason("93", "Aircraft defects")))
                .when(delayReasons)
                .findByCode("93");
        flights.save(
                JpaFlight.fromDomain(
                        Flight.restore(
                                KEY,
                                "AMS",
                                "SXBAA",
                                Instant.parse("2026-11-10T20:00:00Z"),
                                Instant.parse("2026-11-10T22:00:00Z"),
                                FlightStatus.PLANNED,
                                List.of(Delay.restore(UUID.randomUUID(), 400, "71", "Fog")),
                                List.of(Delay.restore(UUID.randomUUID(), 200, "71", "Holding")))));
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(3);
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbit)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void replanStoresTheReplanningAndOneOutboxMessage() {
        replanningService.replan(
                new ReplanFlightCommand(
                        KEY, DelayType.DEPARTURE, null, 140, "93", "Hydraulic leak"));

        Flight stored = flights.findById(JpaFlightId.fromDomain(KEY)).orElseThrow().toDomain();
        assertThat(stored.status()).isEqualTo(FlightStatus.REPLANNED);
        assertThat(stored.replacementCreated()).isFalse();
        assertThat(stored.replaces()).isEmpty();
        var replanning = stored.replanning().orElseThrow();
        assertThat(replanning.newFlightNumber()).isEqualTo("HJ500A");
        assertThat(replanning.newScheduledDeparture())
                .isEqualTo(Instant.parse("2026-11-11T05:00:00Z"));
        assertThat(replanning.newScheduledArrival())
                .isEqualTo(Instant.parse("2026-11-11T10:20:00Z"));
        assertThat(replanning.delayType()).isEqualTo(DelayType.DEPARTURE);
        assertThat(replanning.minutes()).isEqualTo(140);
        assertThat(replanning.reasonCode()).isEqualTo("93");
        assertThat(replanning.description()).isEqualTo("Hydraulic leak");
        assertThat(stored.departureDelays()).hasSize(1);

        List<JpaOutboxMessage> rows = outbox.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getRoutingKey()).isEqualTo("flight.replanned");
        assertThat(rows.getFirst().getPublishedAt()).isNotNull();
        JsonNode message = json.readTree(rows.getFirst().getPayload());
        assertThat(message.get("version").asInt()).isEqualTo(1);
        assertThat(message.get("messageId").asText()).isEqualTo(rows.getFirst().getId().toString());
        assertThat(message.get("flightNumber").asText()).isEqualTo("HJ500");
        assertThat(message.get("scheduledDepartureDate").asText()).isEqualTo("2026-11-10");
        assertThat(message.get("departureAirportCode").asText()).isEqualTo("BRU");
        assertThat(message.get("arrivalAirportCode").asText()).isEqualTo("AMS");
        assertThat(message.get("aircraftRegistration").asText()).isEqualTo("SXBAA");
        assertThat(message.get("scheduledDeparture").asText()).isEqualTo("2026-11-10T20:00:00Z");
        assertThat(message.get("scheduledArrival").asText()).isEqualTo("2026-11-10T22:00:00Z");
        assertThat(message.get("newFlightNumber").asText()).isEqualTo("HJ500A");
        assertThat(message.get("newScheduledDepartureDate").asText()).isEqualTo("2026-11-11");
        assertThat(message.get("newScheduledDeparture").asText()).isEqualTo("2026-11-11T05:00:00Z");
        assertThat(message.get("newScheduledArrival").asText()).isEqualTo("2026-11-11T10:20:00Z");
        assertThat(message.has("reasonCode")).isFalse();
    }

    // the simulators ssm: new flight, both links and two outbox rows in one transaction
    @Test
    void ssmForTheNewKeyCreatesTheReplacementAndLinksBothFlights() {
        replanningService.replan(
                new ReplanFlightCommand(
                        KEY, DelayType.DEPARTURE, null, 140, "93", "Hydraulic leak"));
        outbox.deleteAll();
        FlightKey newKey = new FlightKey("HJ500A", LocalDate.of(2026, 11, 11), "BRU");

        ssmListener.receive(replacementSsm("replan-1"));

        Flight replacement =
                flights.findById(JpaFlightId.fromDomain(newKey)).orElseThrow().toDomain();
        assertThat(replacement.status()).isEqualTo(FlightStatus.PLANNED);
        assertThat(replacement.replaces()).contains(KEY);
        assertThat(replacement.replanning()).isEmpty();
        Flight old = flights.findById(JpaFlightId.fromDomain(KEY)).orElseThrow().toDomain();
        assertThat(old.replacementCreated()).isTrue();
        assertThat(old.replaces()).isEmpty();

        List<JpaOutboxMessage> rows = outbox.findAll();
        assertThat(rows)
                .extracting(JpaOutboxMessage::getRoutingKey)
                .containsExactlyInAnyOrder("flight.created", "flight.replaced");
        JsonNode replaced =
                json.readTree(
                        rows.stream()
                                .filter(row -> row.getRoutingKey().equals("flight.replaced"))
                                .findFirst()
                                .orElseThrow()
                                .getPayload());
        assertThat(replaced.get("version").asInt()).isEqualTo(1);
        assertThat(replaced.get("flightNumber").asText()).isEqualTo("HJ500");
        assertThat(replaced.get("scheduledDepartureDate").asText()).isEqualTo("2026-11-10");
        assertThat(replaced.get("departureAirportCode").asText()).isEqualTo("BRU");
        assertThat(replaced.get("newFlightNumber").asText()).isEqualTo("HJ500A");
        assertThat(replaced.get("newScheduledDepartureDate").asText()).isEqualTo("2026-11-11");
        assertThat(replaced.get("newDepartureAirportCode").asText()).isEqualTo("BRU");
        assertThat(replaced.get("newScheduledDeparture").asText())
                .isEqualTo("2026-11-11T05:00:00Z");
        assertThat(replaced.get("newScheduledArrival").asText()).isEqualTo("2026-11-11T10:20:00Z");

        // the same ssm again (redelivered event) changes nothing
        outbox.deleteAll();
        ssmListener.receive(replacementSsm("replan-1"));

        assertThat(flights.count()).isEqualTo(2);
        assertThat(outbox.count()).isZero();
    }

    private SsmMessage replacementSsm(String messageId) {
        String body =
                """
                {"messageId":"%s","startDate":"2026-11-11","endDate":"2026-11-11",
                 "operatingDays":["WEDNESDAY"],"departureTime":"05:00:00","arrivalTime":"10:20:00",
                 "flightNumber":"HJ500A","departureAirportCode":"BRU","arrivalAirportCode":"AMS",
                 "aircraftRegistration":"SXBAA"}
                """
                        .formatted(messageId);
        return json.readValue(body, SsmMessage.class);
    }

    // a flight that was never replanned reads back without a replanning
    @Test
    void flightWithoutReplanningReadsBackEmpty() {
        Flight stored = flights.findById(JpaFlightId.fromDomain(KEY)).orElseThrow().toDomain();

        assertThat(stored.replanning()).isEmpty();
        assertThat(stored.status()).isEqualTo(FlightStatus.PLANNED);
    }
}
