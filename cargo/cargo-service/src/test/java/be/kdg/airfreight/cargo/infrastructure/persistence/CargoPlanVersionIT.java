package be.kdg.airfreight.cargo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.application.booking.BookUldCommand;
import be.kdg.airfreight.cargo.application.booking.BookUldResult;
import be.kdg.airfreight.cargo.application.booking.BookUldService;
import be.kdg.airfreight.cargo.application.cargoplan.UldPositionService;
import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// every step commits for real because the version is only raised and checked at commit
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    DbCargoPlanRepository.class,
    DbProcessedMessageRepository.class,
    BookUldService.class,
    LoadingWindowService.class,
    UldPositionService.class,
    CargoPlanVersionIT.LoadingWindowBeans.class
})
class CargoPlanVersionIT {
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
    // inside the window 2026-10-18T20:00Z to 2026-10-19T07:40Z
    private static final LoadingWindowPolicy POLICY =
            new LoadingWindowPolicy(Duration.ofHours(12), Duration.ofMinutes(20));
    private static final Instant INSIDE = Instant.parse("2026-10-19T06:00:00Z");
    private static final LoadingContext OPEN =
            new LoadingContext(PLANNED, POLICY.windowFor(STD, STD), INSIDE);
    private static final UldCode AKE = new UldCode("AKE12345KL");
    private static final UldCode PMC = new UldCode("PMC00001KL");
    // day 19 resolves to 2026-10-19 from this first receipt
    private static final Instant RECEIVED = Instant.parse("2026-10-15T10:00:00Z");

    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired private DbCargoPlanRepository repository;
    @Autowired private BookUldService bookUldService;
    @Autowired private UldPositionService uldPositionService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcClient jdbc;
    @MockitoBean private FlightRepository flights;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM uld_bookings").update();
        jdbc.sql("DELETE FROM cargo_plans").update();
        jdbc.sql("DELETE FROM processed_messages").update();
        when(flights.findByFlightKey(KEY)).thenReturn(Optional.of(PLANNED));
    }

    @Test
    void firstBookingCreatesThePlanRowAndEveryBookingRaisesTheVersion() {
        assertThat(book("uld-1", AKE)).isEqualTo(BookUldResult.Outcome.BOOKED);
        assertThat(version()).isZero();

        assertThat(book("uld-2", PMC)).isEqualTo(BookUldResult.Outcome.BOOKED);
        assertThat(version()).isEqualTo(1);

        // a repeated uld and a redelivered message still change nothing
        assertThat(book("uld-3", AKE)).isEqualTo(BookUldResult.Outcome.DUPLICATE_ULD);
        assertThat(book("uld-2", PMC)).isEqualTo(BookUldResult.Outcome.ALREADY_PROCESSED);
        assertThat(version()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM uld_bookings").query(Long.class).single())
                .isEqualTo(2);
    }

    @Test
    void everySaveRaisesTheVersionByOne() {
        bookAkeAndPmc();

        inTransaction(plan -> plan.assignPosition(AKE, LoadingPosition.CL, OPEN));
        assertThat(version()).isEqualTo(2);
        inTransaction(plan -> plan.clearPosition(AKE, OPEN));
        assertThat(version()).isEqualTo(3);
        // reading never changes the version
        repository.findByFlightKey(KEY);
        assertThat(version()).isEqualTo(3);
    }

    @Test
    void secondOfTwoOverlappingChangesOnDifferentPositionsFails() {
        bookAkeAndPmc();

        assertThatThrownBy(
                        () ->
                                overlapping(
                                        first ->
                                                first.assignPosition(AKE, LoadingPosition.CL, OPEN),
                                        second ->
                                                second.assignPosition(
                                                        PMC, LoadingPosition.CR, OPEN)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(positionOf(AKE)).isEqualTo("CL");
        assertThat(positionOf(PMC)).isNull();
        assertThat(version()).isEqualTo(2);
    }

    @Test
    void secondOfTwoOverlappingChangesOnTheSamePositionFails() {
        bookAkeAndPmc();

        assertThatThrownBy(
                        () ->
                                overlapping(
                                        first ->
                                                first.assignPosition(AKE, LoadingPosition.CL, OPEN),
                                        second ->
                                                second.assignPosition(
                                                        PMC, LoadingPosition.CL, OPEN)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uld_bookings_one_uld_per_position");

        assertThat(positionOf(AKE)).isEqualTo("CL");
        assertThat(positionOf(PMC)).isNull();
        assertThat(version()).isEqualTo(2);
    }

    // the listener retries a data access exception and the retry reads the new version
    @Test
    void bookingThatLosesTheRaceFailsAndItsRetrySucceeds() {
        bookAkeAndPmc();
        UldCode late = new UldCode("AKE00003KL");

        assertThatThrownBy(
                        () ->
                                overlapping(
                                        first ->
                                                first.assignPosition(AKE, LoadingPosition.CL, OPEN),
                                        second ->
                                                second.bookUld(
                                                        late, weight(), new AirportCode("AMS"))))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(book("uld-late", late)).isEqualTo(BookUldResult.Outcome.BOOKED);

        assertThat(positionOf(AKE)).isEqualTo("CL");
        assertThat(positionOf(late)).isNull();
        assertThat(version()).isEqualTo(3);
    }

    // the service gets the transaction template of spring so this also proves the wiring
    @Test
    void positionServiceCommitsEachChangeInItsOwnTransaction() {
        bookAkeAndPmc();

        uldPositionService.assignPosition(KEY, AKE, LoadingPosition.RL);
        assertThat(positionOf(AKE)).isEqualTo("RL");
        assertThat(version()).isEqualTo(2);

        uldPositionService.clearPosition(KEY, AKE);
        assertThat(positionOf(AKE)).isNull();
        assertThat(version()).isEqualTo(3);
    }

    private void bookAkeAndPmc() {
        book("uld-1", AKE);
        book("uld-2", PMC);
    }

    private BookUldResult.Outcome book(String messageId, UldCode uldCode) {
        return bookUldService
                .bookUld(
                        new BookUldCommand(
                                messageId,
                                "HJ221",
                                19,
                                KEY.departureAirportCode(),
                                uldCode,
                                weight(),
                                new AirportCode("AMS"),
                                RECEIVED))
                .outcome();
    }

    // load change and save in one committed transaction like the services do
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

    // null when the uld has no position
    private String positionOf(UldCode uldCode) {
        return jdbc.sql("SELECT loading_position FROM uld_bookings WHERE uld_code = ?")
                .param(uldCode.value())
                .query(String.class)
                .list()
                .getFirst();
    }

    @TestConfiguration
    static class LoadingWindowBeans {
        @Bean
        LoadingWindowPolicy loadingWindowPolicy() {
            return POLICY;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(INSIDE, ZoneOffset.UTC);
        }
    }

    private static Weight weight() {
        return new Weight(new BigDecimal("1250.5"), WeightUnit.KG);
    }
}
