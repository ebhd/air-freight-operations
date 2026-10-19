package be.kdg.airfreight.partner.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs both real service JARs; Docker is required. Logs are in build/integration-tests. */
@Testcontainers
class SsmToFlmIntegrationTest {
    @Container
    private static final GenericContainer<?> POSTGRES =
            new GenericContainer<>("postgres:18.6-alpine")
                    .withEnv("POSTGRES_USER", "user")
                    .withEnv("POSTGRES_PASSWORD", "password")
                    .withEnv("POSTGRES_DB", "flight")
                    .withExposedPorts(5432)
                    .waitingFor(
                            Wait.forLogMessage(
                                    ".*database system is ready to accept connections.*\\n", 2));

    @Container
    private static final GenericContainer<?> RABBIT =
            new GenericContainer<>("rabbitmq:4.3.6-management")
                    .withEnv("RABBITMQ_DEFAULT_USER", "user")
                    .withEnv("RABBITMQ_DEFAULT_PASS", "password")
                    .withExposedPorts(5672)
                    .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1));

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final Path LOGS = Path.of("build", "integration-tests");
    private static Process flightService;
    private static Process partnerService;
    private static Connection connection;
    private static Channel channel;

    @BeforeAll
    static void startServices() throws Exception {
        try (var database = DriverManager.getConnection(jdbcUrl("flight"), "user", "password");
                var statement = database.createStatement()) {
            statement.executeUpdate("CREATE DATABASE partner");
        }
        Files.createDirectories(LOGS);
        partnerService = startService("partner");
        flightService = startService("flight");
        await().atMost(Duration.ofSeconds(90))
                .untilAsserted(
                        () -> {
                            assertThat(partnerService.isAlive())
                                    .as("Partner service; see %s", LOGS)
                                    .isTrue();
                            assertThat(flightService.isAlive())
                                    .as("Flight service; see %s", LOGS)
                                    .isTrue();
                            assertThat(Files.readString(LOGS.resolve("partner.log")))
                                    .contains("Started PartnerCommunicationServiceApplication");
                            assertThat(Files.readString(LOGS.resolve("flight.log")))
                                    .contains("Started FlightServiceApplication");
                        });
        var factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getMappedPort(5672));
        factory.setUsername("user");
        factory.setPassword("password");
        connection = factory.newConnection();
        channel = connection.createChannel();
        channel.confirmSelect();
        assertThat(channel.queueDeclarePassive("flight.ssm").getConsumerCount()).isPositive();
        assertThat(channel.queueDeclarePassive("partner.flight-created").getConsumerCount())
                .isPositive();
    }

    @AfterAll
    static void stopServices() throws Exception {
        try {
            stop(flightService);
        } finally {
            try {
                stop(partnerService);
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        }
    }

    @Test
    void newSsmCreatesOneCompleteFlmPerFlight() throws Exception {
        sendSsm("NEW101", "2026-10-20", "2026-10-21", "AMS", "new-ssm");

        assertPublished("NEW101", 2);
        var messages = receiveFlms(2);
        assertFlm(messages.get(0), "NEW101", "2026-10-20", "AMS");
        assertFlm(messages.get(1), "NEW101", "2026-10-21", "AMS");
        assertThat(messages)
                .extracting(message -> message.getProps().getMessageId())
                .doesNotHaveDuplicates();
        assertNoExtraFlms();
    }

    @Test
    void repeatedSsmDoesNotCreateMoreFlightsOrFlms() throws Exception {
        sendSsm("REP101", "2026-10-20", "2026-10-21", "AMS", "repeated-ssm");
        assertPublished("REP101", 2);
        var original = receiveFlms(2);

        sendSsm("REP101", "2026-10-20", "2026-10-21", "AMS", "repeated-ssm");
        // The single SSM consumer processes this marker after the repeated input.
        sendSsm("MARK101", "2026-10-20", "2026-10-20", "AMS", "marker");
        assertPublished("MARK101", 1);

        var messages = receiveFlms(1);
        assertFlm(messages.getFirst(), "MARK101", "2026-10-20", "AMS");
        assertPublished("REP101", 2);
        assertFlm(original.get(0), "REP101", "2026-10-20", "AMS");
        assertFlm(original.get(1), "REP101", "2026-10-21", "AMS");
        assertNoExtraFlms();
    }

    @Test
    void overlappingSsmPreservesExistingFlightsAndOnlyPublishesNewDates() throws Exception {
        sendSsm("OVL101", "2026-10-20", "2026-10-21", "AMS", "first-schedule");
        assertPublished("OVL101", 2);
        var original = receiveFlms(2);

        sendSsm("OVL101", "2026-10-21", "2026-10-22", "LHR", "overlap-schedule");
        assertPublished("OVL101", 3);

        var messages = receiveFlms(1);
        assertFlm(messages.getFirst(), "OVL101", "2026-10-22", "LHR");
        assertFlm(original.get(0), "OVL101", "2026-10-20", "AMS");
        assertFlm(original.get(1), "OVL101", "2026-10-21", "AMS");
        assertNoExtraFlms();
    }

    private static Process startService(String service) throws Exception {
        return new ProcessBuilder(
                        ProcessHandle.current().info().command().orElseThrow(),
                        "-Xmx256m",
                        "-jar",
                        System.getProperty(service + ".jar"),
                        "--server.port=0",
                        "--spring.datasource.url=" + jdbcUrl(service),
                        "--spring.datasource.username=user",
                        "--spring.datasource.password=password",
                        "--spring.rabbitmq.host=" + RABBIT.getHost(),
                        "--spring.rabbitmq.port=" + RABBIT.getMappedPort(5672),
                        "--spring.rabbitmq.username=user",
                        "--spring.rabbitmq.password=password")
                .redirectErrorStream(true)
                .redirectOutput(LOGS.resolve(service + ".log").toFile())
                .start();
    }

    private static void stop(Process process) throws InterruptedException {
        if (process != null) {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static String jdbcUrl(String database) {
        return "jdbc:postgresql://"
                + POSTGRES.getHost()
                + ":"
                + POSTGRES.getMappedPort(5432)
                + "/"
                + database;
    }

    private void sendSsm(String flight, String start, String end, String arrival, String messageId)
            throws Exception {
        var payload =
                """
                {"messageId":"%s","startDate":"%s","endDate":"%s",
                 "operatingDays":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],
                 "departureTime":"08:00:00","arrivalTime":"10:00:00",
                 "flightNumber":"%s","departureAirportCode":"BRU","arrivalAirportCode":"%s",
                 "aircraftRegistration":"SXBAA"}
                """
                        .formatted(messageId, start, end, flight, arrival);
        var properties =
                new AMQP.BasicProperties.Builder()
                        .contentType("application/json")
                        .deliveryMode(2)
                        .messageId(messageId)
                        .build();
        channel.basicPublish(
                "", "flight.ssm", properties, payload.getBytes(StandardCharsets.UTF_8));
        channel.waitForConfirmsOrDie(5_000);
    }

    private void assertPublished(String flight, int expected) {
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            assertThat(
                                            count(
                                                    "flight",
                                                    "SELECT count(*) FROM flights WHERE flight_number = ?",
                                                    flight))
                                    .isEqualTo(expected);
                            assertThat(
                                            count(
                                                    "partner",
                                                    "SELECT count(*) FROM partner_messages WHERE payload::json->>'flightNumber' = ?",
                                                    flight))
                                    .isEqualTo(expected);
                            assertThat(
                                            count(
                                                    "partner",
                                                    "SELECT count(*) FROM partner_messages WHERE payload::json->>'flightNumber' = ? AND state = 'PUBLISHED'",
                                                    flight))
                                    .isEqualTo(expected);
                        });
    }

    private int count(String database, String sql, String flight) throws Exception {
        try (var db = DriverManager.getConnection(jdbcUrl(database), "user", "password");
                var statement = db.prepareStatement(sql)) {
            statement.setString(1, flight);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private List<GetResponse> receiveFlms(int expected) {
        var messages = new ArrayList<GetResponse>();
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            GetResponse message;
                            while ((message = channel.basicGet("partner.outgoing", true)) != null) {
                                messages.add(message);
                            }
                            assertThat(messages).hasSize(expected);
                        });
        return messages;
    }

    private void assertFlm(GetResponse message, String flight, String date, String arrival)
            throws Exception {
        JsonNode body = JSON.readTree(message.getBody());
        var id = UUID.fromString(body.get("messageId").asText());
        assertThat(message.getProps().getMessageId()).isEqualTo(id.toString());
        assertThat(message.getProps().getDeliveryMode()).isEqualTo(2);
        assertThat(message.getProps().getContentType()).isEqualTo("application/json");
        assertThat(body.get("type").asText()).isEqualTo("FLM");
        assertThat(body.get("formatVersion").asInt()).isEqualTo(1);
        var payload = body.get("payload");
        assertThat(payload.isObject()).isTrue();
        assertThat(payload.get("flightNumber").asText()).isEqualTo(flight);
        assertThat(payload.get("scheduledDepartureDate").asText()).isEqualTo(date);
        assertThat(payload.get("departureAirportCode").asText()).isEqualTo("BRU");
        assertThat(payload.get("arrivalAirportCode").asText()).isEqualTo(arrival);
        assertThat(Instant.parse(payload.get("scheduledDeparture").asText()))
                .isEqualTo(Instant.parse(date + "T08:00:00Z"));
        assertThat(Instant.parse(payload.get("scheduledArrival").asText()))
                .isEqualTo(Instant.parse(date + "T10:00:00Z"));
        assertThat(Instant.parse(payload.get("occurredOn").asText())).isNotNull();
        try (var db = DriverManager.getConnection(jdbcUrl("partner"), "user", "password");
                var statement =
                        db.prepareStatement(
                                "SELECT created_at_utc, payload FROM partner_messages WHERE id = ?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(Instant.parse(body.get("createdAt").asText()))
                        .isEqualTo(rows.getTimestamp(1).toInstant());
                assertThat(payload).isEqualTo(JSON.readTree(rows.getString(2)));
            }
        }
        try (var db = DriverManager.getConnection(jdbcUrl("flight"), "user", "password");
                var statement =
                        db.prepareStatement(
                                "SELECT arrival_airport_code FROM flights WHERE flight_number = ? AND scheduled_departure_date = ? AND departure_airport_code = 'BRU'")) {
            statement.setString(1, flight);
            statement.setObject(2, LocalDate.parse(date));
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(arrival);
            }
        }
    }

    private void assertNoExtraFlms() {
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                channel.queueDeclarePassive("partner.outgoing")
                                                        .getMessageCount())
                                        .isZero());
    }
}
