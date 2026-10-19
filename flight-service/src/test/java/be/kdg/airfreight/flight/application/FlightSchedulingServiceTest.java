package be.kdg.airfreight.flight.application;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.flight.domain.Flight;
import be.kdg.airfreight.flight.domain.FlightKey;
import be.kdg.airfreight.flight.domain.FlightSchedule;
import be.kdg.airfreight.flight.domain.events.FlightCreated;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FlightSchedulingServiceTest {
    @Test
    void newScheduleCreatesOneEventPerFlight() {
        var repository = new InMemoryFlights();
        var events = new ArrayList<Object>();
        var service = new FlightSchedulingService(repository, events::add);

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
        var service = new FlightSchedulingService(repository, events::add);
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
        var service = new FlightSchedulingService(repository, publishedEvents::add);

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
        var service = new FlightSchedulingService(repository, publishedEvents::add);
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
            implements be.kdg.airfreight.flight.domain.FlightRepository {
        private final Map<FlightKey, Flight> flights = new HashMap<>();

        @Override
        public Optional<Flight> findById(FlightKey key) {
            return Optional.ofNullable(flights.get(key));
        }

        @Override
        public Flight save(Flight flight) {
            flights.put(flight.key(), flight);
            return flight;
        }
    }
}
