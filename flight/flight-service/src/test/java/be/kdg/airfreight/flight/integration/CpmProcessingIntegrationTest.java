package be.kdg.airfreight.flight.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.infrastructure.messaging.cpm.CpmReceiver;
import be.kdg.airfreight.flight.infrastructure.messaging.cpm.LoadFinalizedMessage;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlight;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightId;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightRepository;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaProcessedMessageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class CpmProcessingIntegrationTest {
    private static final FlightKey KEY = new FlightKey("HJ221", LocalDate.of(2026, 10, 20), "BRU");

    @Autowired private CpmReceiver receiver;
    @Autowired private JpaFlightRepository flights;
    @Autowired private JpaProcessedMessageRepository processedMessages;
    @Autowired private JpaOutboxMessageRepository outbox;
    @Autowired private JsonMapper json;

    @MockitoBean private RabbitTemplate rabbit;

    // no background polling in tests
    @MockitoBean(
            name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    private ScheduledAnnotationBeanPostProcessor scheduling;

    @BeforeEach
    void setUp() {
        processedMessages.deleteAll();
        outbox.deleteAll();
        flights.deleteAll();
        flights.save(
                JpaFlight.fromDomain(
                        Flight.create(
                                KEY,
                                "AMS",
                                "SXBAA",
                                Instant.parse("2026-10-20T08:00:00Z"),
                                Instant.parse("2026-10-20T10:00:00Z"))));
    }

    // the json as cargo's outbox writes it, the ulds are ignored by our record
    @Test
    void cargoMessageSetsTheDepartureCondition() {
        UUID messageId = UUID.randomUUID();

        receiver.receive(cargoMessage(messageId, "HJ221"));

        assertThat(storedFlight().finalizedLoadProcessed()).isTrue();
        assertThat(processedMessages.existsById(messageId)).isTrue();
    }

    @Test
    void redeliveryHasNoSecondEffect() {
        UUID messageId = UUID.randomUUID();
        receiver.receive(cargoMessage(messageId, "HJ221"));

        receiver.receive(cargoMessage(messageId, "HJ221"));

        assertThat(storedFlight().finalizedLoadProcessed()).isTrue();
        assertThat(processedMessages.count()).isEqualTo(1);
        // flight sends no partner message for it, the formatter makes the partner cpm
        assertThat(outbox.count()).isZero();
    }

    @Test
    void unknownFlightIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> receiver.receive(cargoMessage(UUID.randomUUID(), "HJ999")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(
                        "Unknown flight "
                                + new FlightKey("HJ999", KEY.scheduledDepartureDate(), "BRU"));

        assertThat(processedMessages.count()).isZero();
        assertThat(storedFlight().finalizedLoadProcessed()).isFalse();
    }

    private Flight storedFlight() {
        return flights.findById(JpaFlightId.fromDomain(KEY)).orElseThrow().toDomain();
    }

    private LoadFinalizedMessage cargoMessage(UUID messageId, String flightNumber) {
        String body =
                """
                {"messageId":"%s","version":1,"occurredOn":"2026-10-20T06:55:00Z",
                 "flightNumber":"%s","scheduledDepartureDate":"2026-10-20",
                 "departureAirportCode":"BRU",
                 "ulds":[{"uldCode":"AKE12345HJ","position":"CL",
                          "weight":{"value":1250.0,"unit":"KG"},
                          "destinationAirportCode":"AMS"}]}
                """
                        .formatted(messageId, flightNumber);
        return json.readValue(body, LoadFinalizedMessage.class);
    }
}
