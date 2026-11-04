package be.kdg.airfreight.flight.application;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.flight.domain.events.FlightCreated;
import be.kdg.airfreight.flight.domain.events.FlightReplaced;
import be.kdg.airfreight.flight.domain.flight.Delay;
import be.kdg.airfreight.flight.domain.flight.DelayType;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightSchedule;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlightSchedulingServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T14:03:15Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final FlightKey OLD_KEY =
            new FlightKey("HJ500", LocalDate.of(2026, 11, 10), "BRU");
    private static final FlightKey NEW_KEY =
            new FlightKey("HJ500A", LocalDate.of(2026, 11, 11), "BRU");
    private static final Instant NEW_STD = Instant.parse("2026-11-11T05:00:00Z");
    private static final Instant NEW_STA = Instant.parse("2026-11-11T10:20:00Z");

    @Test
    void newScheduleCreatesOneEventPerFlight() {
        var repository = new InMemoryFlights();
        var events = new ArrayList<Object>();
        var service = new FlightSchedulingService(repository, events::add, CLOCK);

        var result =
                service.createFlights(
                        "new", schedule(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14)));

        assertThat(result.createdFlights()).hasSize(2);
        assertThat(result.duplicateFlightKeys()).isEmpty();
        assertThat(events)
                .extracting(event -> ((FlightCreated) event).flightKey())
                .containsExactlyElementsOf(
                        result.createdFlights().stream().map(Flight::key).toList());
    }

    @Test
    void overlappingScheduleOnlyCreatesEventsForNewDates() {
        var repository = new InMemoryFlights();
        var events = new ArrayList<Object>();
        var service = new FlightSchedulingService(repository, events::add, CLOCK);
        service.createFlights(
                "first", schedule(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14)));
        var originalFlights = new HashMap<>(repository.flights);
        events.clear();

        var overlap =
                new FlightSchedule(
                        LocalDate.of(2026, 10, 14),
                        LocalDate.of(2026, 10, 19),
                        EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        LocalTime.of(8, 0),
                        LocalTime.of(11, 0),
                        "HJ221",
                        "BRU",
                        "LHR",
                        "CHANGED");
        var result = service.createFlights("overlap", overlap);

        assertThat(repository.flights).hasSize(3);
        originalFlights.forEach(
                (key, flight) -> assertThat(repository.flights.get(key)).isSameAs(flight));
        assertThat(result.duplicateFlightKeys()).hasSize(1);
        assertThat(result.createdFlights())
                .singleElement()
                .satisfies(
                        flight ->
                                assertThat(flight.key().scheduledDepartureDate())
                                        .isEqualTo(LocalDate.of(2026, 10, 19)));
        assertThat(events)
                .singleElement()
                .isInstanceOfSatisfying(
                        FlightCreated.class,
                        event ->
                                assertThat(event.flightKey())
                                        .isEqualTo(result.createdFlights().getFirst().key()));
    }

    @Test
    void createsMatchingFlightsAndContinuesAfterExistingFlight() {
        var repository = new InMemoryFlights();
        var schedule = schedule(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14));
        repository.save(schedule.expand().getFirst());
        var publishedEvents = new ArrayList<Object>();
        var service = new FlightSchedulingService(repository, publishedEvents::add, CLOCK);

        var result = service.createFlights("message-1", schedule);

        assertThat(result.createdFlights()).hasSize(1);
        assertThat(result.duplicateFlightKeys()).hasSize(1);
        assertThat(repository.flights).hasSize(2);
        var createdFlight = result.createdFlights().getFirst();
        assertThat(publishedEvents)
                .singleElement()
                .isInstanceOfSatisfying(
                        FlightCreated.class,
                        event -> {
                            assertThat(event.flightKey()).isEqualTo(createdFlight.key());
                            assertThat(event.arrivalAirportCode())
                                    .isEqualTo(createdFlight.arrivalAirportCode());
                            assertThat(event.scheduledDeparture())
                                    .isEqualTo(createdFlight.scheduledDeparture());
                            assertThat(event.scheduledArrival())
                                    .isEqualTo(createdFlight.scheduledArrival());
                            assertThat(event.occurredOn()).isNotNull();
                        });
    }

    @Test
    void redeliveredMessageHasNoSecondEffect() {
        var repository = new InMemoryFlights();
        var publishedEvents = new ArrayList<Object>();
        var service = new FlightSchedulingService(repository, publishedEvents::add, CLOCK);
        var schedule = schedule(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));

        var first = service.createFlights("message-1", schedule);
        assertThat(publishedEvents).hasSize(1);
        var originalEvent = publishedEvents.getFirst();

        var second = service.createFlights("message-1", schedule);

        assertThat(first.createdFlights()).hasSize(1);
        assertThat(second.createdFlights()).isEmpty();
        assertThat(second.duplicateFlightKeys())
                .containsExactly(first.createdFlights().getFirst().key().toString());
        assertThat(repository.flights).hasSize(1);
        assertThat(repository.flights.get(first.createdFlights().getFirst().key()))
                .isSameAs(first.createdFlights().getFirst());
        assertThat(publishedEvents).containsExactly(originalEvent);
    }

    // the ssm the simulator sends for a replanned flight links old and new flight both ways
    @Test
    void ssmForTheExpectedKeyLinksTheReplannedFlightAndPublishesReplacedOnce() {
        var repository = new InMemoryFlights();
        Flight replanned = replannedFlight();
        repository.save(replanned);
        var events = new ArrayList<Object>();
        var service = new FlightSchedulingService(repository, events::add, CLOCK);

        var result = service.createFlights("replan-1", replacementSchedule(NEW_STD, NEW_STA));

        Flight replacement = result.createdFlights().getFirst();
        assertThat(replacement.replaces()).contains(OLD_KEY);
        assertThat(repository.flights.get(NEW_KEY).replaces()).contains(OLD_KEY);
        assertThat(repository.flights.get(OLD_KEY).replacementCreated()).isTrue();
        assertThat(events)
                .containsExactly(
                        new FlightCreated(NEW_KEY, "AMS", NEW_STD, NEW_STA, NOW),
                        new FlightReplaced(OLD_KEY, NEW_KEY, NEW_STD, NEW_STA, NOW));

        // the same ssm again creates and links nothing
        events.clear();
        service.createFlights("replan-1", replacementSchedule(NEW_STD, NEW_STA));

        assertThat(events).isEmpty();
        assertThat(repository.flights).hasSize(2);
    }

    // another flight with the right key but another std is a normal flight, not the replacement
    @Test
    void ssmWithTheExpectedKeyButAnotherStdIsNotLinked() {
        var repository = new InMemoryFlights();
        repository.save(replannedFlight());
        var events = new ArrayList<Object>();
        var service = new FlightSchedulingService(repository, events::add, CLOCK);

        var result =
                service.createFlights(
                        "other", replacementSchedule(NEW_STD.plusSeconds(3600), NEW_STA));

        assertThat(result.createdFlights().getFirst().replaces()).isEmpty();
        assertThat(repository.flights.get(OLD_KEY).replacementCreated()).isFalse();
        assertThat(events).singleElement().isInstanceOf(FlightCreated.class);
    }

    private static Flight replannedFlight() {
        Flight flight =
                Flight.restore(
                        OLD_KEY,
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-11-10T20:00:00Z"),
                        Instant.parse("2026-11-10T22:00:00Z"),
                        FlightStatus.PLANNED,
                        List.of(Delay.restore(UUID.randomUUID(), 400, "71", "Fog")),
                        List.of(Delay.restore(UUID.randomUUID(), 200, "71", "Holding")));
        flight.replan(DelayType.DEPARTURE, null, 140, "93", "Leak", "HJ500A", NOW);
        return flight;
    }

    // one day, the utc times of the new schedule, as the simulator maps them
    private static FlightSchedule replacementSchedule(Instant std, Instant sta) {
        return new FlightSchedule(
                LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 11),
                EnumSet.of(DayOfWeek.WEDNESDAY),
                LocalTime.ofInstant(std, ZoneOffset.UTC),
                LocalTime.ofInstant(sta, ZoneOffset.UTC),
                "HJ500A",
                "BRU",
                "AMS",
                "SXBAA");
    }

    private static FlightSchedule schedule(LocalDate start, LocalDate end) {
        return new FlightSchedule(
                start,
                end,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                "HJ221",
                "BRU",
                "AMS",
                "SXBAA");
    }

    private static final class InMemoryFlights
            implements be.kdg.airfreight.flight.domain.flight.FlightRepository {
        private final Map<FlightKey, Flight> flights = new HashMap<>();

        @Override
        public Optional<Flight> findById(FlightKey key) {
            return Optional.ofNullable(flights.get(key));
        }

        @Override
        public Optional<Flight> findByIdForUpdate(FlightKey key) {
            return findById(key);
        }

        @Override
        public List<Flight> findByScheduledDepartureDate(LocalDate date) {
            return flights.values().stream()
                    .filter(flight -> flight.key().scheduledDepartureDate().equals(date))
                    .toList();
        }

        @Override
        public Flight save(Flight flight) {
            flights.put(flight.key(), flight);
            return flight;
        }

        // the same rule as the database query
        @Override
        public Optional<Flight> findReplacedBy(FlightKey newKey, Instant newScheduledDeparture) {
            return flights.values().stream()
                    .filter(flight -> flight.status() == FlightStatus.REPLANNED)
                    .filter(flight -> !flight.replacementCreated())
                    .filter(
                            flight ->
                                    flight.replanning()
                                            .orElseThrow()
                                            .newKey(flight.key().departureAirportCode())
                                            .equals(newKey))
                    .filter(
                            flight ->
                                    flight.replanning()
                                            .orElseThrow()
                                            .newScheduledDeparture()
                                            .equals(newScheduledDeparture))
                    .findFirst();
        }
    }
}
