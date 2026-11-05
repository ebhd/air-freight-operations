package be.kdg.airfreight.flight.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import be.kdg.airfreight.flight.application.FlightDelayService;
import be.kdg.airfreight.flight.application.command.AddDelayCommand;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.support.PostgresTestConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// real postgresql with the real schema because the lock is a database feature
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@ActiveProfiles({"test", "postgres"})
class OverlappingDelayChangesIT {
    private static final FlightKey KEY = new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final Instant STD = Instant.parse("2026-10-19T08:00:00Z");

    @Autowired private FlightDelayService flightDelayService;
    @Autowired private FlightRepository flights;
    @Autowired private JdbcClient jdbc;
    @Autowired private JsonMapper json;
    @MockitoSpyBean private DelayReasonRepository delayReasons;
    @MockitoBean private RabbitTemplate rabbit;

    // no background outbox polling in tests
    @MockitoBean(
            name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    private ScheduledAnnotationBeanPostProcessor scheduling;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM outbox_messages").update();
        jdbc.sql("DELETE FROM departure_delays").update();
        jdbc.sql("DELETE FROM arrival_delays").update();
        jdbc.sql("DELETE FROM flights").update();
        flights.save(Flight.create(KEY, "AMS", "SXBAA", STD, STD.plus(Duration.ofHours(2))));
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
                        eq("flight.estimates-changed"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void secondOfTwoOverlappingDelaysStartsFromTheFirst() throws Exception {
        // without the lock both requests have read the flight here before either one saves
        CountDownLatch bothReadTheFlight = new CountDownLatch(2);
        doAnswer(
                        invocation -> {
                            bothReadTheFlight.countDown();
                            bothReadTheFlight.await(2, TimeUnit.SECONDS);
                            return invocation.callRealMethod();
                        })
                .when(delayReasons)
                .findByCode("71");

        try (ExecutorService planners = Executors.newFixedThreadPool(2)) {
            Future<?> first = planners.submit(() -> addDepartureDelay(30));
            Future<?> second = planners.submit(() -> addDepartureDelay(60));
            first.get();
            second.get();
        }

        Flight stored = flights.findById(KEY).orElseThrow();
        assertThat(stored.departureDelays())
                .extracting(Delay::minutes)
                .containsExactlyInAnyOrder(30, 60);
        assertThat(stored.arrivalDelays()).isEmpty();
        assertThat(stored.estimatedDeparture()).isEqualTo(STD.plus(Duration.ofMinutes(90)));

        List<JsonNode> published = estimatesChangedMessages();
        assertThat(published).hasSize(2);
        JsonNode earlier = published.getFirst();
        JsonNode later = published.getLast();
        assertThat(instant(earlier, "previousEstimatedDeparture")).isEqualTo(STD);
        assertThat(instant(later, "previousEstimatedDeparture"))
                .isEqualTo(instant(earlier, "estimatedDeparture"));
        assertThat(instant(later, "estimatedDeparture")).isEqualTo(stored.estimatedDeparture());
        assertThat(instant(later, "estimatedArrival")).isEqualTo(stored.estimatedArrival());
    }

    private Flight addDepartureDelay(int minutes) {
        return flightDelayService.addDepartureDelay(
                new AddDelayCommand(KEY, minutes, "71", "Fog at departure"));
    }

    private List<JsonNode> estimatesChangedMessages() {
        return jdbc
                .sql(
                        "SELECT payload FROM outbox_messages"
                                + " WHERE routing_key = 'flight.estimates-changed'"
                                + " ORDER BY created_at")
                .query(String.class)
                .list()
                .stream()
                .map(json::readTree)
                .toList();
    }

    private static Instant instant(JsonNode message, String field) {
        return Instant.parse(message.get(field).asText());
    }
}
