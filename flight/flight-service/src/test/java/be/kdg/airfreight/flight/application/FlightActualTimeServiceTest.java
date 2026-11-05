package be.kdg.airfreight.flight.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.application.command.RecordArrivalCommand;
import be.kdg.airfreight.flight.application.command.RecordDepartureCommand;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.events.FlightDeparted;
import be.kdg.airfreight.flight.domain.events.FlightLanded;
import be.kdg.airfreight.flight.domain.flight.ActualTimePolicy;
import be.kdg.airfreight.flight.domain.flight.ActualTimeRefusedException;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlightActualTimeServiceTest {
    private static final FlightKey KEY = new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final Instant STD = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant STA = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-19T10:30:00Z");

    private final InMemoryFlights flights = new InMemoryFlights();
    private final List<Object> events = new ArrayList<>();
    private final FlightActualTimeService service =
            new FlightActualTimeService(
                    flights,
                    events::add,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    new ActualTimePolicy(Duration.ofMinutes(5)));

    @BeforeEach
    void setUp() {
        Flight flight = Flight.create(KEY, "AMS", "SXBAA", STD, STA);
        flight.recordFinalizedLoad();
        flights.save(flight);
        flights.saves = 0;
    }

    @Test
    void departureIsSavedAndPublishedWithTheRegistrationTime() {
        Instant atd = STD.plus(Duration.ofMinutes(3));

        Flight saved = service.recordDeparture(new RecordDepartureCommand(KEY, atd));

        assertThat(saved.status()).isEqualTo(FlightStatus.DEPARTED);
        assertThat(flights.findById(KEY).orElseThrow().actualDeparture()).isEqualTo(atd);
        assertThat(events)
                .singleElement()
                .isEqualTo(new FlightDeparted(KEY, "AMS", "SXBAA", atd, NOW));
    }

    @Test
    void arrivalIsSavedAndPublished() {
        service.recordDeparture(new RecordDepartureCommand(KEY, STD));
        events.clear();
        Instant ata = STA.minus(Duration.ofMinutes(2));

        Flight saved = service.recordArrival(new RecordArrivalCommand(KEY, ata));

        assertThat(saved.status()).isEqualTo(FlightStatus.LANDED);
        assertThat(events)
                .singleElement()
                .isEqualTo(new FlightLanded(KEY, "AMS", "SXBAA", ata, NOW));
    }

    // msg-05: a refused registration sends no ad and changes nothing
    @Test
    void refusedDepartureSavesNothingAndPublishesNothing() {
        assertThatThrownBy(
                        () ->
                                service.recordDeparture(
                                        new RecordDepartureCommand(
                                                KEY, STD.plus(Duration.ofMinutes(6)))))
                .isInstanceOf(ActualTimeRefusedException.class);

        assertThat(flights.findById(KEY).orElseThrow().status()).isEqualTo(FlightStatus.PLANNED);
        assertThat(flights.saves).isZero();
        assertThat(events).isEmpty();
    }

    @Test
    void unknownFlightIsNotFound() {
        FlightKey unknown = new FlightKey("HJ999", LocalDate.of(2026, 10, 19), "BRU");

        assertThatThrownBy(() -> service.recordDeparture(new RecordDepartureCommand(unknown, STD)))
                .isInstanceOf(NotFoundException.class);
        assertThat(events).isEmpty();
    }

    // the lock matters: a second click must wait for the first registration
    @Test
    void flightIsLoadedWithTheRowLock() {
        service.recordDeparture(new RecordDepartureCommand(KEY, STD));

        assertThat(flights.lockedLookups).isEqualTo(1);
    }

    private static final class InMemoryFlights implements FlightRepository {
        private final Map<FlightKey, Flight> flights = new HashMap<>();
        private int lockedLookups;
        private int saves;

        @Override
        public Optional<Flight> findById(FlightKey key) {
            return Optional.ofNullable(flights.get(key));
        }

        @Override
        public Optional<Flight> findByIdForUpdate(FlightKey key) {
            lockedLookups++;
            return findById(key);
        }

        @Override
        public Optional<Flight> findReplacedBy(FlightKey newKey, Instant newScheduledDeparture) {
            return Optional.empty();
        }

        @Override
        public List<Flight> findByScheduledDepartureDate(LocalDate date) {
            return List.of();
        }

        @Override
        public List<Flight> findScheduledBefore(Instant end) {
            return List.of();
        }

        @Override
        public Flight save(Flight flight) {
            saves++;
            flights.put(flight.key(), flight);
            return flight;
        }
    }
}
