package be.kdg.airfreight.flight.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.application.actualtime.FlightActualTimeService;
import be.kdg.airfreight.flight.application.actualtime.RecordArrivalCommand;
import be.kdg.airfreight.flight.application.actualtime.RecordDepartureCommand;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import be.kdg.airfreight.flight.domain.flight.actualtime.ActualTimeRefusedException;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlight;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightId;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightRepository;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessage;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// the real service, flight, jpa and outbox on h2, with the real clock so the schedule is relative
@SpringBootTest
@ActiveProfiles({"test", "h2"})
class ActualTimeIntegrationTest {
    // a flight that left an hour ago: atd and ata in the past but within the tolerance
    private final Instant std =
            Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MINUTES);
    private final Instant sta = std.plus(Duration.ofHours(2));
    private final FlightKey key =
            new FlightKey("HJ221", std.atZone(ZoneOffset.UTC).toLocalDate(), "BRU");

    @Autowired private FlightActualTimeService service;
    @Autowired private JpaFlightRepository flights;
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
        Flight flight = Flight.create(key, "AMS", "SXBAA", std, sta);
        flight.recordFinalizedLoad();
        flights.save(JpaFlight.fromDomain(flight));
    }

    @Test
    void departureIsStoredWithOneOutboxMessage() {
        Instant atd = std.plus(Duration.ofMinutes(3));

        service.recordDeparture(new RecordDepartureCommand(key, atd));

        Flight stored = storedFlight();
        assertThat(stored.status()).isEqualTo(FlightStatus.DEPARTED);
        assertThat(stored.actualDeparture()).isEqualTo(atd);
        assertThat(stored.actualArrival()).isNull();
        JpaOutboxMessage message = outbox.findAll().getFirst();
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(message.getExchange()).isEqualTo("flight.events");
        assertThat(message.getRoutingKey()).isEqualTo("flight.departed");
        assertThat(message.getPublishedAt()).isNull();
        JsonNode payload = json.readTree(message.getPayload());
        assertThat(payload.get("messageId").asString()).isEqualTo(message.getId().toString());
        assertThat(payload.get("version").asInt()).isEqualTo(1);
        assertThat(payload.get("flightNumber").asString()).isEqualTo("HJ221");
        assertThat(payload.get("scheduledDepartureDate").asString())
                .isEqualTo(key.scheduledDepartureDate().toString());
        assertThat(payload.get("departureAirportCode").asString()).isEqualTo("BRU");
        assertThat(payload.get("arrivalAirportCode").asString()).isEqualTo("AMS");
        assertThat(payload.get("aircraftRegistration").asString()).isEqualTo("SXBAA");
        assertThat(Instant.parse(payload.get("actualDeparture").asString())).isEqualTo(atd);
        assertThat(payload.has("occurredOn")).isTrue();
    }

    @Test
    void arrivalIsStoredWithALandedMessage() {
        Instant atd = std;
        Instant ata = std.plus(Duration.ofMinutes(30));
        service.recordDeparture(new RecordDepartureCommand(key, atd));

        service.recordArrival(new RecordArrivalCommand(key, ata));

        Flight stored = storedFlight();
        assertThat(stored.status()).isEqualTo(FlightStatus.LANDED);
        assertThat(stored.actualDeparture()).isEqualTo(atd);
        assertThat(stored.actualArrival()).isEqualTo(ata);
        assertThat(outbox.findAll())
                .extracting(JpaOutboxMessage::getRoutingKey)
                .containsExactlyInAnyOrder("flight.departed", "flight.landed");
        JpaOutboxMessage landed =
                outbox.findAll().stream()
                        .filter(message -> message.getRoutingKey().equals("flight.landed"))
                        .findFirst()
                        .orElseThrow();
        JsonNode payload = json.readTree(landed.getPayload());
        assertThat(Instant.parse(payload.get("actualArrival").asString())).isEqualTo(ata);
        assertThat(payload.has("actualDeparture")).isFalse();
    }

    // msg-05: no ad for a refused registration, the transaction rolls back
    @Test
    void refusedDepartureLeavesNoOutboxMessage() {
        assertThatThrownBy(
                        () ->
                                service.recordDeparture(
                                        new RecordDepartureCommand(
                                                key, std.plus(Duration.ofMinutes(6)))))
                .isInstanceOf(ActualTimeRefusedException.class);

        assertThat(storedFlight().status()).isEqualTo(FlightStatus.PLANNED);
        assertThat(outbox.count()).isZero();
    }

    private Flight storedFlight() {
        return flights.findById(JpaFlightId.fromDomain(key)).orElseThrow().toDomain();
    }
}
