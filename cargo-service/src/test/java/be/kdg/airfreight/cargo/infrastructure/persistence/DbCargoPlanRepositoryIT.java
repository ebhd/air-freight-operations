package be.kdg.airfreight.cargo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.CargoPlan;
import be.kdg.airfreight.cargo.domain.CargoPlanState;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.LoadingPosition;
import be.kdg.airfreight.cargo.domain.UldBooking;
import be.kdg.airfreight.cargo.domain.UldCode;
import be.kdg.airfreight.cargo.domain.Weight;
import be.kdg.airfreight.cargo.domain.WeightUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@Import(DbCargoPlanRepository.class)
class DbCargoPlanRepositoryIT {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);
    private static final AirportCode BRU = new AirportCode("BRU");
    private static final AirportCode AMS = new AirportCode("AMS");
    private static final AirportCode ATH = new AirportCode("ATH");
    private static final FlightKey KEY = new FlightKey("HJ221", DAY, BRU);

    // same postgresql image as docker compose
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired private DbCargoPlanRepository repository;
    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcClient jdbc;

    @Test
    void findsOnlyTheBookingsOfThatFlight() {
        CargoPlan cargoPlan = new CargoPlan(KEY);
        cargoPlan.bookUld(new UldCode("AKE12345KL"), weight("1250.5", WeightUnit.KG), AMS);
        cargoPlan.bookUld(new UldCode("PMC00001KL"), weight("1000", WeightUnit.LB), ATH);
        repository.save(cargoPlan);
        saveOneBooking(new FlightKey("HJ222", DAY, BRU), "OTHERNUMBER");
        saveOneBooking(new FlightKey("HJ221", DAY.plusDays(1), BRU), "OTHERDATE");
        saveOneBooking(new FlightKey("HJ221", DAY, AMS), "OTHERAIRPORT");
        // forget the saved objects so the read below really comes from the database
        entityManager.flush();
        entityManager.clear();

        CargoPlan stored = repository.findByFlightKey(KEY).orElseThrow();

        assertThat(stored.getFlightKey()).isEqualTo(KEY);
        // nothing can finalize a plan yet so every stored plan is a draft
        assertThat(stored.isFinalized()).isFalse();
        assertThat(stored.getBookings())
                .extracting(booking -> booking.getCode().value())
                .containsExactlyInAnyOrder("AKE12345KL", "PMC00001KL");
        UldBooking pallet = stored.findBooking(new UldCode("PMC00001KL")).orElseThrow();
        assertThat(pallet.getWeight()).isEqualTo(weight("1000", WeightUnit.LB));
        assertThat(pallet.getWeight().value().toPlainString()).isEqualTo("1000");
        assertThat(pallet.getDestination()).isEqualTo(ATH);
    }

    @Test
    void flightWithoutBookingsHasNoStoredPlan() {
        assertThat(repository.findByFlightKey(KEY)).isEmpty();
    }

    // the context starts with ddl-auto validate so the entities match the schema file
    @Test
    void storesAndRestoresPositions() {
        repository.save(
                CargoPlan.restore(
                        KEY,
                        CargoPlanState.DRAFT,
                        List.of(
                                booking("AKE12345KL", LoadingPosition.RL),
                                booking("PMC00001KL", null))));
        entityManager.flush();
        entityManager.clear();

        CargoPlan stored = repository.findByFlightKey(KEY).orElseThrow();

        assertThat(stored.findBooking(new UldCode("AKE12345KL")).orElseThrow().getPosition())
                .contains(LoadingPosition.RL);
        assertThat(stored.findBooking(new UldCode("PMC00001KL")).orElseThrow().getPosition())
                .isEmpty();
        assertThat(
                        jdbc.sql(
                                        "SELECT loading_position FROM uld_bookings"
                                                + " WHERE uld_code = 'AKE12345KL'")
                                .query(String.class)
                                .single())
                .isEqualTo("RL");
    }

    @Test
    void databaseRefusesTwoUldsOnOnePosition() {
        repository.save(
                CargoPlan.restore(
                        KEY,
                        CargoPlanState.DRAFT,
                        List.of(
                                booking("AKE12345KL", LoadingPosition.CL),
                                booking("PMC00001KL", null),
                                booking("PMC00002KL", null))));
        // the same position on another flight is fine
        repository.save(
                CargoPlan.restore(
                        new FlightKey("HJ222", DAY, BRU),
                        CargoPlanState.DRAFT,
                        List.of(booking("AKE12345KL", LoadingPosition.CL))));
        // two bookings without a position are fine too
        entityManager.flush();

        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                "UPDATE uld_bookings SET loading_position = 'CL'"
                                                        + " WHERE flight_number = 'HJ221'"
                                                        + " AND uld_code = 'PMC00001KL'")
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uld_bookings_one_uld_per_position");
    }

    private static UldBooking booking(String uldCode, LoadingPosition position) {
        return new UldBooking(new UldCode(uldCode), weight("1", WeightUnit.KG), AMS, position);
    }

    private void saveOneBooking(FlightKey flightKey, String uldCode) {
        CargoPlan cargoPlan = new CargoPlan(flightKey);
        cargoPlan.bookUld(new UldCode(uldCode), weight("1", WeightUnit.KG), AMS);
        repository.save(cargoPlan);
    }

    private static Weight weight(String value, WeightUnit unit) {
        return new Weight(new BigDecimal(value), unit);
    }
}
