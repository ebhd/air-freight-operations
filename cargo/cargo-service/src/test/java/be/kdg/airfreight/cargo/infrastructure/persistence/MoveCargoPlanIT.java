package be.kdg.airfreight.cargo.infrastructure.persistence;

import static be.kdg.airfreight.cargo.support.TestHelper.plannedFlight;
import static be.kdg.airfreight.cargo.support.TestHelper.restoredPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.application.cargoplan.MoveCargoPlanService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanState;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.support.PostgresTestConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// the move commits for real so the deleted rows, the check constraint and the version count
@DataJpaTest
@ActiveProfiles({"test", "postgres"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PostgresTestConfiguration.class, DbCargoPlanRepository.class, MoveCargoPlanService.class})
class MoveCargoPlanIT {
    private static final AirportCode BRU = new AirportCode("BRU");
    private static final FlightKey OLD = new FlightKey("HJ500", LocalDate.of(2026, 11, 10), BRU);
    private static final FlightKey NEW = new FlightKey("HJ500A", LocalDate.of(2026, 11, 11), BRU);
    private static final UldCode AKE = new UldCode("AKE00001HJ");
    private static final UldCode PMC = new UldCode("PMC00002HJ");

    @Autowired private DbCargoPlanRepository repository;
    @Autowired private MoveCargoPlanService moveService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM uld_bookings").update();
        jdbc.sql("DELETE FROM cargo_plans").update();
    }

    @Test
    void moveDeletesTheOldRowsAndWritesTheNewPlanAndTheMarker() {
        Instant finalizedAt = Instant.parse("2026-11-10T13:00:00Z");
        inTransaction(
                () ->
                        repository.save(
                                restoredPlan(
                                        OLD,
                                        CargoPlanState.FINALIZED,
                                        finalizedAt,
                                        List.of(
                                                booking(AKE, LoadingPosition.CL),
                                                booking(PMC, null)))));

        moveService.move(OLD, NEW);

        assertThat(rows("uld_bookings", OLD)).isZero();
        assertThat(rows("uld_bookings", NEW)).isEqualTo(2);
        assertThat(
                        jdbc.sql(
                                        "SELECT loading_position FROM uld_bookings WHERE uld_code"
                                                + " = 'AKE00001HJ'")
                                .query(String.class)
                                .single())
                .isEqualTo("CL");
        assertThat(column("state", OLD)).isEqualTo("REPLANNED");
        assertThat(column("moved_to_flight_number", OLD)).isEqualTo("HJ500A");
        assertThat(column("moved_to_departure_date", OLD)).isEqualTo("2026-11-11");
        assertThat(column("moved_to_departure_airport_code", OLD)).isEqualTo("BRU");
        assertThat(column("state", NEW)).isEqualTo("DRAFT");
        CargoPlan old = repository.findByFlightKey(OLD).orElseThrow();
        assertThat(old.getMovedTo()).contains(NEW);
        assertThat(old.getFinalizedAt()).contains(finalizedAt);
        assertThat(repository.findByFlightKey(NEW).orElseThrow().getBookings()).hasSize(2);
    }

    @Test
    void redeliveredEventChangesNothing() {
        inTransaction(
                () ->
                        repository.save(
                                restoredPlan(
                                        OLD, CargoPlanState.DRAFT, List.of(booking(AKE, null)))));
        moveService.move(OLD, NEW);
        long oldVersion = version(OLD);
        long newVersion = version(NEW);

        moveService.move(OLD, NEW);

        assertThat(version(OLD)).isEqualTo(oldVersion);
        assertThat(version(NEW)).isEqualTo(newVersion);
        assertThat(rows("uld_bookings", NEW)).isEqualTo(1);
    }

    // a position change that read the old plan before the move commits loses its version check
    @Test
    void positionChangeThatLosesAgainstTheMoveFails() {
        inTransaction(
                () ->
                        repository.save(
                                restoredPlan(
                                        OLD, CargoPlanState.DRAFT, List.of(booking(AKE, null)))));
        TransactionTemplate own = new TransactionTemplate(transactionManager);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(
                        () ->
                                own.executeWithoutResult(
                                        outer -> {
                                            CargoPlan late =
                                                    repository.findByFlightKey(OLD).orElseThrow();
                                            // the move commits on its own before the late save
                                            own.executeWithoutResult(
                                                    inner -> moveService.move(OLD, NEW));
                                            // the stale plan still thinks it may change
                                            late.bookUld(
                                                    PMC,
                                                    new Weight(new BigDecimal("1"), WeightUnit.KG),
                                                    new AirportCode("AMS"),
                                                    plannedFlight(OLD));
                                            repository.save(late);
                                        }))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(column("state", OLD)).isEqualTo("REPLANNED");
        assertThat(rows("uld_bookings", OLD)).isZero();
    }

    private void inTransaction(Runnable change) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> change.run());
    }

    private long rows(String table, FlightKey key) {
        return jdbc.sql(
                        "SELECT count(*) FROM "
                                + table
                                + " WHERE flight_number = :number AND scheduled_departure_date"
                                + " = :date")
                .param("number", key.flightNumber())
                .param("date", key.scheduledDepartureDate())
                .query(Long.class)
                .single();
    }

    private String column(String column, FlightKey key) {
        return jdbc.sql(
                        "SELECT "
                                + column
                                + " FROM cargo_plans WHERE flight_number = :number AND"
                                + " scheduled_departure_date = :date")
                .param("number", key.flightNumber())
                .param("date", key.scheduledDepartureDate())
                .query(String.class)
                .single();
    }

    private long version(FlightKey key) {
        return Long.parseLong(column("version", key));
    }

    private static UldBooking booking(UldCode code, LoadingPosition position) {
        return new UldBooking(
                code,
                new Weight(new BigDecimal("1000"), WeightUnit.KG),
                new AirportCode("AMS"),
                position);
    }
}
