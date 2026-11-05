package be.kdg.airfreight.cargo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.application.booking.BookUldCommand;
import be.kdg.airfreight.cargo.application.booking.BookUldService;
import be.kdg.airfreight.cargo.application.cargoplan.FinalizeLoadService;
import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.BookingRejectedException;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadNotFinalizedException;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loadcheck.LateralBalanceCheck;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadChecks;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import be.kdg.airfreight.cargo.domain.loading.LoadingNotAllowedException;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import be.kdg.airfreight.cargo.infrastructure.messaging.OutboxCargoEvents;
import be.kdg.airfreight.cargo.support.PostgresTestConfiguration;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// every step commits for real because the version and the outbox row only count at commit
@DataJpaTest
@ActiveProfiles({"test", "postgres"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    PostgresTestConfiguration.class,
    DbCargoPlanRepository.class,
    DbProcessedMessageRepository.class,
    BookUldService.class,
    LoadingWindowService.class,
    FinalizeLoadService.class,
    OutboxCargoEvents.class,
    FinalizeLoadIT.Beans.class
})
class FinalizeLoadIT {
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
    private static final Instant STD = Instant.parse("2026-10-19T08:00:00Z");
    private static final FlightInformation PLANNED =
            new FlightInformation(
                    KEY,
                    new AirportCode("AMS"),
                    FlightStatus.PLANNED,
                    STD,
                    STD.plus(Duration.ofHours(2)),
                    STD);
    private static final LoadingWindowPolicy POLICY =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    private static final Instant INSIDE = Instant.parse("2026-10-19T06:00:00Z");
    private static final LoadingContext OPEN =
            new LoadingContext(PLANNED, POLICY.windowFor(STD, STD), INSIDE);
    private static final LoadChecks CHECKS =
            new LoadChecks(new LateralBalanceCheck(new BigDecimal("0.10")));
    private static final UldCode AKE = new UldCode("AKE12345KL");
    private static final UldCode PMC = new UldCode("PMC00001KL");
    private static final Instant RECEIVED = Instant.parse("2026-10-15T10:00:00Z");

    @Autowired private DbCargoPlanRepository repository;
    @Autowired private BookUldService bookUldService;
    @Autowired private FinalizeLoadService finalizeLoadService;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcClient jdbc;
    @Autowired private JsonMapper json;
    @MockitoBean private FlightRepository flights;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM outbox_messages").update();
        jdbc.sql("DELETE FROM uld_bookings").update();
        jdbc.sql("DELETE FROM cargo_plans").update();
        jdbc.sql("DELETE FROM processed_messages").update();
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(PLANNED));
    }

    @Test
    void finalizeCommitsTheStateAndOneOutboxRowTogether() {
        book("uld-1", AKE, "1250.5", WeightUnit.KG);
        book("uld-2", PMC, "1000", WeightUnit.LB);
        // a single position counts for neither side so the balance passes
        inTransaction(plan -> plan.assignPosition(AKE, LoadingPosition.B1, OPEN));
        long versionBefore = version();

        finalizeLoadService.finalizeLoad(KEY);

        assertThat(jdbc.sql("SELECT state FROM cargo_plans").query(String.class).single())
                .isEqualTo("FINALIZED");
        assertThat(jdbc.sql("SELECT finalized_at FROM cargo_plans").query(Instant.class).single())
                .isEqualTo(INSIDE);
        assertThat(version()).isEqualTo(versionBefore + 1);
        assertThat(outboxRows()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT published_at FROM outbox_messages").query(Instant.class).list())
                .containsOnlyNulls();

        String id = jdbc.sql("SELECT id FROM outbox_messages").query(String.class).single();
        JsonNode payload =
                json.readTree(
                        jdbc.sql("SELECT payload FROM outbox_messages")
                                .query(String.class)
                                .single());
        assertThat(jdbc.sql("SELECT routing_key FROM outbox_messages").query(String.class).single())
                .isEqualTo("cargo.load-finalized");
        assertThat(payload.get("messageId").asString()).isEqualTo(id);
        assertThat(payload.get("version").asInt()).isEqualTo(1);
        assertThat(payload.get("occurredOn").asString()).isEqualTo("2026-10-19T06:00:00Z");
        assertThat(payload.get("flightNumber").asString()).isEqualTo("HJ221");
        assertThat(payload.get("scheduledDepartureDate").asString()).isEqualTo("2026-10-19");
        assertThat(payload.get("departureAirportCode").asString()).isEqualTo("BRU");
        // only the positioned uld goes in the cpm
        assertThat(payload.get("ulds")).hasSize(1);
        JsonNode uld = payload.get("ulds").get(0);
        assertThat(uld.get("uldCode").asString()).isEqualTo("AKE12345KL");
        assertThat(uld.get("position").asString()).isEqualTo("B1");
        assertThat(uld.get("weight").get("value").decimalValue()).isEqualByComparingTo("1250.5");
        assertThat(uld.get("weight").get("unit").asString()).isEqualTo("KG");
        assertThat(uld.get("destinationAirportCode").asString()).isEqualTo("AMS");
    }

    @Test
    void hardFailureLeavesNoTrace() {
        book("uld-1", AKE, "1000", WeightUnit.KG);
        book("uld-2", PMC, "1101", WeightUnit.KG);
        inTransaction(
                plan -> {
                    plan.assignPosition(AKE, LoadingPosition.CL, OPEN);
                    plan.assignPosition(PMC, LoadingPosition.CR, OPEN);
                });
        long versionBefore = version();

        assertThatThrownBy(() -> finalizeLoadService.finalizeLoad(KEY))
                .isInstanceOf(LoadNotFinalizedException.class);

        assertThat(jdbc.sql("SELECT state FROM cargo_plans").query(String.class).single())
                .isEqualTo("DRAFT");
        assertThat(version()).isEqualTo(versionBefore);
        assertThat(outboxRows()).isZero();
    }

    // the position change commits first so the finalize that read the older plan is undone
    @Test
    void finalizeThatLosesAgainstAPositionChangeRollsBackItsOutboxRow() {
        book("uld-1", AKE, "1000", WeightUnit.KG);
        book("uld-2", PMC, "1000", WeightUnit.KG);

        assertThatThrownBy(
                        () ->
                                overlapping(
                                        first ->
                                                first.assignPosition(AKE, LoadingPosition.CL, OPEN),
                                        second ->
                                                events.publishEvent(
                                                        second.finalizeLoad(OPEN, CHECKS))))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(jdbc.sql("SELECT state FROM cargo_plans").query(String.class).single())
                .isEqualTo("DRAFT");
        assertThat(outboxRows()).isZero();
        assertThat(positionOf(AKE)).isEqualTo("CL");
    }

    // the finalize commits first so the late position change fails and the load stays frozen
    @Test
    void positionChangeThatLosesAgainstFinalizeFails() {
        book("uld-1", AKE, "1000", WeightUnit.KG);

        assertThatThrownBy(
                        () ->
                                overlapping(
                                        first ->
                                                events.publishEvent(
                                                        first.finalizeLoad(OPEN, CHECKS)),
                                        second ->
                                                second.assignPosition(
                                                        AKE, LoadingPosition.CL, OPEN)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(jdbc.sql("SELECT state FROM cargo_plans").query(String.class).single())
                .isEqualTo("FINALIZED");
        assertThat(outboxRows()).isEqualTo(1);
        assertThat(positionOf(AKE)).isNull();
    }

    @Test
    void finalizedPlanRefusesASecondFinalizeAndNewBookings() {
        book("uld-1", AKE, "1000", WeightUnit.KG);
        finalizeLoadService.finalizeLoad(KEY);

        assertThatThrownBy(() -> finalizeLoadService.finalizeLoad(KEY))
                .isInstanceOf(LoadingNotAllowedException.class);
        assertThatThrownBy(() -> book("uld-2", PMC, "1000", WeightUnit.KG))
                .isInstanceOf(BookingRejectedException.class);

        assertThat(outboxRows()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM uld_bookings").query(Long.class).single())
                .isEqualTo(1);
    }

    private void book(String messageId, UldCode uldCode, String weight, WeightUnit unit) {
        bookUldService.bookUld(
                new BookUldCommand(
                        messageId,
                        "HJ221",
                        19,
                        KEY.departureAirportCode(),
                        uldCode,
                        new Weight(new BigDecimal(weight), unit),
                        new AirportCode("AMS"),
                        RECEIVED));
    }

    private void inTransaction(Consumer<CargoPlan> change) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            CargoPlan plan = repository.findByFlightKey(KEY).orElseThrow();
                            change.accept(plan);
                            repository.save(plan);
                        });
    }

    // the second change loads first but the first change commits before it
    private void overlapping(Consumer<CargoPlan> first, Consumer<CargoPlan> second) {
        TransactionTemplate own = new TransactionTemplate(transactionManager);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        own.executeWithoutResult(
                outer -> {
                    CargoPlan late = repository.findByFlightKey(KEY).orElseThrow();
                    own.executeWithoutResult(
                            inner -> {
                                CargoPlan early = repository.findByFlightKey(KEY).orElseThrow();
                                first.accept(early);
                                repository.save(early);
                            });
                    second.accept(late);
                    repository.save(late);
                });
    }

    private long version() {
        return jdbc.sql("SELECT version FROM cargo_plans").query(Long.class).single();
    }

    private long outboxRows() {
        return jdbc.sql("SELECT count(*) FROM outbox_messages").query(Long.class).single();
    }

    private String positionOf(UldCode uldCode) {
        return jdbc.sql("SELECT loading_position FROM uld_bookings WHERE uld_code = ?")
                .param(uldCode.value())
                .query(String.class)
                .list()
                .getFirst();
    }

    @TestConfiguration
    static class Beans {
        @Bean
        LoadingWindowPolicy loadingWindowPolicy() {
            return POLICY;
        }

        @Bean
        LoadChecks loadChecks() {
            return CHECKS;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(INSIDE, ZoneOffset.UTC);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}
