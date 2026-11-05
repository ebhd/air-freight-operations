package be.kdg.airfreight.cargo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// no test transaction because saveIfNewer commits in its own transaction
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
@Import(DbFlightCopyRepository.class)
class DbFlightCopyRepositoryIT {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);
    private static final AirportCode BRU = new AirportCode("BRU");
    private static final FlightKey KEY = new FlightKey("HJ221", DAY, BRU);
    private static final Instant CREATED = Instant.parse("2026-10-18T12:00:00Z");
    private static final Instant DELAYED = Instant.parse("2026-10-19T07:00:00Z");
    private static final Instant ETD = Instant.parse("2026-10-19T08:45:00Z");
    private static final Instant ETA = Instant.parse("2026-10-19T09:55:00Z");

    // same postgresql image as docker compose
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired private DbFlightCopyRepository repository;
    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void emptyCopy() {
        jdbc.sql("DELETE FROM flights").update();
    }

    @Test
    void storesAndReadsBackEveryField() {
        FlightInformation flight = flight(KEY, "08:00");

        assertThat(repository.saveIfNewer(flight, CREATED)).isTrue();

        assertThat(repository.findCopy(KEY)).contains(flight);
    }

    // flight can leave the estimates empty so null must come back as null
    @Test
    void storesAndReadsBackMissingEstimates() {
        FlightInformation flight = flight(KEY, FlightStatus.PLANNED, null, null);

        assertThat(repository.saveIfNewer(flight, CREATED)).isTrue();

        assertThat(repository.findCopy(KEY)).contains(flight);
    }

    @Test
    void unknownFlightHasNoCopy() {
        assertThat(repository.findCopy(KEY)).isEmpty();
    }

    @Test
    void newerChangeReplacesTheCopy() {
        repository.saveIfNewer(flight(KEY, "08:00"), CREATED);
        FlightInformation delayed = flight(KEY, FlightStatus.PLANNED, ETD, ETA);

        assertThat(repository.saveIfNewer(delayed, DELAYED)).isTrue();

        assertThat(repository.findCopy(KEY)).contains(delayed);
    }

    // a retried or late event must not take the copy back in time
    @Test
    void olderChangeIsIgnored() {
        FlightInformation delayed = flight(KEY, FlightStatus.PLANNED, ETD, ETA);
        repository.saveIfNewer(delayed, DELAYED);

        assertThat(repository.saveIfNewer(flight(KEY, "08:00"), CREATED)).isFalse();

        assertThat(repository.findCopy(KEY)).contains(delayed);
    }

    @Test
    void sameChangeTwiceChangesNothingTheSecondTime() {
        repository.saveIfNewer(flight(KEY, "08:00"), CREATED);

        assertThat(repository.saveIfNewer(flight(KEY, "08:00"), CREATED)).isFalse();
    }

    // a status from another event must stay when only the estimates change
    @Test
    void newerEstimatesChangeOnlyTheEstimates() {
        repository.saveIfNewer(flight(KEY, FlightStatus.DEPARTED, null, null), CREATED);

        assertThat(repository.saveEstimatesIfNewer(KEY, ETD, ETA, DELAYED)).isTrue();

        assertThat(repository.findCopy(KEY)).contains(flight(KEY, FlightStatus.DEPARTED, ETD, ETA));
    }

    @Test
    void olderEstimatesAreIgnored() {
        FlightInformation delayed = flight(KEY, FlightStatus.PLANNED, ETD, ETA);
        repository.saveIfNewer(delayed, DELAYED);

        assertThat(
                        repository.saveEstimatesIfNewer(
                                KEY,
                                delayed.scheduledDeparture(),
                                delayed.scheduledArrival(),
                                CREATED))
                .isFalse();

        assertThat(repository.findCopy(KEY)).contains(delayed);
    }

    // an estimate alone is not a whole flight so no row is made
    @Test
    void estimatesForAFlightNotInTheCopyStoreNothing() {
        assertThat(repository.saveEstimatesIfNewer(KEY, ETD, ETA, DELAYED)).isFalse();

        assertThat(repository.findCopy(KEY)).isEmpty();
    }

    // a booking refused after the http lookup rolls back, the fetched flight must stay
    @Test
    void savedFlightStaysWhenTheOuterTransactionRollsBack() {
        FlightInformation flight = flight(KEY, "08:00");
        TransactionTemplate booking = new TransactionTemplate(transactionManager);

        assertThatThrownBy(
                        () ->
                                booking.executeWithoutResult(
                                        status -> {
                                            repository.saveIfNewer(flight, CREATED);
                                            throw new IllegalStateException("booking refused");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.findCopy(KEY)).contains(flight);
    }

    // ----- advanceStatus: the status only moves forward -----

    @Test
    void plannedFlightBecomesDepartedOnce() {
        repository.saveIfNewer(flight(KEY, "08:00"), CREATED);

        assertThat(repository.advanceStatus(KEY, FlightStatus.DEPARTED)).isTrue();
        assertThat(repository.advanceStatus(KEY, FlightStatus.DEPARTED)).isFalse();

        assertThat(repository.findCopy(KEY).orElseThrow().status())
                .isEqualTo(FlightStatus.DEPARTED);
    }

    // landed before departed ends in landed, the late departed changes nothing
    @Test
    void landedBeforeDepartedStaysLanded() {
        repository.saveIfNewer(flight(KEY, "08:00"), CREATED);

        assertThat(repository.advanceStatus(KEY, FlightStatus.LANDED)).isTrue();
        assertThat(repository.advanceStatus(KEY, FlightStatus.DEPARTED)).isFalse();

        assertThat(repository.findCopy(KEY).orElseThrow().status()).isEqualTo(FlightStatus.LANDED);
    }

    @Test
    void unknownFlightCannotAdvance() {
        assertThat(repository.advanceStatus(KEY, FlightStatus.DEPARTED)).isFalse();
    }

    // the change time stays so an older estimate that arrives later is still applied
    @Test
    void advancingTheStatusLeavesTheChangeTimeAlone() {
        repository.saveIfNewer(flight(KEY, "08:00"), CREATED);

        repository.advanceStatus(KEY, FlightStatus.DEPARTED);

        assertThat(repository.saveEstimatesIfNewer(KEY, ETD, ETA, DELAYED)).isTrue();
        assertThat(repository.findCopy(KEY)).contains(flight(KEY, FlightStatus.DEPARTED, ETD, ETA));
    }

    // a late created event or lookup after the departure must not take the status back
    @Test
    void newerWholeRowWriteKeepsTheStatusOfAnExistingRow() {
        repository.saveIfNewer(flight(KEY, "08:00"), CREATED);
        repository.advanceStatus(KEY, FlightStatus.DEPARTED);

        assertThat(repository.saveIfNewer(flight(KEY, FlightStatus.PLANNED, ETD, ETA), DELAYED))
                .isTrue();

        assertThat(repository.findCopy(KEY)).contains(flight(KEY, FlightStatus.DEPARTED, ETD, ETA));
    }

    @Test
    void dayOverviewHasOnlyThatDayOrderedByDeparture() {
        FlightKey late = new FlightKey("HJ300", DAY, BRU);
        FlightKey early = new FlightKey("HJ400", DAY, BRU);
        repository.saveIfNewer(flight(late, "15:00"), CREATED);
        repository.saveIfNewer(flight(early, "06:30"), CREATED);
        repository.saveIfNewer(
                flight(new FlightKey("HJ221", DAY.plusDays(1), BRU), "07:00"), CREATED);

        assertThat(repository.findCopiesOn(DAY))
                .extracting(FlightInformation::flightKey)
                .containsExactly(early, late);
    }

    // departure on the key date at the given utc time and 70 minutes to amsterdam
    private static FlightInformation flight(FlightKey key, String departureTime) {
        Instant departure =
                Instant.parse(key.scheduledDepartureDate() + "T" + departureTime + ":00Z");
        Instant arrival = departure.plusSeconds(70 * 60);
        return new FlightInformation(
                key,
                new AirportCode("AMS"),
                FlightStatus.PLANNED,
                departure,
                arrival,
                departure,
                arrival);
    }

    // the 08:00 flight of the key with the given status and estimates
    private static FlightInformation flight(
            FlightKey key,
            FlightStatus status,
            Instant estimatedDeparture,
            Instant estimatedArrival) {
        FlightInformation planned = flight(key, "08:00");
        return new FlightInformation(
                key,
                planned.arrivalAirportCode(),
                status,
                planned.scheduledDeparture(),
                planned.scheduledArrival(),
                estimatedDeparture,
                estimatedArrival);
    }
}
